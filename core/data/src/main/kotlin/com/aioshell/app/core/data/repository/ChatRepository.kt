package com.aioshell.app.core.data.repository

import com.aioshell.app.core.data.model.ChatConfig
import com.aioshell.app.core.data.model.ChatMessage
import com.aioshell.app.core.data.model.MessageRole
import com.aioshell.app.core.data.model.MiscAiMeta
import com.aioshell.app.core.data.network.ApiClient
import com.aioshell.app.core.data.network.ChatStreamEvent
import com.aioshell.app.core.data.network.MultimodalContentBuilder
import com.aioshell.app.core.data.network.OpenAIChatAdapter
import com.aioshell.app.core.data.network.RequestMessage
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive

/**
 * 对话编排仓库：把历史消息(上下文记忆)与用户配置组装成请求，
 * 返回流式事件（含思考内容与正文内容分流）。
 */
@Singleton
class ChatRepository @Inject constructor(private val apiClient: ApiClient) {

    private val adapter = OpenAIChatAdapter(apiClient.okHttpClient)

    private fun ChatMessage.roleToApi(): String? = when (role) {
        MessageRole.USER -> "user"
        MessageRole.ASSISTANT -> "assistant"
        MessageRole.SYSTEM -> "system"
        MessageRole.ERROR -> null
    }

    /**
     * 携带历史消息发起流式对话（含思考分流）。
     * @param systemPrompt 可选的 system 握手信息（例如杂项 AI 给出的身份/风格建议），非空则置于消息最前。
     * @param imageBase64s 当前用户消息携带的图片（base64，可空）。图片附加到最后一条用户消息。
     * @param contextCount 最多携带的历史消息条数（0 表示不限制）。用于"上下文长度控制"，控制用量与成本。
     */
    fun streamChatWithReasoning(
        config: ChatConfig,
        history: List<ChatMessage>,
        imageBase64s: List<String> = emptyList(),
        systemPrompt: String? = null,
        contextCount: Int = 0,
    ): Flow<ChatStreamEvent> {
        var kept = history.filter { it.roleToApi() != null && it.content.isNotBlank() }
        // 上下文长度控制：仅保留最近 contextCount 条消息，且尽量保留最后一条用户消息
        if (contextCount > 0 && kept.size > contextCount) {
            while (kept.size > contextCount && kept.first().roleToApi() == "assistant") kept = kept.drop(1)
            if (kept.size > contextCount) kept = kept.takeLast(contextCount)
        }
        val body = mutableListOf<RequestMessage>()
        if (!systemPrompt.isNullOrBlank()) {
            body += RequestMessage("system", JsonPrimitive(systemPrompt))
        }
        kept.mapIndexedTo(body) { index, m ->
            val isLastUser = m.role == MessageRole.USER && index == kept.lastIndex && imageBase64s.isNotEmpty()
            val content = if (isLastUser) {
                MultimodalContentBuilder.buildUserContent(m.content, imageBase64s)
            } else {
                JsonPrimitive(m.content)
            }
            RequestMessage(m.roleToApi()!!, content)
        }
        return adapter.streamChatEvents(config, body)
    }

    /** 兼容入口：仅正文增量文本。 */
    fun streamChat(config: ChatConfig, history: List<ChatMessage>, imageBase64s: List<String> = emptyList()): Flow<String> =
        streamChatWithReasoning(config, history, imageBase64s)
            .map { if (it is ChatStreamEvent.ContentDelta) it.text else "" }

    /**
     * 调用"杂项 AI"对首条用户消息做 命名 + 意图分析，并要求返回身份/回答风格建议（纯 JSON）。
     * @param config 用于调用的模型档案（杂项 AI）。
     * @param userText 用户首条消息文本。
     * @param customPrompt 用户在杂项 AI 档案中自定义的提示词前缀（可为空，使用默认）。
     * 解析失败时返回空字段的 [MiscAiMeta]，不抛出，避免阻塞主对话。
     */
    suspend fun analyze(config: ChatConfig, userText: String, customPrompt: String = ""): MiscAiMeta {
        val prompt = buildAnalyzePrompt(userText, customPrompt)
        val messages = listOf(RequestMessage("user", JsonPrimitive(prompt)))
        val raw = runCatching { adapter.complete(config, messages) }.getOrNull().orEmpty()
        val cleaned = stripCodeFence(raw)
        return runCatching { Json { ignoreUnknownKeys = true }.decodeFromString<MiscAiMeta>(cleaned) }
            .getOrElse { MiscAiMeta() }
    }

    /** 组装杂项 AI 提示词：强制返回纯 JSON。 */
    private fun buildAnalyzePrompt(userText: String, customPrompt: String): String {
        val base: String
        if (customPrompt.isNotBlank()) {
            base = customPrompt.trim().trimEnd()
        } else {
            base = "你是一名对话智能体，负责为一场新的 AI 对话做开场分析。"
        }
        return buildString {
            append(base).append("\n\n")
            append("请分析用户首条消息，返回严格的 JSON（不要输出任何 Markdown 代码围栏或多余文字），需包含四个字段：\n")
            append("title: 简洁的中文对话标题（不超过 12 字）；\n")
            append("intent: 一句话概括用户的意图；\n")
            append("identity: 据此意图，建议主聊天模型采用的身份定位（如“资深编程导师”）；\n")
            append("style: 据此意图，建议主聊天模型采用的回答风格（如“条理清晰、先给结论再解释”）。\n\n")
            append("用户首条消息：\n").append(userText)
        }
    }

    /** 剥离可能的 ```json ... ``` 代码围栏，仅保留 JSON 本身。 */
    private fun stripCodeFence(raw: String): String {
        val idx = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        return if (idx >= 0 && end > idx) raw.substring(idx, end + 1) else raw.trim()
    }

    /** 连接测试：校验 Base URL / API Key / 模型名是否可用。 */
    suspend fun testConnection(config: ChatConfig): Boolean = adapter.testConnection(config)
}