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
import com.aioshell.app.core.data.repository.KnowledgeRepository
import com.aioshell.app.core.data.repository.MessageRepository
import com.aioshell.app.core.data.repository.MiscAiRepository
import com.aioshell.app.core.data.repository.PersonaRepository
import com.aioshell.app.core.data.repository.SessionRepository
import com.aioshell.app.core.data.repository.WebSearchRepository
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UiImage(val id: String, val path: String)

/** 一个可切换的分支（叶子消息）概要。 */
data class BranchSummary(
    val leafId: String,
    val preview: String,
    val createdAt: Long,
    val isCurrent: Boolean,
)

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
    /** 待回复的引用目标：长按消息「引用」后设置，发送后清除。 */
    val draftReply: ChatMessage? = null,
    /** 联网搜索开关（来自设置）。 */
    val webSearchEnabled: Boolean = false,
    /** 是否正在检索联网内容（发送消息前短暂置真）。 */
    val searching: Boolean = false,
    /** 本地知识库（RAG）开关（来自设置）。 */
    val knowledgeEnabled: Boolean = false,
    /** 是否正在检索本地知识库。 */
    val retrieving: Boolean = false,
    /** 免提语音模式开关（来自设置）。 */
    val handsFree: Boolean = false,
    /** 可切换的分支叶子列表（对话分支回溯）。 */
    val branchLeaves: List<BranchSummary> = emptyList(),
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val messageRepo: MessageRepository,
    private val sessionRepo: SessionRepository,
    private val configRepo: ConfigRepository,
    private val chatRepo: ChatRepository,
    private val miscAiRepo: MiscAiRepository,
    private val personaRepo: PersonaRepository,
    private val webSearchRepo: WebSearchRepository,
    private val knowledgeRepo: KnowledgeRepository,
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
    private var handsFreeJob: Job? = null
    private var draftJob: Job? = null
    private var speechAccumulated = ""

    /** 当前活动分支的叶子消息 id（null = 默认线性链）。 */
    private var activeLeafId: String? = null

    /** 会话内全部消息（含其它分支），用于重新解析当前分支。 */
    private var allMessages: List<ChatMessage> = emptyList()

    /** 上下文长度上限（用户+AI 历史条数，0 不限制）与自动朗读开关，来自设置。 */
    private var contextCount = 60
    private var autoTtsEnabled = false
    private var handsFreeEnabled = false

    /** 语音模型下载 / 加载状态（来自 [ModelManager]）。 */
    val voiceModelState: StateFlow<VoiceModelState> = modelManager.state

    /** TTS 朗读状态（来自 [TtsManager]）。 */
    val ttsState: StateFlow<TtsState> = ttsManager.state

    init {
        viewModelScope.launch {
            val session = sessionRepo.getById(sessionId)
            session?.let {
                activeLeafId = it.leafId
                _state.value = _state.value.copy(title = it.title, draft = it.draft.orEmpty())
            }
            messageRepo.observeInSession(sessionId).collectLatest { msgs ->
                allMessages = msgs
                applyBranchResolution()
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
        viewModelScope.launch {
            settingsStore.webSearchEnabled.collect { enabled ->
                _state.value = _state.value.copy(webSearchEnabled = enabled)
            }
        }
        viewModelScope.launch {
            settingsStore.knowledgeEnabled.collect { enabled ->
                _state.value = _state.value.copy(knowledgeEnabled = enabled)
            }
        }
        viewModelScope.launch {
            settingsStore.handsFreeVoice.collect { enabled ->
                handsFreeEnabled = enabled
                _state.value = _state.value.copy(handsFree = enabled)
            }
        }
    }

    /** 按当前活动分支重算视图消息与分支列表。 */
    private fun applyBranchResolution() {
        val chain = resolveActiveChain(allMessages, activeLeafId)
        val leaves = computeLeaves(allMessages, activeLeafId)
        _state.value = _state.value.copy(
            messages = chain,
            loading = false,
            branchLeaves = leaves,
        )
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

        // 引用目标：发送后清除
        val reply = _state.value.draftReply
        // 当前分支叶子：新消息作为其子节点（无则作为根）
        val parentId = activeLeafId

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
                _state.value = _state.value.copy(pendingImages = emptyList(), draftReply = null)
                messageRepo.addUserMessage(
                    sessionId, raw, attachmentPaths = imagePaths,
                    replyToRole = reply?.role, replyToContent = reply?.content,
                    parentMessageId = parentId,
                )
                sessionRepo.touch(sessionId)
                val assistant = messageRepo.addAssistantMessage(sessionId, parentMessageId = requireNotNull(messageRepo.getInSession(sessionId).lastOrNull()?.id))
                // 新消息成为当前分支叶子
                setActiveLeaf(assistant.id)

                // 首条消息：用"杂项 AI"命名对话 + 分析意图；人格预设为基础 system，杂项 AI meta 叠加补充（均不落库）
                val metaPrompt = if (isFirst) {
                    val meta = chatRepo.analyze(pickMiscAiConfig(effectiveCfg), raw, pickMiscAiPrompt())
                    val finalTitle = meta.title.ifBlank { localTitle(raw) }
                    if (finalTitle.isNotBlank() && finalTitle != "新对话") {
                        sessionRepo.rename(sessionId, finalTitle)
                        _state.value = _state.value.copy(title = finalTitle)
                    }
                    buildSystemPrompt(meta)
                } else null
                // 联网搜索增强：开启时先检索真实内容，再注入 system 上下文（失败则静默降级）
                var searchContext = ""
                if (_state.value.webSearchEnabled && raw.isNotBlank()) {
                    _state.value = _state.value.copy(searching = true)
                    searchContext = runCatching { webSearchRepo.search(raw) }
                        .getOrNull()?.context.orEmpty()
                    _state.value = _state.value.copy(searching = false)
                }
                // 本地知识库（RAG）：开启时检索相关文档块，注入 system 上下文（失败则静默降级）
                var knowledgeContext = ""
                if (_state.value.knowledgeEnabled && raw.isNotBlank()) {
                    _state.value = _state.value.copy(retrieving = true)
                    knowledgeContext = runCatching { knowledgeRepo.retrieveContext(raw) }
                        .getOrDefault("")
                    _state.value = _state.value.copy(retrieving = false)
                }
                val personaPrompt = currentPersonaPrompt()
                val systemPrompt = buildList {
                    personaPrompt?.let { add(it) }
                    metaPrompt?.let { add(it) }
                    searchContext.takeIf { it.isNotBlank() }?.let { add(it) }
                    knowledgeContext.takeIf { it.isNotBlank() }?.let { add(it) }
                }.joinToString("\n").takeIf { it.isNotBlank() }

                // 发送后清空输入草稿
                if (_state.value.draft.isNotBlank()) {
                    sessionRepo.saveDraft(sessionId, null)
                    _state.value = _state.value.copy(draft = "")
                }

                val history = resolveActiveChain(messageRepo.getInSession(sessionId), activeLeafId)
                    .filterNot { it.id == assistant.id }
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

    /** 把杂项 AI 返回的元数据组织成 system 握手信息。 */
    private fun buildSystemPrompt(meta: MiscAiMeta): String? {
        val parts = buildList {
            if (meta.identity.isNotBlank()) add("你在此对话中的身份：${meta.identity}。")
            if (meta.style.isNotBlank()) add("你的回答风格：${meta.style}。")
            if (meta.intent.isNotBlank()) add("用户本次对话的意图：${meta.intent}。")
        }
        return parts.joinToString("\n").takeIf { it.isNotBlank() }
    }

    /** 读取当前选中的人格预设，组织成基础 system 提示（无内容时返回 null）。 */
    private suspend fun currentPersonaPrompt(): String? {
        val p = runCatching { personaRepo.currentPersona.first() }.getOrNull() ?: return null
        if (!p.hasContent) return null
        return buildList {
            if (p.identity.isNotBlank()) add(p.identity.trimEnd().removeSuffix("。") + "。")
            if (p.style.isNotBlank()) add("回答风格：${p.style.trimEnd().removeSuffix("。")}。")
            if (p.prompt.isNotBlank()) add(p.prompt.trim())
        }.joinToString("\n")
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
            // 自动朗读：自动朗读开关或免提语音模式下朗读 AI 回复
            if ((autoTtsEnabled || handsFreeEnabled) && acc.isNotBlank()) {
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
                streamAssistant(effectiveCfg, assistant, history, emptyList(), currentPersonaPrompt(), contextCount)
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
                        streamAssistant(effectiveCfg, assistant, history, emptyList(), currentPersonaPrompt(), contextCount)
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

    /** 设置待回复的引用目标（长按消息「引用」触发）。 */
    fun setReply(target: ChatMessage?) {
        _state.value = _state.value.copy(draftReply = target)
    }

    /** 清除待回复的引用目标。 */
    fun clearReply() = setReply(null)

    /** 快捷 / 持久开关：修改联网搜索的全局启用状态。 */
    fun toggleWebSearch() = viewModelScope.launch {
        settingsStore.setWebSearchEnabled(!_state.value.webSearchEnabled)
    }

    /** 快捷 / 持久开关：修改本地知识库（RAG）的全局启用状态。 */
    fun toggleKnowledge() = viewModelScope.launch {
        settingsStore.setKnowledgeEnabled(!_state.value.knowledgeEnabled)
    }

    /** 免提语音模式开关：开启后识别完成自动发送，AI 回复自动朗读。 */
    fun toggleHandsFree() = viewModelScope.launch {
        val on = !_state.value.handsFree
        handsFreeEnabled = on
        settingsStore.setHandsFreeVoice(on)
        _state.value = _state.value.copy(handsFree = on)
        // 关闭时停止正在进行的语音监听
        if (!on) {
            voiceJob?.cancel()
            voiceJob = null
            _state.value = _state.value.copy(isListening = false, speechText = "")
            speechAccumulated = ""
        }
    }

    /** 在指定消息处派生新分支（新消息以 [fromMessageId] 为父节点继续）。 */
    fun startBranch(fromMessageId: String) {
        if (fromMessageId.isBlank()) return
        viewModelScope.launch {
            activeLeafId = fromMessageId
            sessionRepo.setBranchLeaf(sessionId, fromMessageId)
            applyBranchResolution()
        }
    }

    /** 切换到指定分支（[leafId] 为该分支的叶子消息）。 */
    fun switchBranch(leafId: String) {
        if (leafId.isBlank()) return
        viewModelScope.launch {
            activeLeafId = leafId
            sessionRepo.setBranchLeaf(sessionId, leafId)
            applyBranchResolution()
        }
    }

    /** 恢复默认线性视图（等价于切换到最新分支，或清空 leafId 后重解析）。 */
    fun resetBranch() {
        viewModelScope.launch {
            activeLeafId = allMessages.maxByOrNull { it.createdAt }?.id
            sessionRepo.setBranchLeaf(sessionId, activeLeafId)
            applyBranchResolution()
        }
    }

    private suspend fun setActiveLeaf(leaf: String) {
        activeLeafId = leaf
        sessionRepo.setBranchLeaf(sessionId, leaf)
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
                            // 免提语音：识别完成后自动发送，回复由流式结束自动朗读
                            if (handsFreeEnabled && speechAccumulated.isNotBlank()) {
                                val text = speechAccumulated
                                speechAccumulated = ""
                                _state.value = _state.value.copy(speechText = "", isListening = false)
                                send(text)
                                return@collect
                            }
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
 * 从 [all] 中解析出当前活动分支链：从 [leafId] 沿父指针回溯到根，再按时间升序返回。
 * leafId 为 null 或找不到时，回退为按时间升序的全部消息（默认线性，兼容升级前数据）。
 */
private fun resolveActiveChain(all: List<ChatMessage>, leafId: String?): List<ChatMessage> {
    if (all.isEmpty()) return all
    val map = all.associateBy { it.id }
    if (leafId.isNullOrBlank() || map[leafId] == null) {
        // 未分支：线性返回（如旧数据 parentId 全空）
        return all.sortedBy { it.createdAt }
    }
    val chain = ArrayList<ChatMessage>()
    var cur: String? = leafId
    val guard = HashSet<String>()
    while (cur != null && guard.add(cur)) {
        val msg = map[cur] ?: break
        chain.add(msg)
        cur = msg.parentMessageId
    }
    return chain.sortedBy { it.createdAt }
}

/** 枚举会话内所有分支叶子（没有子消息的消息），用于分支切换面板。 */
private fun computeLeaves(all: List<ChatMessage>, activeLeafId: String?): List<BranchSummary> {
    if (all.isEmpty()) return emptyList()
    val children = all.mapNotNull { it.parentMessageId }.toHashSet()
    val leaves = all
        .filter { it.id !in children }
        .sortedByDescending { it.createdAt }
    val activeId = activeLeafId
    return leaves.map { leaf ->
        BranchSummary(
            leafId = leaf.id,
            preview = leaf.content.lineSequence().firstOrNull { it.isNotBlank() }
                ?.take(30) ?: (if (leaf.role == MessageRole.USER) "[图像]" else "[空回复]"),
            createdAt = leaf.createdAt,
            isCurrent = leaf.id == activeId,
        )
    }
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