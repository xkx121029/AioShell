package com.aioshell.app.feature.config

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aioshell.app.core.data.model.ChatConfig
import com.aioshell.app.core.data.repository.ConfigRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ConfigListUi(
    val configs: List<ChatConfig> = emptyList(),
    val activeId: String? = null,
    val isLoading: Boolean = true,
)

@HiltViewModel
class ConfigListViewModel @Inject constructor(
    private val repo: ConfigRepository,
) : ViewModel() {

    val uiState: StateFlow<ConfigListUi> = combine(repo.profiles, repo.activeConfig) { list, active ->
        ConfigListUi(list, active?.id, isLoading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ConfigListUi(isLoading = true))

    fun setActive(id: String) = viewModelScope.launch { repo.setActive(id) }

    fun delete(id: String) = viewModelScope.launch { repo.delete(id) }
}