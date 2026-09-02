package com.aioshell.app.core.data.repository

import androidx.room.withTransaction
import com.aioshell.app.core.data.database.AppDatabase
import com.aioshell.app.core.data.database.MessageEntity
import com.aioshell.app.core.data.database.SessionEntity
import com.aioshell.app.core.data.model.ChatMessage
import com.aioshell.app.core.data.model.MessageRole
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
        val pinned: Boolean = false,
        val archived: Boolean = false,
        val draft: String? = null,
        val tags: List<String> = emptyList(),
        val modelOverride: String? = null,
        val lastMessagePreview: String = "",
    )

    /** 会话统计汇总。 */
    data class SessionStats(
        val sessions: Long,
        val messages: Long,
        val userMessages: Long,
        val aiMessages: Long,
        val totalChars: Long,
        val estimatedTokens: Long,
    )

    val sessions: Flow<List<SessionSummary>> = dao.observeAll().map { list ->
        list.map { s -> toSummary(s, summarizePreview(messageDao.getInSession(s.id).lastOrNull()?.content.orEmpty())) }
    }

    /** 归档会话列表。 */
    val archivedSessions: Flow<List<SessionSummary>> = dao.observeArchived().map { list ->
        list.map { s -> toSummary(s, summarizePreview(messageDao.getInSession(s.id).lastOrNull()?.content.orEmpty())) }
    }

    suspend fun create(title: String = "新对话", configId: String): String {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        dao.insert(SessionEntity(id, title, configId, now, now, false))
        return id
    }

    /** 摘要统一规则：代码降级为 [代码]，超长截断。 */
    private fun summarizePreview(content: String): String = when {
        content.contains("```") -> "[代码]"
        content.length > 40 -> content.take(40) + "…"
        else -> content
    }

    suspend fun getById(id: String): SessionEntity? = dao.getById(id)

    /** 批量按 id 查询会话（用于批量导出）。 */
    suspend fun getByIds(ids: List<String>): List<SessionEntity> = dao.getByIds(ids)

    suspend fun rename(id: String, title: String) = dao.rename(id, title, System.currentTimeMillis())

    suspend fun touch(id: String) = dao.touch(id, System.currentTimeMillis())

    /** 切换某个会话的置顶状态。置顶会话固定在列表顶部。 */
    suspend fun setPinned(id: String, pinned: Boolean) = dao.setPinned(id, pinned)

    /** 归档 / 恢复会话：归档后从主列表隐藏，可在归档页恢复。 */
    suspend fun setArchived(id: String, archived: Boolean) =
        dao.setArchived(id, archived, System.currentTimeMillis())

    /** 保存 / 清空输入草稿。 */
    suspend fun saveDraft(id: String, draft: String?) = dao.setDraft(id, draft)

    /** 设置当前活动分支的叶子消息 id（null 表示恢复默认线性视图）。 */
    suspend fun setBranchLeaf(id: String, leafId: String?) = dao.setLeaf(id, leafId)

    suspend fun delete(id: String) {
        db.withTransaction {
            dao.deleteById(id)
            messageDao.deleteBySession(id)
        }
    }

    /** 批量删除多个会话及其全部消息与附件（事务保证一致性）。 */
    suspend fun deleteAll(ids: List<String>) {
        if (ids.isEmpty()) return
        db.withTransaction {
            dao.deleteByIds(ids)
            messageDao.deleteBySessions(ids)
        }
    }

    /** 会话摘要附带最后一条消息预览（含归档，用于备份 / 统计）。 */
    suspend fun withPreviewsAll(): List<SessionSummary> {
        return dao.getAllIncludeArchived().map { s ->
            val last = messageDao.getInSession(s.id).lastOrNull()
            toSummary(s, last?.content.orEmpty())
        }
    }

    /** 实体 → 列表摘要（解析逗号分隔标签）。 */
    private fun toSummary(s: SessionEntity, preview: String): SessionSummary =
        SessionSummary(
            id = s.id, title = s.title, configId = s.configId, createdAt = s.createdAt,
            updatedAt = s.updatedAt, pinned = s.pinned, archived = s.archived, draft = s.draft,
            tags = parseTags(s.tags), modelOverride = s.modelOverride, lastMessagePreview = preview,
        )

    /** 解析逗号分隔标签为去空白、去重的集合。 */
    fun parseTags(raw: String?): List<String> =
        raw?.split(',')?.map { it.trim() }?.filter { it.isNotBlank() }?.distinct().orEmpty()

    fun joinTags(tags: List<String>): String? = tags.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        .joinToString(",").takeIf { it.isNotBlank() }

    /** 设置 / 清空会话标签。 */
    suspend fun setTags(id: String, tags: List<String>) = dao.setTags(id, joinTags(tags))

    /** 设置 / 清空会话级模型覆盖（null 表示沿用档案模型）。 */
    suspend fun setModelOverride(id: String, model: String?) = dao.setModelOverride(id, model?.trim()?.takeIf { it.isNotBlank() })

    /**
     * 复制会话：以新 id 创建会话，并以递增时间戳复制其全部消息（含附件）。
     * 返回新会话 id。
     */
    suspend fun duplicate(originId: String, configId: String): String? = db.withTransaction {
        val origin = dao.getById(originId) ?: return@withTransaction null
        val newId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        // 原会话按 id 字符串排序，转换为递增 createdAt 以保持顺序
        val originMsgs = messageDao.getInSession(originId)
        dao.insert(
            SessionEntity(
                id = newId, title = "${origin.title}（副本）", configId = configId,
                createdAt = now, updatedAt = now, tags = origin.tags, modelOverride = origin.modelOverride,
            ),
        )
        val base = now
        originMsgs.forEachIndexed { idx, m ->
            messageDao.insert(m.copy(id = UUID.randomUUID().toString(), sessionId = newId, createdAt = base + idx))
        }
        newId
    }

    /**
     * 合并会话：把源会话 [sourceId] 的全部消息追加到目标会话 [targetId]，
     * 然后删除源会话（含附件），使消息时间连续。返回合并后的会话 id。
     */
    suspend fun merge(targetId: String, sourceId: String): String {
        db.withTransaction {
            val target = dao.getById(targetId) ?: error("目标会话不存在")
            val sourceMsgs = messageDao.getInSession(sourceId)
            // 以目标会话最后一条消息时间为基准，确保新增消息在其后
            val lastTarget = messageDao.getInSession(targetId).lastOrNull()?.createdAt ?: target.updatedAt
            var cursor = lastTarget + 1
            sourceMsgs.forEach { m ->
                messageDao.insertOrReplace(m.copy(id = UUID.randomUUID().toString(), sessionId = targetId, createdAt = cursor))
                cursor++
            }
            dao.deleteById(sourceId)
            messageDao.deleteBySession(sourceId)
            dao.touch(targetId, System.currentTimeMillis())
        }
        return targetId
    }

    /** 会话统计。 */
    suspend fun getStats(): SessionStats {
        val sessions = dao.countAll()
        val messages = messageDao.countAll()
        val userMsgs = messageDao.countByRole("user")
        val aiMsgs = messageDao.countByRole("assistant")
        val totalChars = messageDao.totalChars()
        // 粗略 token 估算：中文为主时约 0.7 字符/token，英语约 4 字符/token，取折中系数 0.75。
        val estimatedTokens = (totalChars * 0.75f).toLong()
        return SessionStats(sessions, messages, userMsgs, aiMsgs, totalChars, estimatedTokens)
    }

    /** 全部会话原始实体（含归档，用于备份导出）。 */
    suspend fun getAllEntitiesForBackup(): List<SessionEntity> = dao.getAllIncludeArchived()

    /** 备份恢复：以事务形式写入一个会话及其全部消息（冲突覆盖，可重复导入）。 */
    suspend fun importSession(
        id: String,
        title: String,
        configId: String,
        createdAt: Long,
        updatedAt: Long,
        pinned: Boolean,
        archived: Boolean,
        draft: String?,
        messages: List<MessageEntity>,
    ) {
        db.withTransaction {
            dao.insertOrReplace(SessionEntity(id, title, configId, createdAt, updatedAt, pinned, archived, draft))
            messages.forEach { messageDao.insertOrReplace(it) }
        }
    }
}

