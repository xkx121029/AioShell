package com.aioshell.app.core.data.store

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aioshell.app.core.data.model.PersistedConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.aioshellDataStore by preferencesDataStore(name = "aioshell_config")

/** DataStore 配置存储：接口档案(json 序列化) 与 当前活动档案 id。 */
@Singleton
class ConfigStore @Inject constructor(@ApplicationContext private val context: Context) {

    private companion object {
        val KEY_PROFILES = stringPreferencesKey("config_profiles")
        val KEY_ACTIVE_ID = stringPreferencesKey("active_config_id")
        private val json = Json { ignoreUnknownKeys = true }
        private val serializer = ListSerializer(PersistedConfig.serializer())
    }

    val profilesFlow: Flow<List<PersistedConfig>> =
        context.aioshellDataStore.data.map { prefs ->
            val raw = prefs[KEY_PROFILES] ?: "[]"
            runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(emptyList())
        }

    val activeIdFlow: Flow<String?> =
        context.aioshellDataStore.data.map { it[KEY_ACTIVE_ID] }

    suspend fun saveProfiles(list: List<PersistedConfig>) {
        context.aioshellDataStore.edit { it[KEY_PROFILES] = json.encodeToString(serializer, list) }
    }

    suspend fun setActiveId(id: String?) {
        context.aioshellDataStore.edit {
            if (id == null) it.remove(KEY_ACTIVE_ID) else it[KEY_ACTIVE_ID] = id
        }
    }
}