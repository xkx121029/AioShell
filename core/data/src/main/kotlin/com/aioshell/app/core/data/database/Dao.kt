package com.aioshell.app.core.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Query("SELECT * FROM session ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM session ORDER BY updatedAt DESC")
    suspend fun getAll(): List<SessionEntity>

    @Query("SELECT * FROM session WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): SessionEntity?

    @Insert
    suspend fun insert(entity: SessionEntity)

    @Update
    suspend fun update(entity: SessionEntity)

    @Query("UPDATE session SET title = :title, updatedAt = :updatedAt WHERE id = :id")
    suspend fun rename(id: String, title: String, updatedAt: Long)

    @Query("UPDATE session SET updatedAt = :updatedAt WHERE id = :id")
    suspend fun touch(id: String, updatedAt: Long)

    @Delete
    suspend fun delete(entity: SessionEntity)

    @Query("DELETE FROM session WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM message WHERE sessionId = :sessionId ORDER BY createdAt ASC")
    fun observeInSession(sessionId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM message WHERE sessionId = :sessionId ORDER BY createdAt ASC")
    suspend fun getInSession(sessionId: String): List<MessageEntity>

    @Query("SELECT * FROM message WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): MessageEntity?

    @Insert
    suspend fun insert(entity: MessageEntity)

    @Update
    suspend fun update(entity: MessageEntity)

    @Query("DELETE FROM message WHERE sessionId = :sessionId")
    suspend fun deleteBySession(sessionId: String)
}