/** 消息仓库：流式对话过程中的消息落地。 */
@Singleton
class MessageRepository @Inject constructor(private val db: AppDatabase) {

    private val dao = db.messageDao()
    private val attachmentDao = db.messageAttachmentDao()

    fun observeInSession(sessionId: String): Flow<List<ChatMessage>> =
        dao.observeInSession(sessionId).map { list -> list.map { it.toDomain() } }

    suspend fun getInSession(sessionId: String): List<ChatMessage> =
        dao.getInSession(sessionId).map { it.toDomain() }

    /** 原始实体（含推理 / 状态等字段），用于备份导出。 */
    suspend fun getRawInSession(sessionId: String): List<MessageEntity> =
        dao.getInSession(sessionId)

    /** 全文搜索：在所有会话中匹配消息正文，返回为领域模型。 */
    suspend fun search(query: String): List<ChatMessage> =
        dao.search(query).map { it.toDomain() }

    suspend fun getById(id: String): ChatMessage? = dao.getById(id)?.toDomain()

    /** 收藏（星标）消息：跨会话集中展示。 */
    fun observeStarred(): Flow<List<ChatMessage>> =
        dao.observeStarred().map { list -> list.map { it.toDomain() } }

    /** 收藏 / 取消收藏消息。 */
    suspend fun toggleStarred(id: String) {
        val cur = dao.getById(id) ?: return
        dao.setStarred(id, !cur.starred)
    }

