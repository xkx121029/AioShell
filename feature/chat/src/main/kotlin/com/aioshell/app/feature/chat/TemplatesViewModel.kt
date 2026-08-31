package com.aioshell.app.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aioshell.app.core.data.repository.PromptTemplate
import com.aioshell.app.core.data.repository.PromptTemplateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TemplatesUiState(
    val templates: List<PromptTemplate> = emptyList(),
    val loading: Boolean = true,
)

@HiltViewModel
class TemplatesViewModel @Inject constructor(
    private val repo: PromptTemplateRepository,
) : ViewModel() {

    val uiState: StateFlow<TemplatesUiState> =
        repo.templates
            .map { TemplatesUiState(it, loading = false) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TemplatesUiState())

    init {
        // 首次进入时确保内置模板已写入数据库
        viewModelScope.launch { repo.ensureBuiltIns() }
    }

    fun add(title: String, content: String, category: String) = viewModelScope.launch {
        repo.add(title, content, category)
    }

    fun update(id: String, title: String, content: String, category: String) = viewModelScope.launch {
        repo.update(id, title, content, category)
    }

    fun delete(id: String) = viewModelScope.launch {
        repo.delete(id)
    }
}
