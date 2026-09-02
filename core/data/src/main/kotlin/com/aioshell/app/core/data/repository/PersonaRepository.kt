package com.aioshell.app.core.data.repository

import com.aioshell.app.core.data.model.BuiltinPersonas
import com.aioshell.app.core.data.model.Persona
import com.aioshell.app.core.data.store.SettingsStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 人格预设仓库：加载 / 保存 / 切换人格。
 * 内置预设始终存在；用户自定义（non builtin）持久化于 DataStore（JSON）。
 */
@Singleton
class PersonaRepository @Inject constructor(private val settingsStore: SettingsStore) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** 完整人格列表 = 内置预设 + 用户自定义（自定义可覆盖同名内置，内置总是排前）。 */
    val personas: Flow<List<Persona>> = settingsStore.personasRaw.map { raw ->
        val custom = runCatching {
            raw?.let { json.decodeFromString<List<Persona>>(it) }.orEmpty()
        }.getOrDefault(emptyList())
        val customMap = custom.associateBy { it.id }
        BuiltinPersonas.list.map { p -> customMap[p.id] ?: p } + custom.filter { !it.builtin }
    }

    /** 当前选中的人格（blank → null，表示不注入人格）。 */
    val currentPersona: Flow<Persona?> = combine(settingsStore.currentPersonaId, personas) { id, list ->
        if (id.isBlank()) null else list.firstOrNull { it.id == id }
    }

    suspend fun setCurrent(id: String) = settingsStore.setCurrentPersona(id)

    /** 保存自定义人格列表（过滤内置项，仅保留用户新增/修改项）。 */
    suspend fun save(personas: List<Persona>) {
        val custom = personas.filterNot { it.builtin }
        settingsStore.savePersonas(json.encodeToString(custom))
    }

    /** 新增或更新一条人格（先合并再整体保存）。 */
    suspend fun upsert(persona: Persona) {
        val list = personas.first().toMutableList()
        val idx = list.indexOfFirst { it.id == persona.id }
        if (idx >= 0) list[idx] = persona else list += persona
        save(list)
    }

    suspend fun delete(id: String) {
        save(personas.first().filterNot { it.id == id })
        // 若删除的是当前选中项，清除选中
        if (settingsStore.currentPersonaId.first() == id) setCurrent("")
    }
}