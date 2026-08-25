package com.aioshell.app.feature.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aioshell.app.core.data.model.ThemeMode
import com.aioshell.app.core.data.repository.ConfigRepository
import com.aioshell.app.core.data.repository.SessionRepository
import com.aioshell.app.core.data.store.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
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
)

data class SessionUiState(
    val sessions: List<SessionUiItem> = emptyList(),
    val hasConfig: Boolean = false,
    val activeConfigName: String? = null,
    val activeConfigId: String? = null,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val loading: Boolean = true,
)

@HiltViewModel
class SessionViewModel @Inject constructor(
    private val sessionRepo: SessionRepository,
    private val configRepo: ConfigRepository,
    private val settingsStore: SettingsStore,
) : ViewModel() {

    val uiState: StateFlow<SessionUiState> = combine(
        sessionRepo.sessions,
        configRepo.activeConfig,
        settingsStore.themeMode,
    ) { sessions, active, themeMode ->
        SessionUiState(
            sessions = sessions.map { SessionUiItem(it.id, it.title, it.updatedAt, it.lastMessagePreview) },
            hasConfig = active != null,
            activeConfigName = active?.name,
            activeConfigId = active?.id,
            themeMode = themeMode,
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
}