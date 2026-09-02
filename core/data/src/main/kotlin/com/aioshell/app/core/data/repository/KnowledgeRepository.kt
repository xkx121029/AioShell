package com.aioshell.app.core.data.repository

import com.aioshell.app.core.data.database.AppDatabase
import com.aioshell.app.core.data.database.KnowledgeChunkEntity
import com.aioshell.app.core.data.database.KnowledgeDocumentEntity
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * 本地知识库（RAG）仓库：导入文本资料 → 切分索引 → 按相关性召回。
 *
 * 检索采用轻量"字符大 n-gram + 关键词"打分，无需引入嵌入模型，完全离线。
 */
@Singleton
class KnowledgeRepository @Inject constructor(private val db: AppDatabase) {

    private val dao = db.knowledgeDao()

    /** 全部文档，按导入时间倒序。 */
    fun observeDocuments(): Flow<List<KnowledgeDocumentEntity>> = dao.observeDocuments()

    suspend fun documents(): List<KnowledgeDocumentEntity> = dao.getAllDocuments()

    /**
     * 导入一份文本。按约 [CHUNK_SIZE] 字符（含 [CHUNK_OVERLAP] 重叠）切块后入库。
     * @return 该文档的领域描述
     */
    suspend fun importText(title: String, sourceName: String, text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return
        val chunks = chunk(trimmed)
        val docId = UUID.randomUUID().toString()
        dao.insertDocument(
            KnowledgeDocumentEntity(
                id = docId, title = title.trim().ifBlank { "未命名文档" },
                sourceName = sourceName, sizeChars = trimmed.length,
                chunkCount = chunks.size, createdAt = System.currentTimeMillis(),
            )
        )
        dao.insertChunks(
            chunks.mapIndexed { i, chunk ->
                KnowledgeChunkEntity(id = UUID.randomUUID().toString(), docId = docId, text = chunk, orderIndex = i)
            }
        )
    }

    suspend fun deleteDocument(docId: String) {
        dao.deleteChunksByDoc(docId)
        dao.deleteDocumentById(docId)
    }

    suspend fun deleteAll() {
        dao.getAllDocuments().forEach { deleteDocument(it.id) }
    }

    suspend fun countDocuments(): Long = dao.countDocuments()

    /**
     * 召回与 [query] 最相关的最多 [maxChunks] 块，并组织成可注入 system 的上下文文本。
     * 无结果返回空字符串。
     */
    suspend fun retrieveContext(query: String, maxChunks: Int = 4): String {
        val q = query.trim()
        if (q.isEmpty()) return ""
        val all = dao.getAllChunks()
        if (all.isEmpty()) return ""
        val scored = all
            .map { chunk -> chunk to score(query, chunk.text) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
        val best = scored.filter { it.second >= MIN_SCORE }.take(maxChunks)
        if (best.isEmpty()) return ""
        // 去重（相同 docId+前 40 字视为重复）
        val seen = HashSet<String>()
        val picks = mutableListOf<KnowledgeChunkEntity>()
        for ((chunk, _) in best) {
            val key = chunk.docId + "|" + chunk.text.take(40)
            if (seen.add(key)) picks.add(chunk)
        }
        return buildString {
            appendLine("以下为本地知识库中与用户问题相关的资料，请优先依据其中内容作答，并说明信息来源：")
            val byDoc = picks.groupBy { it.docId }
            byDoc.forEach { (docId, chunks) ->
                val docs = dao.getAllDocuments().associateBy { it.id }
                val title = docs[docId]?.title ?: "未知资料"
                appendLine("")
                appendLine("📁 $title：")
                chunks.sortedBy { it.orderIndex }.forEach { c ->
                    appendLine(c.text.trim())
                }
            }
        }
    }

    private fun chunk(text: String): List<String> {
        val out = ArrayList<String>()
        var start = 0
        while (start < text.length) {
            var end = (start + CHUNK_SIZE).coerceAtMost(text.length)
            // 尽量在换行处断开，避免切断句子
            if (end < text.length) {
                val nl = text.lastIndexOf('\n', end)
                if (nl > start + CHUNK_SIZE / 2) end = nl
            }
            out.add(text.substring(start, end).trim())
            start = (end - CHUNK_OVERLAP).coerceAtLeast(start + 1)
        }
        return out.filter { it.isNotBlank() }
    }

    /**
     * 轻量相关性打分：字符双元组（如中文字对、英文相邻字母）与关键词的共现度。
     */
    private fun score(query: String, text: String): Int {
        if (query.isBlank() || text.isBlank()) return 0
        val qBigrams = bigrams(query)
        val tLower = text.lowercase()
        // 关键词直命中：每个命中计基础分
        var hits = 0
        val keywords = query.split(Regex("[\\s,，。;；。!！?？:：/\\\\|]+"))
            .filter { it.length in 1..12 && it.lowercase() !in STOP_WORDS }
            .distinct()
        keywords.forEach { kw ->
            val kl = kw.lowercase()
            if (tLower.contains(kl)) hits += if (kw.length >= 2) 3 else 1
        }
        // 双元组共现
        var bigHit = 0
        val tBigrams = bigrams(text.lowercase())
        qBigrams.forEach { if (it in tBigrams) bigHit += 1 }
        return hits + bigHit
    }

    private fun bigrams(s: String): Set<String> {
        val clean = s.replace(Regex("[\\s\\p{Punct}]+"), "").lowercase()
        if (clean.length < 2) return emptySet()
        return HashSet<String>(clean.length).also { set ->
            for (i in 0 until clean.length - 1) set.add(clean.substring(i, i + 2))
        }
    }

    private companion object {
        const val CHUNK_SIZE = 500
        const val CHUNK_OVERLAP = 60
        const val MIN_SCORE = 2
        val STOP_WORDS = setOf("的", "了", "吗", "呢", "啊", "吧", "是", "在", "个", "有", "和", "与", "或", "the", "a", "an", "and", "of", "to", "in")
    }
}