    /** 编辑消息正文（用户消息编辑 / 修正后用）。 */
    suspend fun updateContent(id: String, content: String) {
        val existing = dao.getById(id) ?: return
        dao.updateContent(id, content)
    }

    suspend fun addUserMessage(
        sessionId: String,
        content: String,
        attachmentPaths: List<String> = emptyList(),
        replyToRole: MessageRole? = null,
        replyToContent: String? = null,
        parentMessageId: String? = null,
    ): ChatMessage {
        val msg = insert(sessionId, "user", content, replyToRole, replyToContent, parentMessageId)
        if (attachmentPaths.isNotEmpty()) {
            attachmentDao.insertAll(
                attachmentPaths.mapIndexed { idx, path ->
                    com.aioshell.app.core.data.database.MessageAttachmentEntity(
                        id = UUID.randomUUID().toString(),
                        messageId = msg.id,
                        localPath = path,
                        mimeType = "image/jpeg",
                        orderIndex = idx,
                    )
                }
            )
        }
        return msg
    }

    suspend fun addAssistantMessage(sessionId: String, initial: String = "", parentMessageId: String? = null): ChatMessage =
        insert(sessionId, "assistant", initial, null, null, parentMessageId)

    suspend fun updateAssistantText(id: String, content: String) {
        val existing = dao.getById(id) ?: return
        dao.update(existing.copy(content = content))
    }

    suspend fun updateAssistantReasoning(id: String, reasoning: String, durationMs: Long) {
        val existing = dao.getById(id) ?: return
        dao.update(existing.copy(reasoning = reasoning, reasoningDurationMs = durationMs))
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
        attachmentDao.deleteByMessage(id)
    }

    private suspend fun insert(
        sessionId: String,
        role: String,
        content: String,
        replyToRole: MessageRole? = null,
        replyToContent: String? = null,
        parentMessageId: String? = null,
    ): ChatMessage {
        val entity = com.aioshell.app.core.data.database.MessageEntity(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            role = role,
            content = content,
            createdAt = System.currentTimeMillis(),
            status = if (role == "user") "DONE" else "SENDING",
            replyToRole = replyToRole?.name,
            replyToContent = replyToContent,
            parentMessageId = parentMessageId,
        )
        dao.insert(entity)
        return entity.toDomain()
    }

    private suspend fun com.aioshell.app.core.data.database.MessageEntity.toDomain(): ChatMessage {
        val r = runCatching { com.aioshell.app.core.data.model.MessageRole.valueOf(role) }
            .getOrDefault(com.aioshell.app.core.data.model.MessageRole.ASSISTANT)
        val s = runCatching { com.aioshell.app.core.data.model.MessageStatus.valueOf(status) }
            .getOrDefault(com.aioshell.app.core.data.model.MessageStatus.DONE)
        val rr = replyToRole?.let {
            runCatching { com.aioshell.app.core.data.model.MessageRole.valueOf(it) }.getOrNull()
        }
        val attachments = attachmentDao.getForMessages(listOf(id))
            .map { com.aioshell.app.core.data.model.MessageAttachment(it.id, it.localPath, it.mimeType, it.orderIndex) }
        return ChatMessage(
            id = id, sessionId = sessionId, role = r, content = content, createdAt = createdAt,
            status = s, reasoningContent = reasoning, reasoningDurationMs = reasoningDurationMs,
            attachments = attachments, starred = starred,
            replyToRole = rr, replyToContent = replyToContent,
            parentMessageId = parentMessageId,
        )
    }
}