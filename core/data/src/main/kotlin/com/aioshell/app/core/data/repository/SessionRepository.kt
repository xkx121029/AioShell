package com.aioshell.app.core.data.repository

import androidx.room.withTransaction
import com.aioshell.app.core.data.database.AppDatabase
import com.aioshell.app.core.data.database.SessionEntity
import com.aioshell.app.core.data.model.ChatMessage
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** 会话仓库：多会话 创建 / 切换 / 重命名 / 删除 / 本地持久化。 */
@Singleton
class SessionRepository @Inject constructor(private val db: AppDatabase) {

    private val dao = db.sessionDao()
    private val messageDao = db.messageDao()

    data class SessionSummary(
        val id: String,
        val title: String,
        val configId: String,
        val createdAt: Long,
        val updatedAt: Long,
        val lastMessagePreview: String = "",
    )

    val sessions: Flow<List<SessionSummary>> = dao.observeAll().map { list ->
        list.map { s ->
            val preview = messageDao.getInSession(s.id).lastOrNull()?.content.orEmpty()
            SessionSummary(s.id, s.title, s.configId, s.createdAt, s.updatedAt, summarizePreview(preview))
        }
    }

    suspend fun create(title: String = "新对话", configId: String): String {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        dao.insert(SessionEntity(id, title, configId, now, now))
        return id
    }

    /** 摘要统一规则：代码降级为 [代码]，超长截断。 */
    private fun summarizePreview(content: String): String = when {
        content.contains("```") -> "[代码]"
        content.length > 40 -> content.take(40) + "…"
        else -> content
    }

    suspend fun getById(id: String): SessionEntity? = dao.getById(id)

    suspend fun rename(id: String, title: String) = dao.rename(id, title, System.currentTimeMillis())

    suspend fun touch(id: String) = dao.touch(id, System.currentTimeMillis())

    suspend fun delete(id: String) {
        db.withTransaction {
            dao.deleteById(id)
            messageDao.deleteBySession(id)
        }
    }

    /** 会话摘要附带最后一条消息预览。 */
    suspend fun withPreviews(): List<SessionSummary> {
        return dao.getAll().map { s ->
            val last = messageDao.getInSession(s.id).lastOrNull()
            SessionSummary(s.id, s.title, s.configId, s.createdAt, s.updatedAt, last?.content.orEmpty())
        }
    }
}

/** 消息仓库：流式对话过程中的消息落地。 */
@Singleton
class MessageRepository @Inject constructor(private val db: AppDatabase) {

    private val dao = db.messageDao()

    fun observeInSession(sessionId: String): Flow<List<ChatMessage>> =
        dao.observeInSession(sessionId).map { list -> list.map { it.toDomain() } }

    suspend fun getInSession(sessionId: String): List<ChatMessage> =
        dao.getInSession(sessionId).map { it.toDomain() }

    suspend fun addUserMessage(sessionId: String, content: String): ChatMessage =
        insert(sessionId, "user", content)

    suspend fun addAssistantMessage(sessionId: String, initial: String = ""): ChatMessage =
        insert(sessionId, "assistant", initial)

    suspend fun updateAssistantText(id: String, content: String) {
        val existing = dao.getById(id) ?: return
        dao.update(existing.copy(content = content))
    }

    suspend fun markAssistantError(id: String, errorText: String) {
        val existing = dao.getById(id) ?: return
        dao.update(existing.copy(status = "ERROR", content = errorText))
    }

    suspend fun markAssistantDone(id: String) {
        val existing = dao.getById(id) ?: return
        dao.update(existing.copy(status = "DONE"))
    }

    suspend fun delete(id: String) {
        dao.deleteById(id)
    }

    private suspend fun insert(sessionId: String, role: String, content: String): ChatMessage {
        val entity = com.aioshell.app.core.data.database.MessageEntity(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            role = role,
            content = content,
            createdAt = System.currentTimeMillis(),
            status = if (role == "user") "DONE" else "SENDING",
        )
        dao.insert(entity)
        return entity.toDomain()
    }

    private fun com.aioshell.app.core.data.database.MessageEntity.toDomain(): ChatMessage {
        val r = runCatching { com.aioshell.app.core.data.model.MessageRole.valueOf(role) }
            .getOrDefault(com.aioshell.app.core.data.model.MessageRole.ASSISTANT)
        val s = runCatching { com.aioshell.app.core.data.model.MessageStatus.valueOf(status) }
            .getOrDefault(com.aioshell.app.core.data.model.MessageStatus.DONE)
        return ChatMessage(id, sessionId, r, content, createdAt, s)
    }
}