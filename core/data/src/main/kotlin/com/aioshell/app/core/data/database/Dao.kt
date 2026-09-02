package com.aioshell.app.core.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    /** 主列表：置顶优先，其余按最近更新倒序；排除归档会话。 */
    @Query("SELECT * FROM session WHERE archived = 0 ORDER BY pinned DESC, updatedAt DESC")
    fun observeAll(): Flow<List<SessionEntity>>

    /** 归档列表：仅归档会话，按最近归档时间倒序。 */
    @Query("SELECT * FROM session WHERE archived = 1 ORDER BY updatedAt DESC")
    fun observeArchived(): Flow<List<SessionEntity>>

    /** 全部会话（含归档，用于统计 / 备份 / 搜索）。 */
    @Query("SELECT * FROM session ORDER BY updatedAt DESC")
    suspend fun getAllIncludeArchived(): List<SessionEntity>

    @Query("SELECT * FROM session WHERE archived = 0 ORDER BY pinned DESC, updatedAt DESC")
    suspend fun getAll(): List<SessionEntity>

    @Query("SELECT COUNT(*) FROM session")
    suspend fun countAll(): Long

    @Query("SELECT * FROM session WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): SessionEntity?

    @Query("SELECT * FROM session WHERE id IN (:ids) ORDER BY pinned DESC, updatedAt DESC")
    suspend fun getByIds(ids: List<String>): List<SessionEntity>

    @Insert
    suspend fun insert(entity: SessionEntity)

    /** 备份恢复用：冲突即覆盖，保证重复导入幂等。 */
    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(entity: SessionEntity)

    @Update
    suspend fun update(entity: SessionEntity)

    @Query("UPDATE session SET title = :title, updatedAt = :updatedAt WHERE id = :id")
    suspend fun rename(id: String, title: String, updatedAt: Long)

    @Query("UPDATE session SET updatedAt = :updatedAt WHERE id = :id")
    suspend fun touch(id: String, updatedAt: Long)

    @Query("UPDATE session SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: String, pinned: Boolean)

    @Query("UPDATE session SET archived = :archived, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setArchived(id: String, archived: Boolean, updatedAt: Long)

    @Query("UPDATE session SET draft = :draft WHERE id = :id")
    suspend fun setDraft(id: String, draft: String?)

    @Query("UPDATE session SET tags = :tags WHERE id = :id")
    suspend fun setTags(id: String, tags: String?)

    @Query("UPDATE session SET modelOverride = :modelOverride WHERE id = :id")
    suspend fun setModelOverride(id: String, modelOverride: String?)

    @Query("UPDATE session SET leafId = :leafId WHERE id = :id")
    suspend fun setLeaf(id: String, leafId: String?)

    @Delete
    suspend fun delete(entity: SessionEntity)

    @Query("DELETE FROM session WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM session WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM message WHERE sessionId = :sessionId ORDER BY createdAt ASC")
    fun observeInSession(sessionId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM message WHERE sessionId = :sessionId ORDER BY createdAt ASC")
    suspend fun getInSession(sessionId: String): List<MessageEntity>

    @Query("SELECT * FROM message WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): MessageEntity?

    /** 收藏（星标）消息：跨会话集中展示，按收藏时间倒序。 */
    @Query("SELECT * FROM message WHERE starred = 1 ORDER BY createdAt DESC")
    fun observeStarred(): Flow<List<MessageEntity>>

    @Query("UPDATE message SET starred = :starred WHERE id = :id")
    suspend fun setStarred(id: String, starred: Boolean)

    @Query("UPDATE message SET content = :content WHERE id = :id")
    suspend fun updateContent(id: String, content: String)

    /**
     * 全文搜索：按关键词（忽略大小写）在所有会话中匹配消息正文，
     * 排除错误消息，按时间倒序返回。命中关键词会被高亮显示。
     */
    @Query(
        """
        SELECT * FROM message
        WHERE content LIKE '%' || :query || '%'
          AND role != 'ERROR'
        ORDER BY createdAt DESC
        """
    )
    suspend fun search(query: String): List<MessageEntity>

    @Insert
    suspend fun insert(entity: MessageEntity)

    /** 备份恢复用：冲突即覆盖。 */
    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(entity: MessageEntity)

    @Update
    suspend fun update(entity: MessageEntity)

    @Query("DELETE FROM message WHERE sessionId = :sessionId")
    suspend fun deleteBySession(sessionId: String)

    @Query("DELETE FROM message WHERE sessionId IN (:sessionIds)")
    suspend fun deleteBySessions(sessionIds: List<String>)

    @Query("DELETE FROM message WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT COUNT(*) FROM message")
    suspend fun countAll(): Long

    @Query("SELECT COALESCE(SUM(LENGTH(content)), 0) FROM message")
    suspend fun totalChars(): Long

    @Query("SELECT COUNT(*) FROM message WHERE role = :role")
    suspend fun countByRole(role: String): Long
}

/** 本地知识库（RAG）DAO：文档与其分块的新增 / 查询 / 删除。 */
@Dao
interface KnowledgeDao {
    @Query("SELECT * FROM knowledge_document ORDER BY createdAt DESC")
    fun observeDocuments(): Flow<List<KnowledgeDocumentEntity>>

    @Query("SELECT * FROM knowledge_document ORDER BY createdAt DESC")
    suspend fun getAllDocuments(): List<KnowledgeDocumentEntity>

    @Query("SELECT COUNT(*) FROM knowledge_document")
    suspend fun countDocuments(): Long

    @Insert
    suspend fun insertDocument(doc: KnowledgeDocumentEntity)

    @Query("DELETE FROM knowledge_document WHERE id = :id")
    suspend fun deleteDocumentById(id: String)

    @Query("SELECT * FROM knowledge_chunk WHERE docId = :docId ORDER BY orderIndex ASC")
    suspend fun getChunksForDoc(docId: String): List<KnowledgeChunkEntity>

    @Query("SELECT * FROM knowledge_chunk ORDER BY docId ASC, orderIndex ASC")
    suspend fun getAllChunks(): List<KnowledgeChunkEntity>

    @Insert
    suspend fun insertChunks(chunks: List<KnowledgeChunkEntity>)

    @Query("DELETE FROM knowledge_chunk WHERE docId = :docId")
    suspend fun deleteChunksByDoc(docId: String)

    @Query("SELECT COUNT(*) FROM knowledge_chunk")
    suspend fun countChunks(): Long
}

@Dao
interface MessageAttachmentDao {
    @Query("SELECT * FROM message_attachments WHERE messageId = :messageId ORDER BY orderIndex ASC")
    fun observeForMessage(messageId: String): Flow<List<MessageAttachmentEntity>>

    @Query("SELECT * FROM message_attachments WHERE messageId IN (:messageIds) ORDER BY orderIndex ASC")
    suspend fun getForMessages(messageIds: List<String>): List<MessageAttachmentEntity>

    @Insert
    suspend fun insertAll(entities: List<MessageAttachmentEntity>)

    @Query("DELETE FROM message_attachments WHERE messageId = :messageId")
    suspend fun deleteByMessage(messageId: String)

    @Query("DELETE FROM message_attachments WHERE messageId IN (SELECT id FROM message WHERE sessionId = :sessionId)")
    suspend fun deleteBySession(sessionId: String)

    @Query("DELETE FROM message_attachments WHERE messageId IN (SELECT id FROM message WHERE sessionId IN (:sessionIds))")
    suspend fun deleteBySessions(sessionIds: List<String>)
}

/** 提示词模板 DAO。 */
@Dao
interface PromptTemplateDao {
    @Query("SELECT * FROM prompt_template ORDER BY orderIndex ASC, title ASC")
    fun observeAll(): Flow<List<PromptTemplateEntity>>

    @Query("SELECT * FROM prompt_template ORDER BY orderIndex ASC, title ASC")
    suspend fun getAll(): List<PromptTemplateEntity>

    @Query("SELECT * FROM prompt_template WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): PromptTemplateEntity?

    @Insert
    suspend fun insert(entity: PromptTemplateEntity)

    @Update
    suspend fun update(entity: PromptTemplateEntity)

    @Query("DELETE FROM prompt_template WHERE id = :id")
    suspend fun deleteById(id: String)
}