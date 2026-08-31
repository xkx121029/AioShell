package com.aioshell.app.core.data.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.aioshell.app.core.data.database.MessageEntity
import com.aioshell.app.core.data.database.SessionEntity
import com.aioshell.app.core.data.model.ChatConfig
import com.aioshell.app.core.data.repository.ConfigRepository
import com.aioshell.app.core.data.repository.MessageRepository
import com.aioshell.app.core.data.repository.SessionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** 备份导入结果汇总。 */
data class BackupResult(
    val configs: Int,
    val sessions: Int,
    val messages: Int,
)

/**
 * 数据备份与恢复：把全部接口配置（API Key 重新加密）与会话、消息、草稿、
 * 置顶/归档状态导出为单个 JSON 备份文件，或从备份文件恢复。
 * 恢复采用「覆盖写入」语义（按 id 冲突即替换），可重复导入，不产生重复数据。
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionRepo: SessionRepository,
    private val messageRepo: MessageRepository,
    private val configRepo: ConfigRepository,
) {

    private companion object {
        const val BACKUP_VERSION = 1
        const val MIME_JSON = "application/json"
    }

    /** 生成备份文件名：AioShell_备份_时间戳.json */
    private fun backupFileName(): String {
        val time = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return "AioShell_备份_$time.json"
    }

    /**
     * 导出全量备份：写入缓存目录并调起系统分享。
     * 返回 true 表示导出成功。
     */
    suspend fun exportBackupAndShare(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val sessions = sessionRepo.getAllEntitiesForBackup()
            val configs = configRepo.getAll()
            val root = JSONObject()
                .put("app", "AioShell")
                .put("type", "backup")
                .put("version", BACKUP_VERSION)
                .put("exportedAt", System.currentTimeMillis())

            val configArr = JSONArray()
            configs.forEach { configArr.put(configToJson(it)) }
            root.put("configs", configArr)

            val sessionArr = JSONArray()
            sessions.forEach { s ->
                val messages = messageRepo.getRawInSession(s.id)
                sessionArr.put(sessionToJson(s, messages))
            }
            root.put("sessions", sessionArr)

            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            val file = File(dir, backupFileName())
            file.writeText(root.toString(2))

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val share = Intent(Intent.ACTION_SEND).apply {
                type = MIME_JSON
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "AioShell 数据备份")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(Intent.createChooser(share, "导出备份")) }
        }.isSuccess
    }

    /** 从备份文件恢复数据，返回导入统计。解析失败抛异常。 */
    suspend fun importBackup(uri: Uri): BackupResult = withContext(Dispatchers.IO) {
        val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: error("无法读取备份文件")
        val root = JSONObject(raw)
        if (root.optString("app") != "AioShell" || root.optString("type") != "backup") {
            error("不是有效的 AioShell 备份文件")
        }

        val configs = mutableListOf<ChatConfig>()
        root.optJSONArray("configs")?.let { arr ->
            for (i in 0 until arr.length()) {
                configs.add(jsonToConfig(arr.getJSONObject(i)))
            }
        }
        configRepo.restoreAll(configs)

        var sessions = 0
        var messages = 0
        root.optJSONArray("sessions")?.let { arr ->
            for (i in 0 until arr.length()) {
                val s = arr.getJSONObject(i)
                val msgArr = s.optJSONArray("messages") ?: JSONArray()
                val msgList = mutableListOf<MessageEntity>()
                for (j in 0 until msgArr.length()) {
                    msgList.add(jsonToMessage(s.optString("id", ""), msgArr.getJSONObject(j)))
                }
                sessionRepo.importSession(
                    id = s.optString("id", ""),
                    title = s.optString("title", "未命名会话"),
                    configId = s.optString("configId", ""),
                    createdAt = s.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = s.optLong("updatedAt", System.currentTimeMillis()),
                    pinned = s.optBoolean("pinned", false),
                    archived = s.optBoolean("archived", false),
                    draft = s.optString("draft", "").takeIf { it.isNotEmpty() },
                    messages = msgList,
                )
                sessions++
                messages += msgList.size
            }
        }
        BackupResult(configs = configs.size, sessions = sessions, messages = messages)
    }

    private fun configToJson(c: ChatConfig): JSONObject = JSONObject()
        .put("id", c.id)
        .put("name", c.name)
        .put("baseUrl", c.baseUrl)
        .put("apiKey", c.apiKey)
        .put("model", c.model)
        .put("temperature", c.temperature)
        .put("maxTokens", c.maxTokens)
        .put("topP", c.topP)
        .put("isDefault", c.isDefault)
        .put("reasoningEnabled", c.reasoningEnabled)

    private fun jsonToConfig(o: JSONObject): ChatConfig = ChatConfig(
        id = o.optString("id"),
        name = o.optString("name", "接口配置"),
        baseUrl = o.optString("baseUrl"),
        apiKey = o.optString("apiKey"),
        model = o.optString("model", "default"),
        temperature = o.optDouble("temperature", 0.7).toFloat(),
        maxTokens = o.optInt("maxTokens", 2048),
        topP = o.optDouble("topP", 1.0).toFloat(),
        isDefault = o.optBoolean("isDefault", false),
        reasoningEnabled = o.optBoolean("reasoningEnabled", true),
    )

    private fun sessionToJson(s: SessionEntity, messages: List<MessageEntity>): JSONObject {
        val msgArr = JSONArray()
        messages.forEach { msgArr.put(messageToJson(it)) }
        return JSONObject()
            .put("id", s.id)
            .put("title", s.title)
            .put("configId", s.configId)
            .put("createdAt", s.createdAt)
            .put("updatedAt", s.updatedAt)
            .put("pinned", s.pinned)
            .put("archived", s.archived)
            .put("draft", s.draft ?: "")
            .put("messages", msgArr)
    }

    private fun messageToJson(m: MessageEntity): JSONObject = JSONObject()
        .put("id", m.id)
        .put("role", m.role)
        .put("content", m.content)
        .put("createdAt", m.createdAt)
        .put("status", m.status)
        .put("reasoning", m.reasoning ?: "")
        .put("reasoningDurationMs", m.reasoningDurationMs ?: 0L)

    private fun jsonToMessage(sessionId: String, o: JSONObject): MessageEntity = MessageEntity(
        id = o.optString("id").takeIf { it.isNotBlank() } ?: java.util.UUID.randomUUID().toString(),
        sessionId = sessionId,
        role = o.optString("role", "assistant"),
        content = o.optString("content", ""),
        createdAt = o.optLong("createdAt", System.currentTimeMillis()),
        status = o.optString("status", "DONE"),
        reasoning = o.optString("reasoning", "").takeIf { it.isNotEmpty() },
        reasoningDurationMs = o.optLong("reasoningDurationMs", 0L).takeIf { it > 0 },
    )
}
