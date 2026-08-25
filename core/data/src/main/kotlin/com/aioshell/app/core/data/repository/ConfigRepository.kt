package com.aioshell.app.core.data.repository

import android.util.Log
import com.aioshell.app.core.data.model.ChatConfig
import com.aioshell.app.core.data.model.PersistedConfig
import com.aioshell.app.core.data.security.SecurityCrypto
import com.aioshell.app.core.data.store.ConfigStore
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * 接口配置档案仓库：负责 档案CRUD + 当前档案 + API Key 加解密。
 * API Key 仅以密文落盘，内存中使用解密值，绝不写日志。
 */
@Singleton
class ConfigRepository @Inject constructor(
    private val store: ConfigStore,
    private val crypto: SecurityCrypto,
) {

    private companion object { const val TAG = "ConfigRepository" }

    private fun PersistedConfig.toConfig(): ChatConfig {
        val key = runCatching { crypto.decrypt(apiKeyCipher) }
            .onFailure { Log.w(TAG, "API Key 解密失败: ${it.message}") }
            .getOrDefault("")
        return ChatConfig(
            id = id, name = name, baseUrl = baseUrl, apiKey = key, model = model,
            temperature = temperature, maxTokens = maxTokens, topP = topP, isDefault = isDefault,
        )
    }

    private fun ChatConfig.toPersisted(): PersistedConfig = PersistedConfig(
        id = id, name = name, baseUrl = baseUrl, apiKeyCipher = crypto.encrypt(apiKey),
        model = model, temperature = temperature, maxTokens = maxTokens, topP = topP, isDefault = isDefault,
    )

    /** 观察全部档案（含解密后的 API Key）。 */
    val profiles: Flow<List<ChatConfig>> = store.profilesFlow.map { list -> list.map { it.toConfig() } }

    /** 观察当前活动档案。 */
    val activeConfig: Flow<ChatConfig?> = combine(store.profilesFlow, store.activeIdFlow) { list, activeId ->
        val active = list.firstOrNull { it.id == activeId } ?: list.firstOrNull { it.isDefault }
        active?.toConfig()
    }

    suspend fun getAll(): List<ChatConfig> = store.profilesFlow.first().map { it.toConfig() }

    suspend fun getById(id: String): ChatConfig? =
        store.profilesFlow.first().firstOrNull { it.id == id }?.toConfig()

    /** 新增或更新档案；新增时生成 id，首个档案设为默认。 */
    suspend fun save(config: ChatConfig): ChatConfig {
        val list = store.profilesFlow.first().toMutableList()
        val target = if (config.id.isBlank()) config.copy(id = UUID.randomUUID().toString()) else config
        val idx = list.indexOfFirst { it.id == target.id }
        if (idx >= 0) {
            list[idx] = target.toPersisted()
        } else {
            val persisted = if (list.isEmpty()) target.copy(isDefault = true).toPersisted() else target.toPersisted()
            list += persisted
        }
        store.saveProfiles(list)
        if (list.size == 1) store.setActiveId(list.first().id)
        return target
    }

    suspend fun delete(id: String) {
        val list = store.profilesFlow.first().filterNot { it.id == id }
        store.saveProfiles(list)
        val activeId = store.activeIdFlow.first()
        if (activeId == id) {
            store.setActiveId(list.firstOrNull()?.id)
        }
    }

    suspend fun setActive(id: String) = store.setActiveId(id)
}