package com.aioshell.app.core.data.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 会话实体：本地持久化，不对外上传。 */
@Entity(tableName = "session")
data class SessionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val configId: String,
    val createdAt: Long,
    val updatedAt: Long,
    /** 是否置顶：置顶会话固定在列表顶部。 */
    val pinned: Boolean = false,
    /** 是否已归档：归档会话默认不显示在主列表，可随时恢复。 */
    val archived: Boolean = false,
    /** 输入草稿：进入会话自动填充，发送/清空后置空。 */
    val draft: String? = null,
    /** 自定义标签（逗号分隔，用于列表筛选）。 */
    val tags: String? = null,
    /** 会话级模型覆盖：非空时该会话使用指定模型名（结合当前档案），否则沿用档案模型。 */
    val modelOverride: String? = null,
)

/** 消息实体。 */
@Entity(
    tableName = "message",
    indices = [Index("sessionId")],
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val role: String,
    val content: String,
    val createdAt: Long,
    val status: String,
    val reasoning: String? = null,
    val reasoningDurationMs: Long? = null,
    /** 是否收藏（星标）：长按收藏重要消息，独立收藏页查看。 */
    val starred: Boolean = false,
)

/** 消息附件实体（本地图片引用）。 */
@Entity(
    tableName = "message_attachments",
    indices = [Index("messageId")],
)
data class MessageAttachmentEntity(
    @PrimaryKey val id: String,
    val messageId: String,
    val localPath: String,
    val mimeType: String,
    val orderIndex: Int,
)

/** 提示词模板实体。内置模板 [builtIn] 不可删除，可自定义新增/编辑。 */
@Entity(tableName = "prompt_template")
data class PromptTemplateEntity(
    @PrimaryKey val id: String,
    val title: String,
    val content: String,
    val category: String,
    val builtIn: Boolean = false,
    val orderIndex: Int = 0,
)