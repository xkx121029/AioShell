package com.aioshell.app.core.data.store

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aioshell.app.core.data.model.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "aioshell_settings")

/** 应用偏好存储（主题模式 + 杂项AI档案）。杂项AI 的 base64 密文由仓库层负责加解密。 */
@Singleton
class SettingsStore @Inject constructor(@ApplicationContext private val context: Context) {

    private companion object {
        val KEY_THEME = stringPreferencesKey("theme_mode")
        val KEY_MISC_AI = stringPreferencesKey("misc_ai")
    }

    val themeMode: Flow<ThemeMode> = context.settingsDataStore.data.map { prefs ->
        runCatching { ThemeMode.valueOf(prefs[KEY_THEME] ?: ThemeMode.SYSTEM.name) }
            .getOrDefault(ThemeMode.SYSTEM)
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { it[KEY_THEME] = mode.name }
    }

    /** 观察杂项 AI 档案（序列化后的明文 JSON）。 */
    val miscAiConfigurable: Flow<String?> = context.settingsDataStore.data.map { it[KEY_MISC_AI] }

    suspend fun saveMiscAi(json: String?) {
        context.settingsDataStore.edit {
            if (json == null) it.remove(KEY_MISC_AI) else it[KEY_MISC_AI] = json
        }
    }
}