package com.aioshell.app.core.data.store

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
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
        val KEY_AMOLED = booleanPreferencesKey("amoled_pure_black")
        val KEY_ACCENT = stringPreferencesKey("accent_color")
        val KEY_CHAT_FONT = floatPreferencesKey("chat_font_size")
        val KEY_CHAT_LINE = floatPreferencesKey("chat_line_spacing")
    }

    /** 自定义强调色（十六进制，如 "#0B6B60"）；null 表示跟随默认主题色。 */
    val accentColor: Flow<String?> = context.settingsDataStore.data.map { it[KEY_ACCENT] }

    suspend fun setAccentColor(hex: String?) {
        context.settingsDataStore.edit {
            if (hex == null) it.remove(KEY_ACCENT) else it[KEY_ACCENT] = hex
        }
    }

    /** 聊天正文字号（sp），默认 16。 */
    val chatFontSize: Flow<Float> = context.settingsDataStore.data.map { it[KEY_CHAT_FONT] ?: 16f }

    suspend fun setChatFontSize(sp: Float) {
        context.settingsDataStore.edit { it[KEY_CHAT_FONT] = sp }
    }

    /** 聊天正文行距（sp），默认 0（跟随默认行高）。 */
    val chatLineSpacing: Flow<Float> = context.settingsDataStore.data.map { it[KEY_CHAT_LINE] ?: 0f }

    suspend fun setChatLineSpacing(sp: Float) {
        context.settingsDataStore.edit { it[KEY_CHAT_LINE] = sp }
    }

    val themeMode: Flow<ThemeMode> = context.settingsDataStore.data.map { prefs ->
        runCatching { ThemeMode.valueOf(prefs[KEY_THEME] ?: ThemeMode.SYSTEM.name) }
            .getOrDefault(ThemeMode.SYSTEM)
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { it[KEY_THEME] = mode.name }
    }

    /** AMOLED 纯黑模式：深色背景下背景/表面切换为纯黑，适配 OLED 屏。 */
    val amoledPureBlack: Flow<Boolean> = context.settingsDataStore.data.map { it[KEY_AMOLED] ?: false }

    suspend fun setAmoledPureBlack(enabled: Boolean) {
        context.settingsDataStore.edit { it[KEY_AMOLED] = enabled }
    }

    /** 观察杂项 AI 档案（序列化后的明文 JSON）。 */
    val miscAiConfigurable: Flow<String?> = context.settingsDataStore.data.map { it[KEY_MISC_AI] }

    suspend fun saveMiscAi(json: String?) {
        context.settingsDataStore.edit {
            if (json == null) it.remove(KEY_MISC_AI) else it[KEY_MISC_AI] = json
        }
    }
}