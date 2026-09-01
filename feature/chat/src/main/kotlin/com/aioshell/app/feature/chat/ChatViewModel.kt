package com.aioshell.app.feature.chat

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aioshell.app.core.data.audio.ModelManager
import com.aioshell.app.core.data.audio.TtsManager
import com.aioshell.app.core.data.audio.TtsState
import com.aioshell.app.core.data.audio.VoskRecognizer
import com.aioshell.app.core.data.audio.VoiceModelState
import com.aioshell.app.core.data.export.ConversationExporter
import com.aioshell.app.core.data.export.ExportFormat
import com.aioshell.app.core.data.image.ImageProcessor
import com.aioshell.app.core.data.model.ChatConfig
import com.aioshell.app.core.data.model.ChatMessage
import com.aioshell.app.core.data.model.MessageRole
import com.aioshell.app.core.data.model.MiscAiMeta
import com.aioshell.app.core.data.network.ApiException
import com.aioshell.app.core.data.network.ChatStreamEvent
import com.aioshell.app.core.data.repository.ChatRepository
import com.aioshell.app.core.data.repository.ConfigRepository
import com.aioshell.app.core.data.repository.MessageRepository
import com.aioshell.app.core.data.repository.MiscAiRepository
import com.aioshell.app.core.data.repository.SessionRepository
import com.aioshell.app.core.data.store.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    val draft: String = "",
    val messages: List<ChatMessage> = emptyList(),
    val loading: Boolean = true,
    val hasConfig: Boolean = false,
    val configName: String? = null,
    val isStreaming: Boolean = false,
    val reasoningEnabled: Boolean = true,
    val pendingImages: List<UiImage> = emptyList(),
    val isListening: Boolean = false,
    val speechText: String = "",
    val soundLevel: Float = 0f,
    val highlightMessageId: String? = null,
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val messageRepo: MessageRepository,
    private val sessionRepo: SessionRepository,
    private val configRepo: ConfigRepository,
    private val chatRepo: ChatRepository,
    private val miscAiRepo: MiscAiRepository,
    private val modelManager: ModelManager,
    private val ttsManager: TtsManager,
    private val exporter: ConversationExporter,
    private val settingsStore: SettingsStore,
    @ApplicationContext private val appContext: Context,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val sessionId: String = checkNotNull(savedStateHandle["sessionId"])
    private val highlightMessageId: String? =
        savedStateHandle.get<String>("highlight")?.takeIf { it.isNotBlank() }

    private val _state = MutableStateFlow(
        ChatUiState(sessionId = sessionId, highlightMessageId = highlightMessageId)
    )
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private val activeConfig: StateFlow<ChatConfig?> =
        configRepo.activeConfig
            .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, null)

    private var streamingJob: Job? = null
    private var voiceJob: Job? = null
    private var draftJob: Job? = null
    private var speechAccumulated = ""

    /** 上下文长度上限（用户+AI 历史条数，0 不限制）与自动朗读开关，来自设置。 */
    private var contextCount = 60
    private var autoTtsEnabled = false

    /** 语音模型下载 / 加载状态（来自 [ModelManager]）。 */
    val voiceModelState: StateFlow<VoiceModelState> = modelManager.state

    /** TTS 朗读状态（来自 [TtsManager]）。 */
    val ttsState: StateFlow<TtsState> = ttsManager.state

    init {
        viewModelScope.launch {
            val session = sessionRepo.getById(sessionId)
            session?.let { _state.value = _state.value.copy(title = it.title, draft = it.draft.orEmpty()) }
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
        viewModelScope.launch { settingsStore.contextMessageCount.collect { contextCount = it } }
        viewModelScope.launch { settingsStore.autoTts.collect { autoTtsEnabled = it } }
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
                // 会话级模型覆盖：该会话指定模型时优先，否则沿用档案模型
                val session = sessionRepo.getById(sessionId)
                val effectiveCfg = session?.modelOverride?.takeIf { it.isNotBlank() && it != cfg.model }?.let { cfg.copy(model = it) } ?: cfg
                // 是否为会话首条消息（新建对话的第一条用户消息）
                val isFirst = messageRepo.getInSession(sessionId).isEmpty()
                val imagePaths = images.map { it.path }
                _state.value = _state.value.copy(pendingImages = emptyList())
                messageRepo.addUserMessage(sessionId, raw, attachmentPaths = imagePaths)
                sessionRepo.touch(sessionId)
                val assistant = messageRepo.addAssistantMessage(sessionId)

                // 首条消息：用"杂项 AI"命名对话 + 分析意图，并把身份/风格建议注入主模型
                val systemPrompt = if (isFirst) {
                    val meta = chatRepo.analyze(pickMiscAiConfig(effectiveCfg), raw, pickMiscAiPrompt())
                    val finalTitle = meta.title.ifBlank { localTitle(raw) }
                    if (finalTitle.isNotBlank() && finalTitle != "新对话") {
                        sessionRepo.rename(sessionId, finalTitle)
                        _state.value = _state.value.copy(title = finalTitle)
                    }
                    buildSystemPrompt(meta)
                } else null

                // 发送后清空输入草稿
                if (_state.value.draft.isNotBlank()) {
                    sessionRepo.saveDraft(sessionId, null)
                    _state.value = _state.value.copy(draft = "")
                }

                val history = messageRepo.getInSession(sessionId).filterNot { it.id == assistant.id }
                val imageBase64s = withContext(Dispatchers.IO) {
                    imagePaths.map { ImageProcessor.encodeToBase64(File(it)) }
                }
                streamAssistant(effectiveCfg, assistant, history, imageBase64s, systemPrompt, contextCount)
                sessionRepo.touch(sessionId)
            } finally {
                _state.value = _state.value.copy(isStreaming = false)
            }
        }
    }

    /** 选择用于"杂项 AI"的模型档案：优先用户配置的独立档案，否则回退当前聊天模型。 */
    private suspend fun pickMiscAiConfig(fallback: ChatConfig): ChatConfig {
        val misc = miscAiRepo.get()
        val valid = misc.enabled && misc.baseUrl.isNotBlank() && misc.model.isNotBlank()
        if (!valid) return fallback
        return ChatConfig(
            id = "misc_ai", name = "杂项AI", baseUrl = misc.baseUrl,
            apiKey = misc.apiKey, model = misc.model, temperature = 0.2f,
        )
    }

    private suspend fun pickMiscAiPrompt(): String = if (miscAiRepo.get().enabled) miscAiRepo.get().prompt else ""

    /** 把杂项 AI 返回的元数据组织成主模型第一条请求的 system 握手信息。 */
    private fun buildSystemPrompt(meta: MiscAiMeta): String? {
        val parts = buildList {
            if (meta.identity.isNotBlank()) add("你在此对话中的身份：${meta.identity}。")
            if (meta.style.isNotBlank()) add("你的回答风格：${meta.style}。")
            if (meta.intent.isNotBlank()) add("用户本次对话的意图：${meta.intent}。")
        }
        return parts.joinToString("\n").takeIf { it.isNotBlank() }
    }

    /** 流式生成：思考内容与正文内容分流写入。 */
    private suspend fun streamAssistant(
        cfg: ChatConfig,
        assistant: ChatMessage,
        history: List<ChatMessage>,
        imageBase64s: List<String>,
        systemPrompt: String? = null,
        contextCount: Int = 0,
    ) {
        var acc = ""
        var reasoningAcc = ""
        runCatching {
            chatRepo.streamChatWithReasoning(cfg, history, imageBase64s, systemPrompt, contextCount).collect { event ->
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
            // AI 回复自动朗读：完成后若开启且内容非空，则用系统 TTS 朗读
            if (autoTtsEnabled && acc.isNotBlank()) {
                ttsManager.speak(acc.trim(), assistant.id)
            }
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
                val session = sessionRepo.getById(sessionId)
                val effectiveCfg = session?.modelOverride?.takeIf { it.isNotBlank() && it != cfg.model }?.let { cfg.copy(model = it) } ?: cfg
                streamAssistant(effectiveCfg, assistant, history, emptyList(), null, contextCount)
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

    /** 收藏 / 取消收藏消息。 */
    fun toggleStarred(messageId: String) = viewModelScope.launch {
        messageRepo.toggleStarred(messageId)
    }

    /**
     * 编辑消息正文。若被编辑的是"用户消息"，删除其之后的所有消息并重新生成 AI 回复；
     * 若是 AI 消息，则仅修正本地正文（作为手动备注）。
     */
    fun editMessage(messageId: String, newContent: String) {
        val raw = newContent.trim()
        if (raw.isEmpty()) return
        val target = _state.value.messages.firstOrNull { it.id == messageId } ?: return
        if (_state.value.isStreaming) return
        viewModelScope.launch {
            messageRepo.updateContent(messageId, raw)
            // 仅用户消息编辑触发重新生成：删除后续消息（含本条用户消息之后的 AI 回复）
            if (target.role == MessageRole.USER) {
                val messages = _state.value.messages
                val idx = messages.indexOfFirst { it.id == messageId }
                val toDelete = messages.drop(idx + 1).map { it.id }
                toDelete.forEach { messageRepo.delete(it) }
                val cfg = activeConfig.value ?: return@launch
                val session = sessionRepo.getById(sessionId)
                val effectiveCfg = session?.modelOverride?.takeIf { it.isNotBlank() && it != cfg.model }?.let { cfg.copy(model = it) } ?: cfg
                val assistant = messageRepo.addAssistantMessage(sessionId)
                val history = messageRepo.getInSession(sessionId).filterNot { it.id == assistant.id }
                if (history.isEmpty()) { messageRepo.delete(assistant.id); return@launch }
                streamingJob?.cancel()
                _state.value = _state.value.copy(isStreaming = true)
                streamingJob = viewModelScope.launch {
                    try {
                        streamAssistant(effectiveCfg, assistant, history, emptyList(), null, contextCount)
                        sessionRepo.touch(sessionId)
                    } finally {
                        _state.value = _state.value.copy(isStreaming = false)
                    }
                }
            } else {
                sessionRepo.touch(sessionId)
            }
        }
    }

    fun rename(title: String) = viewModelScope.launch {
        sessionRepo.rename(sessionId, title)
        _state.value = _state.value.copy(title = title)
    }

    /** 输入草稿自动保存：停止输入 500ms 后持久化，空内容自动清空。 */
    fun updateDraft(text: String) {
        draftJob?.cancel()
        draftJob = viewModelScope.launch {
            delay(500)
            val trimmed = text.trim()
            sessionRepo.saveDraft(sessionId, trimmed.ifBlank { null })
            _state.value = _state.value.copy(draft = trimmed)
        }
    }

    /** 清空输入草稿（发送 / 退出会话时调用）。 */
    fun clearDraft() = viewModelScope.launch {
        draftJob?.cancel()
        sessionRepo.saveDraft(sessionId, null)
        _state.value = _state.value.copy(draft = "")
    }

    /** 朗读/停止朗读某条消息（仅对 AI 与错误消息开放）。 */
    fun toggleSpeak(message: ChatMessage) {
        if (message.content.isBlank()) return
        if (ttsManager.isPlaying(message.id)) {
            ttsManager.stop()
        } else {
            ttsManager.speak(message.content, message.id)
        }
    }

    fun stopSpeak() = ttsManager.stop()

    /** 导出当前会话为指定格式并调起系统分享。 */
    fun exportSession(format: ExportFormat) = viewModelScope.launch {
        exporter.exportAndShare(sessionId, format)
    }

    /** 导出当前会话为长图并分享。 */
    fun exportLongImage() = viewModelScope.launch(Dispatchers.IO) {
        exporter.exportAndShareLongImage(sessionId)
    }

    /** 导出当前会话为 PDF 并分享。 */
    fun exportPdf() = viewModelScope.launch(Dispatchers.IO) {
        exporter.exportAndSharePdf(sessionId)
    }

    override fun onCleared() {
        ttsManager.stop()
        super.onCleared()
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
        _state.value = _state.value.copy(isListening = true, soundLevel = 0f)
        voiceJob = viewModelScope.launch {
            runCatching { modelManager.ensureModel() }
                .onSuccess { model ->
                    VoskRecognizer.listen(model).collect { r ->
                        _state.value = _state.value.copy(soundLevel = r.volume)
                        if (r.finalized) {
                            speechAccumulated += r.text
                            _state.value = _state.value.copy(speechText = speechAccumulated)
                        } else if (r.text.isNotEmpty()) {
                            _state.value = _state.value.copy(speechText = r.text)
                        }
                    }
                }
                .onFailure {
                    _state.value = _state.value.copy(isListening = false, speechText = "", soundLevel = 0f)
                }
        }
    }

    /** 取消语音输入：停止识别且不把结果回填输入框。 */
    fun cancelVoice() {
        voiceJob?.cancel()
        voiceJob = null
        speechAccumulated = ""
        _state.value = _state.value.copy(isListening = false, speechText = "")
    }

    private companion object { const val MAX_IMAGES = 4 }
}

/**
 * 本地规则生成会话标题：取首条消息第一行、去掉 Markdown 标记、截断到 ~20 字。
 * 仅在杂项 AI 未返回标题时作为回退，避免千篇一律的"新对话"。
 */
private fun localTitle(raw: String): String {
    val firstLine = raw.lineSequence().firstOrNull { it.isNotBlank() } ?: return ""
    val cleaned = firstLine
        .replace(Regex("^#{1,6}\\s+"), "")
        .replace(Regex("^>\\s?"), "")
        .replace("`", "")
        .replace(Regex("\\*\\*|\\*|__|_|~~"), "")
        .replace(Regex("\\[([^\\]]+)]\\([^)]*\\)"), "$1")
        .replace(Regex("!\\[[^\\]]*]\\([^)]*\\)"), "[图片]")
        .trim()
    return cleaned.take(20).ifBlank { "新对话" }
}