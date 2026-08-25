package com.aioshell.app.core.data.repository

import com.aioshell.app.core.data.model.ChatConfig
import com.aioshell.app.core.data.model.ChatMessage
import com.aioshell.app.core.data.network.ApiClient
import com.aioshell.app.core.data.network.OpenAIChatAdapter
import com.aioshell.app.core.data.network.RequestMessage
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * 对话编排仓库：把历史消息(上下文记忆)与用户配置组装成请求，返回流式增量文本。
 */
@Singleton
class ChatRepository @Inject constructor(private val apiClient: ApiClient) {

    private val adapter = OpenAIChatAdapter(apiClient.okHttpClient)

    private fun ChatMessage.roleToApi(): String? = when (role) {
        com.aioshell.app.core.data.model.MessageRole.USER -> "user"
        com.aioshell.app.core.data.model.MessageRole.ASSISTANT -> "assistant"
        com.aioshell.app.core.data.model.MessageRole.SYSTEM -> "system"
        com.aioshell.app.core.data.model.MessageRole.ERROR -> null
    }

    /**
     * 携带历史消息发起流式对话。增量文本逐块 emit。
     */
    fun streamChat(config: ChatConfig, history: List<ChatMessage>): Flow<String> {
        val payload = history.mapNotNull { m ->
            val role = m.roleToApi() ?: return@mapNotNull null
            if (m.content.isBlank()) null else RequestMessage(role, m.content)
        }
        return adapter.streamChat(config, payload)
    }

    /** 连接测试：校验 Base URL / API Key / 模型名是否可用。 */
    suspend fun testConnection(config: ChatConfig): Boolean = adapter.testConnection(config)
}