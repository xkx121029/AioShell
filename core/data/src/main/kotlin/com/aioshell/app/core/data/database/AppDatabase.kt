package com.aioshell.app.core.data.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        SessionEntity::class,
        MessageEntity::class,
        MessageAttachmentEntity::class,
        PromptTemplateEntity::class,
        KnowledgeDocumentEntity::class,
        KnowledgeChunkEntity::class,
    ],
    version = 9,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun messageDao(): MessageDao
    abstract fun messageAttachmentDao(): MessageAttachmentDao
    abstract fun promptTemplateDao(): PromptTemplateDao
    abstract fun knowledgeDao(): KnowledgeDao

    companion object {
        /** V1 → V2：message 表新增思考字段。 */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE message ADD COLUMN reasoning TEXT")
                db.execSQL("ALTER TABLE message ADD COLUMN reasoningDurationMs INTEGER")
            }
        }

        /** V2 → V3：新增消息附件表。 */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS message_attachments (
                        id TEXT PRIMARY KEY NOT NULL,
                        messageId TEXT NOT NULL,
                        localPath TEXT NOT NULL,
                        mimeType TEXT NOT NULL,
                        orderIndex INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_message_attachments_messageId ON message_attachments (messageId)")
            }
        }

        /** V3 → V4：新增提示词模板表。 */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS prompt_template (
                        id TEXT PRIMARY KEY NOT NULL,
                        title TEXT NOT NULL,
                        content TEXT NOT NULL,
                        category TEXT NOT NULL,
                        builtIn INTEGER NOT NULL DEFAULT 0,
                        orderIndex INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
            }
        }

        /** V4 → V5：session 表新增置顶字段。 */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE session ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** V5 → V6：session 表新增归档与输入草稿字段。 */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE session ADD COLUMN archived INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE session ADD COLUMN draft TEXT")
            }
        }

        /** V6 → V7：session 表新增标签与会话级模型覆盖；message 表新增收藏标记。 */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE session ADD COLUMN tags TEXT")
                db.execSQL("ALTER TABLE session ADD COLUMN modelOverride TEXT")
                db.execSQL("ALTER TABLE message ADD COLUMN starred INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** V7 → V8：message 表新增回复引用字段。 */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE message ADD COLUMN replyToRole TEXT")
                db.execSQL("ALTER TABLE message ADD COLUMN replyToContent TEXT")
            }
        }

        /** V8 → V9：分支字段 + 本地知识库（RAG）表。 */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE message ADD COLUMN parentMessageId TEXT")
                db.execSQL("ALTER TABLE session ADD COLUMN leafId TEXT")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS knowledge_document (
                        id TEXT PRIMARY KEY NOT NULL,
                        title TEXT NOT NULL,
                        sourceName TEXT NOT NULL,
                        sizeChars INTEGER NOT NULL,
                        chunkCount INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS knowledge_chunk (
                        id TEXT PRIMARY KEY NOT NULL,
                        docId TEXT NOT NULL,
                        text TEXT NOT NULL,
                        orderIndex INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_knowledge_chunk_docId ON knowledge_chunk (docId)")
            }
        }
    }
}