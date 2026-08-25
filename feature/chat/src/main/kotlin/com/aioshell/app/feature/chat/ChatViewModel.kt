package com.aioshell.app.feature.chat

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aioshell.app.core.data.audio.ModelManager
import com.aioshell.app.core.data.audio.VoskRecognizer
import com.aioshell.app.core.data.audio.VoiceModelState
import com.aioshell.app.core.data.image.ImageProcessor
import com.aioshell.app.core.data.model.ChatConfig
import com.aioshell.app.core.data.model.ChatMessage
import com.aioshell.app.core.data.network.ApiException
import com.aioshell.app.core.data.network.ChatStreamEvent
import com.aioshell.app.core.data.repository.ChatRepository
import com.aioshell.app.core.data.repository.ConfigRepository
import com.aioshell.app.core.data.repository.MessageRepository
import com.aioshell.app.core.data.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UiImage(val id: String, val path: String)

data class ChatUiState(
    val sessionId: String = "",
    val title: String = "对话",
    val messages: List<ChatMessage> = emptyList(),
    val loading: Boolean = true,
    val hasConfig: Boolean = false,
    val configName: String? = null,
    val isStreaming: Boolean = false,
    val reasoningEnabled: Boolean = true,
    val pendingImages: List<UiImage> = emptyList(),
    val isListening: Boolean = false,
    val speechText: String = "",
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val messageRepo: MessageRepository,
    private val sessionRepo: SessionRepository,
    private val configRepo: ConfigRepository,
    private val chatRepo: ChatRepository,
    private val modelManager: ModelManager,
    @ApplicationContext private val appContext: Context,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val sessionId: String = checkNotNull(savedStateHandle["sessionId"])

    private val _state = MutableStateFlow(ChatUiState(sessionId = sessionId))
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private val activeConfig: StateFlow<ChatConfig?> =
        configRepo.activeConfig
            .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, null)

    private var streamingJob: Job? = null
    private var voiceJob: Job? = null
    private var speechAccumulated = ""

    /** 语音模型下载 / 加载状态（来自 [ModelManager]）。 */
    val voiceModelState: StateFlow<VoiceModelState> = modelManager.state

    init {
        viewModelScope.launch {
            val session = sessionRepo.getById(sessionId)
            session?.let { _state.value = _state.value.copy(title = it.title) }
            messageRepo.observeInSession(sessionId).collectLatest { msgs ->
                _state.value = _state.value.copy(messages = msgs, loading = false)
            }
        }
        viewModelScope.launch {
            activeConfig.collectLatest { cfg ->
                if (cfg != null) {
                    _state.value = _state.value.copy(
                        hasConfig = true,
                        configName = cfg.name.ifBlank { cfg.model },
                        reasoningEnabled = cfg.reasoningEnabled,
                    )
                } else {
                    _state.value = _state.value.copy(hasConfig = false, configName = null, reasoningEnabled = true)
                }
            }
        }
    }

    /** 图片选择（Photo Picker 回调）：压缩到私有目录，存入待发送列表。 */
    fun onImagesPicked(uris: List<Uri>) {
        if (_state.value.pendingImages.size >= MAX_IMAGES) return
        viewModelScope.launch {
            val current = _state.value.pendingImages.toMutableList()
            for (uri in uris.take(MAX_IMAGES)) {
                if (current.size >= MAX_IMAGES) break
                runCatching { ImageProcessor.compress(appContext, uri) }
                    .onSuccess { file -> current.add(UiImage(file.path, file.path)) }
            }
            _state.value = _state.value.copy(pendingImages = current)
        }
    }

    fun removePendingImage(path: String) {
        _state.value = _state.value.copy(pendingImages = _state.value.pendingImages.filterNot { it.path == path })
    }

    fun send(text: String) {
        val raw = text.trim()
        val images = _state.value.pendingImages
        if (raw.isEmpty() && images.isEmpty()) return
        if (_state.value.isStreaming) return
        val cfg = activeConfig.value ?: return

        streamingJob?.cancel()
        streamingJob = viewModelScope.launch {
            _state.value = _state.value.copy(isStreaming = true)
            try {
                val imagePaths = images.map { it.path }
                _state.value = _state.value.copy(pendingImages = emptyList())
                messageRepo.addUserMessage(sessionId, raw, attachmentPaths = imagePaths)
                sessionRepo.touch(sessionId)
                val assistant = messageRepo.addAssistantMessage(sessionId)
                val history = messageRepo.getInSession(sessionId).filterNot { it.id == assistant.id }
                val imageBase64s = withContext(Dispatchers.IO) {
                    imagePaths.map { ImageProcessor.encodeToBase64(File(it)) }
                }
                streamAssistant(cfg, assistant, history, imageBase64s)
                sessionRepo.touch(sessionId)
            } finally {
                _state.value = _state.value.copy(isStreaming = false)
            }
        }
    }

    /** 流式生成：思考内容与正文内容分流写入。 */
    private suspend fun streamAssistant(
        cfg: ChatConfig,
        assistant: ChatMessage,
        history: List<ChatMessage>,
        imageBase64s: List<String>,
    ) {
        var acc = ""
        var reasoningAcc = ""
        runCatching {
            chatRepo.streamChatWithReasoning(cfg, history, imageBase64s).collect { event ->
                when (event) {
                    is ChatStreamEvent.ReasoningDelta -> {
                        reasoningAcc += event.text
                        messageRepo.updateAssistantReasoning(assistant.id, reasoningAcc, 0)
                    }
                    is ChatStreamEvent.ContentDelta -> {
                        acc += event.text
                        messageRepo.updateAssistantText(assistant.id, acc)
                    }
                    is ChatStreamEvent.Done -> {
                        if (reasoningAcc.isNotEmpty()) {
                            messageRepo.updateAssistantReasoning(assistant.id, reasoningAcc, event.reasoningDurationMs)
                        }
                    }
                }
            }
        }.onSuccess {
            messageRepo.markAssistantDone(assistant.id)
        }.onFailure { e ->
            val msg = (e as? ApiException)?.message ?: (e.message ?: "请求失败")
            messageRepo.markAssistantError(assistant.id, msg)
        }
    }

    fun stopStreaming() {
        streamingJob?.cancel()
        streamingJob = null
        _state.value = _state.value.copy(isStreaming = false)
    }

    /** 重新生成某条 AI 回复。 */
    fun regenerate(assistantId: String) {
        if (_state.value.isStreaming) return
        val cfg = activeConfig.value ?: return
        val idx = _state.value.messages.indexOfFirst { it.id == assistantId }
        if (idx < 0) return

        streamingJob?.cancel()
        streamingJob = viewModelScope.launch {
            _state.value = _state.value.copy(isStreaming = true)
            try {
                messageRepo.delete(assistantId)
                val assistant = messageRepo.addAssistantMessage(sessionId)
                val history = messageRepo.getInSession(sessionId).filterNot { it.id == assistant.id }
                if (history.isEmpty()) { messageRepo.delete(assistant.id); return@launch }
                streamAssistant(cfg, assistant, history, emptyList())
                sessionRepo.touch(sessionId)
            } finally {
                _state.value = _state.value.copy(isStreaming = false)
            }
        }
    }

    /** 快捷 / 主开关：修改当前模型档案的思考展示状态。 */
    fun toggleReasoning() {
        val cfg = activeConfig.value ?: return
        viewModelScope.launch { configRepo.setReasoningEnabled(cfg.id, !_state.value.reasoningEnabled) }
    }

    fun rename(title: String) = viewModelScope.launch {
        sessionRepo.rename(sessionId, title)
        _state.value = _state.value.copy(title = title)
    }

    /**
     * 语音输入开关：点击开始 / 停止本地识别。
     * 停止时把识别到的文本通过 [onResult] 交回 UI 填入输入框。
     */
    fun toggleVoice(onResult: (String) -> Unit) {
        if (_state.value.isListening) {
            voiceJob?.cancel()
            voiceJob = null
            _state.value = _state.value.copy(isListening = false, speechText = "")
            if (speechAccumulated.isNotBlank()) onResult(speechAccumulated)
            speechAccumulated = ""
            return
        }
        speechAccumulated = ""
        _state.value = _state.value.copy(isListening = true)
        voiceJob = viewModelScope.launch {
            runCatching { modelManager.ensureModel() }
                .onSuccess { model ->
                    VoskRecognizer.listen(model).collect { r ->
                        if (r.finalized) {
                            speechAccumulated += r.text
                            _state.value = _state.value.copy(speechText = speechAccumulated)
                        } else {
                            _state.value = _state.value.copy(speechText = r.text)
                        }
                    }
                }
                .onFailure {
                    _state.value = _state.value.copy(isListening = false, speechText = "")
                }
        }
    }

    private companion object { const val MAX_IMAGES = 4 }
}