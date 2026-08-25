package com.aioshell.app.core.data.repository

import com.aioshell.app.core.data.model.ChatConfig
import com.aioshell.app.core.data.model.ChatMessage
import com.aioshell.app.core.data.model.MessageRole
import com.aioshell.app.core.data.network.ApiClient
import com.aioshell.app.core.data.network.ChatStreamEvent
import com.aioshell.app.core.data.network.MultimodalContentBuilder
import com.aioshell.app.core.data.network.OpenAIChatAdapter
import com.aioshell.app.core.data.network.RequestMessage
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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
     * @param imageBase64s 当前用户消息携带的图片（base64，可空）。图片附加到最后一条用户消息。
     */
    fun streamChatWithReasoning(
        config: ChatConfig,
        history: List<ChatMessage>,
        imageBase64s: List<String> = emptyList(),
    ): Flow<ChatStreamEvent> {
        val kept = history.filter { it.roleToApi() != null && it.content.isNotBlank() }
        val payload = kept.mapIndexed { index, m ->
            val isLastUser = m.role == MessageRole.USER && index == kept.lastIndex && imageBase64s.isNotEmpty()
            val content = if (isLastUser) {
                MultimodalContentBuilder.buildUserContent(m.content, imageBase64s)
            } else {
                JsonPrimitive(m.content)
            }
            RequestMessage(m.roleToApi()!!, content)
        }
        return adapter.streamChatEvents(config, payload)
    }

    /** 兼容入口：仅正文增量文本。 */
    fun streamChat(config: ChatConfig, history: List<ChatMessage>, imageBase64s: List<String> = emptyList()): Flow<String> =
        streamChatWithReasoning(config, history, imageBase64s)
            .map { if (it is ChatStreamEvent.ContentDelta) it.text else "" }

    /** 连接测试：校验 Base URL / API Key / 模型名是否可用。 */
    suspend fun testConnection(config: ChatConfig): Boolean = adapter.testConnection(config)
}