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