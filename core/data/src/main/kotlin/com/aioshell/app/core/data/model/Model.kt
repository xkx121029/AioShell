package com.aioshell.app.core.data.model

import kotlinx.serialization.Serializable

/**
 * 用户配置的一个接口档案。
 * [apiKey] 为该档案在当前会话内存中的解密值，绝不写入日志、不上传第三方。
 */
data class ChatConfig(
    val id: String,
    val name: String,
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val temperature: Float = 0.7f,
    val maxTokens: Int = 2048,
    val topP: Float = 1.0f,
    val isDefault: Boolean = false,
)

/**
 * 持久化到 Disk 的档案形态。[apiKey] 以密文(base64)形式保存。
 */
@Serializable
data class PersistedConfig(
    val id: String,
    val name: String,
    val baseUrl: String,
    val apiKeyCipher: String,
    val model: String,
    val temperature: Float = 0.7f,
    val maxTokens: Int = 2048,
    val topP: Float = 1.0f,
    val isDefault: Boolean = false,
)

/** 对话消息角色。 */
enum class MessageRole { USER, ASSISTANT, SYSTEM, ERROR }

/** 消息落地状态。 */
enum class MessageStatus { SENDING, DONE, ERROR }

/** 领域层对话消息。 */
data class ChatMessage(
    val id: String,
    val sessionId: String,
    val role: MessageRole,
    val content: String,
    val createdAt: Long,
    val status: MessageStatus,
)