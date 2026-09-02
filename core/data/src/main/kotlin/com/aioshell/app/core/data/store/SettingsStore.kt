package com.aioshell.app.core.data.store

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
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
        val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val KEY_MISC_AI = stringPreferencesKey("misc_ai")
        val KEY_AMOLED = booleanPreferencesKey("amoled_pure_black")
        val KEY_ACCENT = stringPreferencesKey("accent_color")
        val KEY_CHAT_FONT = floatPreferencesKey("chat_font_size")
        val KEY_CHAT_LINE = floatPreferencesKey("chat_line_spacing")
        val KEY_CONTEXT_COUNT = intPreferencesKey("context_message_count")
        val KEY_AUTO_TTS = booleanPreferencesKey("auto_tts")
        val KEY_IN_PRICE = floatPreferencesKey("token_input_price")
        val KEY_OUT_PRICE = floatPreferencesKey("token_output_price")
        val KEY_LOCK_ENABLED = booleanPreferencesKey("lock_enabled")
        val KEY_LOCK_PIN = stringPreferencesKey("lock_pin_hash")
        val KEY_REMINDERS = stringPreferencesKey("reminders")
        val KEY_PERSONAS = stringPreferencesKey("personas")
        val KEY_CURRENT_PERSONA = stringPreferencesKey("current_persona_id")
        val KEY_WEB_SEARCH = booleanPreferencesKey("web_search_enabled")
        val KEY_KNOWLEDGE = booleanPreferencesKey("knowledge_enabled")
        val KEY_HANDS_FREE = booleanPreferencesKey("hands_free_voice")
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

    /** 动态取色（Material You）：开启后跟随系统壁纸生成主色。 */
    val dynamicColorEnabled: Flow<Boolean> =
        context.settingsDataStore.data.map { it[KEY_DYNAMIC_COLOR] ?: false }

    suspend fun setDynamicColorEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[KEY_DYNAMIC_COLOR] = enabled }
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

    /** 上下文长度：最多携带的历史消息条数（用户+AI 合计），0 表示不限制。 */
    val contextMessageCount: Flow<Int> = context.settingsDataStore.data.map { it[KEY_CONTEXT_COUNT] ?: 60 }

    suspend fun setContextMessageCount(count: Int) {
        context.settingsDataStore.edit { it[KEY_CONTEXT_COUNT] = count.coerceIn(0, 200) }
    }

    /** AI 回复完成后是否自动朗读。 */
    val autoTts: Flow<Boolean> = context.settingsDataStore.data.map { it[KEY_AUTO_TTS] ?: false }

    suspend fun setAutoTts(enabled: Boolean) {
        context.settingsDataStore.edit { it[KEY_AUTO_TTS] = enabled }
    }

    /** Token 单价（美元 / 每百万 token），默认 0，用于费用估算。 */
    val tokenInputPrice: Flow<Float> = context.settingsDataStore.data.map { it[KEY_IN_PRICE] ?: 0f }

    suspend fun setTokenInputPrice(price: Float) {
        context.settingsDataStore.edit { it[KEY_IN_PRICE] = price.coerceAtLeast(0f) }
    }

    val tokenOutputPrice: Flow<Float> = context.settingsDataStore.data.map { it[KEY_OUT_PRICE] ?: 0f }

    suspend fun setTokenOutputPrice(price: Float) {
        context.settingsDataStore.edit { it[KEY_OUT_PRICE] = price.coerceAtLeast(0f) }
    }

    /** 应用锁：是否开启（PIN）。 */
    val lockEnabled: Flow<Boolean> = context.settingsDataStore.data.map { it[KEY_LOCK_ENABLED] ?: false }

    suspend fun setLockEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[KEY_LOCK_ENABLED] = enabled }
    }

    /** PIN 的 SHA-256 十六进制哈希；null 表示未设置。 */
    val lockPinHash: Flow<String?> = context.settingsDataStore.data.map { it[KEY_LOCK_PIN] }

    suspend fun setLockPinHash(hash: String?) {
        context.settingsDataStore.edit {
            if (hash == null) it.remove(KEY_LOCK_PIN) else it[KEY_LOCK_PIN] = hash
        }
    }

    /** 定时提醒列表（序列化 JSON，由 ReminderManager 维护）。 */
    val remindersRaw: Flow<String?> = context.settingsDataStore.data.map { it[KEY_REMINDERS] }

    suspend fun saveReminders(json: String?) {
        context.settingsDataStore.edit {
            if (json == null) it.remove(KEY_REMINDERS) else it[KEY_REMINDERS] = json
        }
    }

    /** 人格预设列表（序列化 JSON）。空表示尚未保存（回退内置预设）。 */
    val personasRaw: Flow<String?> = context.settingsDataStore.data.map { it[KEY_PERSONAS] }

    suspend fun savePersonas(json: String?) {
        context.settingsDataStore.edit {
            if (json == null) it.remove(KEY_PERSONAS) else it[KEY_PERSONAS] = json
        }
    }

    /** 当前选中的人格预设 id；空表示"默认（不注入人格）"。 */
    val currentPersonaId: Flow<String> =
        context.settingsDataStore.data.map { it[KEY_CURRENT_PERSONA] ?: "" }

    suspend fun setCurrentPersona(id: String) {
        context.settingsDataStore.edit {
            if (id.isBlank()) it.remove(KEY_CURRENT_PERSONA) else it[KEY_CURRENT_PERSONA] = id
        }
    }

    /** 联网搜索开关：开启后发送消息时先检索真实内容再交给模型。 */
    val webSearchEnabled: Flow<Boolean> =
        context.settingsDataStore.data.map { it[KEY_WEB_SEARCH] ?: false }

    suspend fun setWebSearchEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[KEY_WEB_SEARCH] = enabled }
    }

    /** 本地知识库（RAG）开关：开启后发送消息前先检索本地资料再交给模型。 */
    val knowledgeEnabled: Flow<Boolean> =
        context.settingsDataStore.data.map { it[KEY_KNOWLEDGE] ?: false }

    suspend fun setKnowledgeEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[KEY_KNOWLEDGE] = enabled }
    }

    /** 免提语音模式开关：持续监听，识别完成后自动发送，AI 回复自动朗读。 */
    val handsFreeVoice: Flow<Boolean> =
        context.settingsDataStore.data.map { it[KEY_HANDS_FREE] ?: false }

    suspend fun setHandsFreeVoice(enabled: Boolean) {
        context.settingsDataStore.edit { it[KEY_HANDS_FREE] = enabled }
    }
}