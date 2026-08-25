package com.aioshell.app.feature.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aioshell.app.core.data.repository.ConfigRepository
import com.aioshell.app.core.data.repository.SessionRepository
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
)

data class SessionUiState(
    val sessions: List<SessionUiItem> = emptyList(),
    val hasConfig: Boolean = false,
    val activeConfigName: String? = null,
    val activeConfigId: String? = null,
    val loading: Boolean = true,
)

@HiltViewModel
class SessionViewModel @Inject constructor(
    private val sessionRepo: SessionRepository,
    private val configRepo: ConfigRepository,
) : ViewModel() {

    val uiState: StateFlow<SessionUiState> = combine(
        sessionRepo.sessions,
        configRepo.activeConfig,
    ) { sessions, active ->
        SessionUiState(
            sessions = sessions.map { SessionUiItem(it.id, it.title, it.updatedAt) },
            hasConfig = active != null,
            activeConfigName = active?.name,
            activeConfigId = active?.id,
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
}