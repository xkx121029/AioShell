package com.aioshell.app.feature.session

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aioshell.app.core.data.export.BackupManager
import com.aioshell.app.core.data.export.ConversationExporter
import com.aioshell.app.core.data.export.ExportFormat
import com.aioshell.app.core.data.model.ChatConfig
import com.aioshell.app.core.data.model.ThemeMode
import com.aioshell.app.core.data.repository.ConfigRepository
import com.aioshell.app.core.data.repository.SessionRepository
import com.aioshell.app.core.data.store.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SessionUiItem(
    val id: String,
    val title: String,
    val updatedAt: Long,
    val preview: String,
    val pinned: Boolean = false,
    val tags: List<String> = emptyList(),
)

data class SessionUiState(
    val sessions: List<SessionUiItem> = emptyList(),
    val archived: List<SessionUiItem> = emptyList(),
    val hasConfig: Boolean = false,
    val activeConfigName: String? = null,
    val activeConfigId: String? = null,
    val configs: List<ChatConfig> = emptyList(),
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val amoledPureBlack: Boolean = false,
    val loading: Boolean = true,
)

/** 备份 / 恢复操作的反馈消息。 */
data class BackupNotice(val ok: Boolean, val message: String)

/** Token 单价（美元 / 每百万 token），用于费用估算展示。 */
data class CostPrices(val input: Float = 0f, val output: Float = 0f)

