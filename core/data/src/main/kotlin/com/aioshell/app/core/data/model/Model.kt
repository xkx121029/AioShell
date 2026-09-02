package com.aioshell.app.core.data.model

import kotlinx.serialization.Serializable

/** 消息附件（本地图片）。 */
data class MessageAttachment(
    val id: String,
    val localPath: String,
    val mimeType: String,
    val orderIndex: Int,
)

/**
 * 用户配置的一个接口档案。
 * [apiKey] 为该档案在当前会话内存中的解密值，绝不写入日志、不上传第三方。
 * [reasoningEnabled]：本模型是否展示思考过程（模型级开关，默认开启）。
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
    val reasoningEnabled: Boolean = true,
)

/**
 * 持久化到 Disk 的档案形态。[apiKey] 以密文(base64)形式保存。
 * [reasoningEnabled] 新增字段带默认值，保证老数据兼容读取。
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
    val reasoningEnabled: Boolean = true,
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
    val reasoningContent: String? = null,
    val reasoningDurationMs: Long? = null,
    val attachments: List<MessageAttachment> = emptyList(),
    /** 是否收藏（星标）。 */
    val starred: Boolean = false,
    /** 被引用消息的发起方角色（无引用则为 null）。 */
    val replyToRole: MessageRole? = null,
    /** 被引用消息正文快照（无引用则为 null）。 */
    val replyToContent: String? = null,
    /** 父消息 id（分支指针）；null 表示分支根 / 默认线性链。 */
    val parentMessageId: String? = null,
)