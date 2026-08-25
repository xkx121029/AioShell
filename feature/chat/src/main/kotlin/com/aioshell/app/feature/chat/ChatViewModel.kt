package com.aioshell.app.feature.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aioshell.app.core.data.model.ChatConfig
import com.aioshell.app.core.data.model.ChatMessage
import com.aioshell.app.core.data.network.ApiException
import com.aioshell.app.core.data.repository.ChatRepository
import com.aioshell.app.core.data.repository.ConfigRepository
import com.aioshell.app.core.data.repository.MessageRepository
import com.aioshell.app.core.data.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ChatUiState(
    val sessionId: String = "",
    val title: String = "对话",
    val messages: List<ChatMessage> = emptyList(),
    val loading: Boolean = true,
    val hasConfig: Boolean = false,
    val configName: String? = null,
    val isStreaming: Boolean = false,
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val messageRepo: MessageRepository,
    private val sessionRepo: SessionRepository,
    private val configRepo: ConfigRepository,
    private val chatRepo: ChatRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val sessionId: String = checkNotNull(savedStateHandle["sessionId"])

    private val _state = MutableStateFlow(ChatUiState(sessionId = sessionId))
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private val activeConfig: StateFlow<ChatConfig?> =
        configRepo.activeConfig
            .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, null)

    private var streamingJob: Job? = null

    init {
        viewModelScope.launch {
            val session = sessionRepo.getById(sessionId)
            session?.let { _state.value = _state.value.copy(title = it.title) }
            messageRepo.observeInSession(sessionId).collectLatest { msgs ->
                _state.value = _state.value.copy(messages = msgs, loading = false)
            }
        }
        viewModelScope.launch {
            activeConfig.filterNotNull().collectLatest { cfg ->
                _state.value = _state.value.copy(hasConfig = true, configName = cfg.name.ifBlank { cfg.model })
            }
            // 配置为空时同步状态
            activeConfig.collectLatest { cfg ->
                if (cfg == null) _state.value = _state.value.copy(hasConfig = false, configName = null)
            }
        }
    }

    fun send(text: String) {
        val raw = text.trim()
        if (raw.isEmpty() || _state.value.isStreaming) return
        val cfg = activeConfig.value
        if (cfg == null) return

        streamingJob?.cancel()
        streamingJob = viewModelScope.launch {
            _state.value = _state.value.copy(isStreaming = true)
            try {
                messageRepo.addUserMessage(sessionId, raw)
                sessionRepo.touch(sessionId)
                val assistant = messageRepo.addAssistantMessage(sessionId)
                // 上下文记忆：取该消息之前的所有历史（去掉本次占位消息）
                val history = messageRepo.getInSession(sessionId).filterNot { it.id == assistant.id }
                var acc = ""
                runCatching {
                    chatRepo.streamChat(cfg, history).collect { delta ->
                        acc += delta
                        messageRepo.updateAssistantText(assistant.id, acc)
                    }
                }.onSuccess {
                    messageRepo.markAssistantDone(assistant.id)
                }.onFailure { e ->
                    val msg = (e as? ApiException)?.message ?: (e.message ?: "请求失败")
                    messageRepo.markAssistantError(assistant.id, msg)
                }
                sessionRepo.touch(sessionId)
            } finally {
                _state.value = _state.value.copy(isStreaming = false)
            }
        }
    }

    fun stopStreaming() {
        streamingJob?.cancel()
        streamingJob = null
        _state.value = _state.value.copy(isStreaming = false)
    }

    fun rename(title: String) = viewModelScope.launch {
        sessionRepo.rename(sessionId, title)
        _state.value = _state.value.copy(title = title)
    }
}