@HiltViewModel
class SessionViewModel @Inject constructor(
    private val sessionRepo: SessionRepository,
    private val configRepo: ConfigRepository,
    private val settingsStore: SettingsStore,
    private val exporter: ConversationExporter,
    private val backupManager: BackupManager,
) : ViewModel() {

    /** 备份 / 恢复操作的一次性反馈（UI 收集后置空）。 */
    val backupNotices = MutableSharedFlow<BackupNotice>(extraBufferCapacity = 1)

    /** 会话统计（仅在统计页加载）。 */
    val stats = MutableStateFlow<SessionRepository.SessionStats?>(null)

    /** Token 单价（统计页费用估算用）。 */
    val costPrices: StateFlow<CostPrices> = combine(
        settingsStore.tokenInputPrice,
        settingsStore.tokenOutputPrice,
    ) { input, output -> CostPrices(input, output) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CostPrices())

    fun loadStats() = viewModelScope.launch(Dispatchers.IO) {
        stats.value = sessionRepo.getStats()
    }

    val uiState: StateFlow<SessionUiState> = combine(
        combine(
            sessionRepo.sessions,
            sessionRepo.archivedSessions,
            settingsStore.themeMode,
            settingsStore.amoledPureBlack,
        ) { sessions, archived, themeMode, amoled ->
            Triple(sessions, archived, Pair(themeMode, amoled))
        },
        configRepo.activeConfig,
        configRepo.profiles,
    ) { (sessions, archived, ta), active, configs ->
        SessionUiState(
            sessions = sessions.map { it.toUiItem() },
            archived = archived.map { it.toUiItem() },
            hasConfig = active != null,
            activeConfigName = active?.name,
            activeConfigId = active?.id,
            configs = configs,
            themeMode = ta.first,
            amoledPureBlack = ta.second,
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SessionUiState())

    fun createAndGetId(configId: String, onResult: (String) -> Unit) =
        viewModelScope.launch {
            val id = sessionRepo.create(configId = configId)
            onResult(id)
        }

    fun rename(id: String, title: String) = viewModelScope.launch {
        sessionRepo.rename(id, title)
    }

    fun delete(id: String) = viewModelScope.launch {
        sessionRepo.delete(id)
    }

    /** 批量删除多个会话。 */
    fun deleteMany(ids: List<String>) = viewModelScope.launch {
        sessionRepo.deleteAll(ids)
    }

    /** 切换会话置顶状态。 */
    fun togglePinned(id: String) = viewModelScope.launch {
        val target = uiState.value.sessions.firstOrNull { it.id == id } ?: return@launch
        sessionRepo.setPinned(id, !target.pinned)
    }

    /** 归档会话（从主列表隐藏，可在归档页恢复）。 */
    fun archive(id: String) = viewModelScope.launch { sessionRepo.setArchived(id, true) }

    /** 恢复归档会话回主列表。 */
    fun restore(id: String) = viewModelScope.launch { sessionRepo.setArchived(id, false) }

    /** 批量归档多个会话。 */
    fun archiveMany(ids: List<String>) = viewModelScope.launch {
        ids.forEach { sessionRepo.setArchived(it, true) }
    }

    /** 快速切换当前接口配置（不进设置页）。 */
    fun setActiveConfig(id: String) = viewModelScope.launch { configRepo.setActive(id) }

    /** 设置 / 清空会话标签。 */
    fun setTags(id: String, tags: List<String>) = viewModelScope.launch { sessionRepo.setTags(id, tags) }

    /** 设置 / 清空会话级模型覆盖。 */
    fun setModelOverride(id: String, model: String?) = viewModelScope.launch { sessionRepo.setModelOverride(id, model) }

    /** 复制会话：返回新会话 id（用于导航打开）。 */
    fun duplicate(id: String, onResult: (String) -> Unit) = viewModelScope.launch {
        val cfgId = uiState.value.activeConfigId
        val newId = (if (cfgId != null) sessionRepo.duplicate(id, cfgId) else null) ?: return@launch
        onResult(newId)
    }

    /** 合并会话：把 [sourceId] 并入 [targetId]，删除源会话。 */
    fun merge(targetId: String, sourceId: String) = viewModelScope.launch {
        runCatching { sessionRepo.merge(targetId, sourceId) }
    }

    /** 循环切换主题模式：浅色 → 深色 → 跟随系统。 */
    fun cycleTheme() = viewModelScope.launch {
        val next = when (uiState.value.themeMode) {
            ThemeMode.LIGHT -> ThemeMode.DARK
            ThemeMode.DARK -> ThemeMode.SYSTEM
            ThemeMode.SYSTEM -> ThemeMode.LIGHT
        }
        settingsStore.setThemeMode(next)
    }

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { settingsStore.setThemeMode(mode) }

    /** 切换 AMOLED 纯黑模式（仅深色下生效）。 */
    fun setAmoled(enabled: Boolean) = viewModelScope.launch { settingsStore.setAmoledPureBlack(enabled) }

    /** 设置自定义强调色（hex，null 恢复默认）。 */
    fun setAccent(hex: String?) = viewModelScope.launch { settingsStore.setAccentColor(hex) }

    /** 设置聊天正文字号（sp）。 */
    fun setChatFontSize(sp: Float) = viewModelScope.launch { settingsStore.setChatFontSize(sp) }

    /** 设置聊天正文行距（sp）。 */
    fun setChatLineSpacing(sp: Float) = viewModelScope.launch { settingsStore.setChatLineSpacing(sp) }

    /** 批量导出多个会话为指定格式并调起系统分享。 */
    fun exportMany(ids: List<String>, format: ExportFormat) = viewModelScope.launch {
        exporter.exportAndShareMultiple(ids, format)
    }

    /** 导出全量数据备份并调起分享面板。 */
    fun exportBackup() = viewModelScope.launch(Dispatchers.IO) {
        val ok = backupManager.exportBackupAndShare()
        backupNotices.emit(
            BackupNotice(ok, if (ok) "备份文件已生成，选择目标位置保存" else "备份失败，请重试"),
        )
    }

    /** 从备份文件恢复数据。 */
    fun importBackup(uri: Uri) = viewModelScope.launch {
        runCatching { backupManager.importBackup(uri) }
            .onSuccess { r ->
                backupNotices.emit(
                    BackupNotice(
                        true,
                        "恢复成功：${r.configs} 个接口 · ${r.sessions} 个会话 · ${r.messages} 条消息",
                    ),
                )
            }
            .onFailure { e ->
                backupNotices.emit(BackupNotice(false, "恢复失败：${e.message}"))
            }
    }
}

/** 会话摘要 → 列表展示项。 */
private fun com.aioshell.app.core.data.repository.SessionRepository.SessionSummary.toUiItem(): SessionUiItem =
    SessionUiItem(id, title, updatedAt, lastMessagePreview, pinned, tags)