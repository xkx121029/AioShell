package com.aioshell.app.feature.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aioshell.app.core.data.repository.MessageRepository
import com.aioshell.app.core.data.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

/** 搜索结果条目：合并消息与其所属会话标题。 */
data class SearchHit(
    val messageId: String,
    val sessionId: String,
    val sessionTitle: String,
    val role: String,
    val content: String,
    val createdAt: Long,
    val reasoning: String? = null,
)

data class SearchUiState(
    val query: String = "",
    val searching: Boolean = false,
    val hits: List<SearchHit> = emptyList(),
)

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val sessionRepo: SessionRepository,
    private val messageRepo: MessageRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private val queryFlow = MutableStateFlow("")
    private var searchJob: Job? = null

    init {
        // 输入防抖 300ms 后触发搜索，避免每次按键都全表扫描
        viewModelScope.launch {
            queryFlow.debounce(300).collect { q ->
                _state.value = _state.value.copy(query = q, searching = true)
                val hits = if (q.isBlank()) emptyList() else messageRepo.search(q)
                _state.value = _state.value.copy(hits = hits.map { it.toHit() }, searching = false)
            }
        }
    }

    fun onQueryChange(q: String) {
        queryFlow.value = q
    }

    /** 消息 → 搜索结果条目，并补充所属会话标题。 */
    private suspend fun com.aioshell.app.core.data.model.ChatMessage.toHit(): SearchHit {
        val title = sessionRepo.getById(sessionId)?.title ?: "未知会话"
        return SearchHit(
            messageId = id,
            sessionId = sessionId,
            sessionTitle = title,
            role = role.name.lowercase(),
            content = content,
            createdAt = createdAt,
            reasoning = reasoningContent,
        )
    }
}