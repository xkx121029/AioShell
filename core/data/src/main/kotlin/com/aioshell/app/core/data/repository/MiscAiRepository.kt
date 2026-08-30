package com.aioshell.app.core.data.repository

import android.util.Log
import com.aioshell.app.core.data.model.MiscAiConfig
import com.aioshell.app.core.data.model.PersistedMiscAi
import com.aioshell.app.core.data.security.SecurityCrypto
import com.aioshell.app.core.data.store.SettingsStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 杂项 AI 档案仓库：读写独立于聊天模型的"杂项 AI"配置（命名/意图分析）。
 * API Key 以密文落盘，内存中使用解密值。
 */
@Singleton
class MiscAiRepository @Inject constructor(
    private val store: SettingsStore,
    private val crypto: SecurityCrypto,
) {

    private companion object {
        const val TAG = "MiscAiRepository"
        private val json = Json { ignoreUnknownKeys = true }
    }

    private fun PersistedMiscAi.toConfig(): MiscAiConfig {
        val key = runCatching { crypto.decrypt(apiKeyCipher) }
            .onFailure { Log.w(TAG, "杂项 AI API Key 解密失败: ${it.message}") }
            .getOrDefault("")
        return MiscAiConfig(baseUrl, key, model, prompt, enabled)
    }

    private fun MiscAiConfig.toPersisted(): PersistedMiscAi = PersistedMiscAi(
        baseUrl = baseUrl,
        apiKeyCipher = apiKey.ifBlank { "" }.let { crypto.encrypt(it) },
        model = model,
        prompt = prompt,
        enabled = enabled,
    )

    private fun decode(raw: String): PersistedMiscAi? =
        runCatching { json.decodeFromString<PersistedMiscAi>(raw) }.getOrNull()

    /** 观察杂项 AI 档案（含解密后的 API Key）。 */
    val config: Flow<MiscAiConfig> =
        store.miscAiConfigurable.map { raw -> raw?.let { decode(it)?.toConfig() } ?: MiscAiConfig("", "", "", "", false) }

    suspend fun get(): MiscAiConfig = config.first()

    suspend fun save(cfg: MiscAiConfig) {
        val jsonStr = json.encodeToString(PersistedMiscAi.serializer(), cfg.toPersisted())
        store.saveMiscAi(jsonStr)
    }
}