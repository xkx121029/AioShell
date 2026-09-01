package com.aioshell.app.core.data.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.aioshell.app.core.data.model.ChatMessage
import com.aioshell.app.core.data.model.MessageRole
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

/** 导出格式。 */
enum class ExportFormat(val label: String, val extension: String) {
    MARKDOWN("Markdown", "md"),
    TEXT("纯文本", "txt"),
    JSON("JSON", "json"),
}

/**
 * 会话导出与分享：把单个会话导出为 Markdown / 纯文本 / JSON，
 * 落盘到应用缓存目录后通过系统分享面板分享（FileProvider，免存储权限）。
 */
@Singleton
class ConversationExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionRepo: SessionRepository,
    private val messageRepo: MessageRepository,
) {

    /** 生成导出文件名：AioShell_会话标题_时间戳.扩展名 */
    private fun fileName(title: String, format: ExportFormat): String {
        val safe = title.replace(Regex("[\\\\/:*?\"<>|\\s]+"), "_").take(24)
        val time = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return "AioShell_${safe.ifBlank { "对话" }}_$time.${format.extension}"
    }

    /** 导出并调起系统分享。返回 false 表示无内容可导出。 */
    suspend fun exportAndShare(sessionId: String, format: ExportFormat): Boolean = withContext(Dispatchers.IO) {
        val session = sessionRepo.getById(sessionId) ?: return@withContext false
        val all = messageRepo.getInSession(sessionId)
        val messages = all.filter { it.content.isNotBlank() }
        if (messages.isEmpty()) return@withContext false

        val body = when (format) {
            ExportFormat.MARKDOWN -> toMarkdown(session.title, messages)
            ExportFormat.TEXT -> toPlainText(session.title, messages)
            ExportFormat.JSON -> toJson(session.title, messages)
        }
        shareBody(body, session.title, format)
        true
    }

    /**
     * 批量导出多个会话为单个文件并分享。
     * 若某个会话无内容则跳过；全部无内容返回 false。
     */
    suspend fun exportAndShareMultiple(sessionIds: List<String>, format: ExportFormat): Boolean =
        withContext(Dispatchers.IO) {
            // 预取会话与消息（非空），避免在非挂起 lambda 中调用挂起函数
            val sessions = sessionRepo.getByIds(sessionIds).mapNotNull { s ->
                val msgs = messageRepo.getInSession(s.id).filter { it.content.isNotBlank() }
                if (msgs.isEmpty()) null else s to msgs
            }
            if (sessions.isEmpty()) return@withContext false

            val body = when (format) {
                ExportFormat.MARKDOWN -> sessions.joinToString("\n") { (s, msgs) ->
                    "---\n\n" + toMarkdown(s.title, msgs)
                }
                ExportFormat.TEXT -> sessions.joinToString("\n\n") { (s, msgs) ->
                    toPlainText(s.title, msgs)
                }
                ExportFormat.JSON -> toJsonMultiple(
                    sessions.map { (s, msgs) -> s.title to msgs },
                )
            }
            val title = if (sessions.size == 1) sessions.first().first.title else "${sessions.size}个会话"
            shareBody(body, title, format)
            true
        }

    /** 把内容落盘并调起系统分享面板。 */
    private fun shareBody(body: String, title: String, format: ExportFormat) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, fileName(title, format))
        file.writeText(body)

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val share = Intent(Intent.ACTION_SEND).apply {
            type = when (format) {
                ExportFormat.MARKDOWN -> "text/markdown"
                ExportFormat.TEXT -> "text/plain"
                ExportFormat.JSON -> "application/json"
            }
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(Intent.EXTRA_SUBJECT, "「$title」对话导出")
        }
        runCatching { context.startActivity(Intent.createChooser(share, "导出「$title」")) }
    }

    /**
     * 导出会话为长图（PNG）并分享。
     * 使用 Canvas + TextPaint 按消息逐块排版渲染，落盘后经 FileProvider 分享。
     */
    suspend fun exportAndShareLongImage(sessionId: String): Boolean = withContext(Dispatchers.IO) {
        val session = sessionRepo.getById(sessionId) ?: return@withContext false
        val entries = visualEntries(sessionId) ?: return@withContext false
        val bitmap = renderLongImage(session.title, entries) ?: return@withContext false
        shareBitmap(bitmap, session.title)
        true
    }

    /** 导出会话为 PDF 并分享。 */
    suspend fun exportAndSharePdf(sessionId: String): Boolean = withContext(Dispatchers.IO) {
        val session = sessionRepo.getById(sessionId) ?: return@withContext false
        val entries = visualEntries(sessionId) ?: return@withContext false
        val file = renderPdf(session.title, entries) ?: return@withContext false
        shareFile(file, "$session.title 对话导出", "application/pdf", "PDF")
        true
    }

    /** 汇总可视化条目（角色 / 时间 / 正文）。 */
    private suspend fun visualEntries(sessionId: String): List<VisualEntry>? {
        val messages = messageRepo.getInSession(sessionId).filter { it.content.isNotBlank() }
        if (messages.isEmpty()) return null
        return messages.map { VisualEntry(roleLabel(it.role), formatTime(it.createdAt), it.content) }
    }

    /** 把图片 Bitmap 落盘为 PNG 并分享。 */
    private fun shareBitmap(bitmap: android.graphics.Bitmap, title: String) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val name = "AioShell_${title.safeFile()}_${stamp()}.png"
        val file = File(dir, name)
        file.createNewFile()
        java.io.FileOutputStream(file).use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        shareFile(file, title, "image/png", "长图")
    }

    /** 通用分享：把文件经 FileProvider 调起系统分享面板。 */
    private fun shareFile(file: File, title: String, mimeType: String, desc: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val share = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(Intent.EXTRA_SUBJECT, "「$title」对话导出")
        }
        runCatching { context.startActivity(Intent.createChooser(share, "导出「$title」（$desc）")) }
    }

    /** 渲染会话为单张长图 Bitmap。 */
    private fun renderLongImage(title: String, entries: List<VisualEntry>): android.graphics.Bitmap? {
        val widthPx = 760
        val pad = 44
        val maxTextWidth = widthPx - pad * 2
        val titlePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 42f; color = 0xFF0B6B60.toInt(); isFakeBoldText = true
        }
        val rolePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 26f; color = 0xFF0B6B60.toInt(); isFakeBoldText = true
        }
        val timePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 20f; color = 0xFF8A8A8E.toInt()
        }
        val bodyPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 30f; color = 0xFF1C1C1E.toInt()
        }
        val lineSpace = 34
        val sectionSpace = 26

        // 预排版：角色标题 / 时间戳 / 正文块
        data class Block(val paint: android.graphics.Paint, val lines: List<String>)
        val blocks = buildList {
            add(Block(titlePaint, wrap(titlePaint, title, maxTextWidth)))
            add(Block(timePaint, wrap(timePaint, "由 AioShell 导出 · ${formatTime(System.currentTimeMillis())}", maxTextWidth)))
            entries.forEach { e ->
                add(Block(rolePaint, wrap(rolePaint, "${e.role} · ${e.time}", maxTextWidth)))
                add(Block(bodyPaint, wrap(bodyPaint, e.content, maxTextWidth)))
            }
        }
        val totalLines = blocks.sumOf { it.lines.size }
        val heightPx = (pad * 2 + totalLines * lineSpace + blocks.size * sectionSpace).coerceIn(400, 60_000)

        val bitmap = android.graphics.Bitmap.createBitmap(widthPx, heightPx, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(0xFFF7F8FA.toInt())
        var y = pad + bodyPaint.textSize
        blocks.forEach { block ->
            block.lines.forEach { line ->
                canvas.drawText(line, pad.toFloat(), y.toFloat(), block.paint)
                y += lineSpace
            }
            y += sectionSpace
        }
        return bitmap
    }

    /** 渲染会话为 PDF 文件。 */
    private fun renderPdf(title: String, entries: List<VisualEntry>): File? {
        val pdf = android.graphics.pdf.PdfDocument()
        val width = 595; val height = 842; val margin = 48
        val maxTextWidth = width - margin * 2
        val rolePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 30f; color = 0xFF0B6B60.toInt(); isFakeBoldText = true
        }
        val bodyPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 28f; color = 0xFF1C1C1E.toInt()
        }
        val titlePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 40f; color = 0xFF0B6B60.toInt(); isFakeBoldText = true
        }

        val lineSpace = 40
        var page = pdf.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(width, height, 1).create())
        var canvas = page.canvas
        canvas.drawColor(0xFFF7F8FA.toInt())
        var y = 80

        // 需要换页时才滚动到新页
        fun ensureSpace() {
            if (y > height - margin) {
                pdf.finishPage(page)
                page = pdf.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(width, height, 1).create())
                canvas = page.canvas
                canvas.drawColor(0xFFF7F8FA.toInt())
                y = 80
            }
        }

        fun drawBlock(paint: android.graphics.Paint, text: String) {
            wrap(paint, text, maxTextWidth).forEach { line ->
                ensureSpace()
                canvas.drawText(line, margin.toFloat(), y.toFloat(), paint)
                y += lineSpace
            }
            y += lineSpace
        }

        drawBlock(titlePaint, title)
        drawBlock(rolePaint, "由 AioShell 导出 · ${formatTime(System.currentTimeMillis())}")
        entries.forEach { e ->
            drawBlock(rolePaint, "${e.role} · ${e.time}")
            drawBlock(bodyPaint, e.content)
        }

        // 结束当前页并写出
        pdf.finishPage(page)
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "AioShell_${title.safeFile()}_${stamp()}.pdf")
        java.io.FileOutputStream(file).use { pdf.writeTo(it) }
        pdf.close()
        return file
    }

    /** 可排版的换行：把多行文本（含换行）按宽度折断为多行。 */
    private fun wrap(paint: android.graphics.Paint, text: String, maxWidth: Int): List<String> =
        text.split("\n").flatMap { line -> linesOf(paint, line, maxWidth) }.ifEmpty { listOf(" ") }

    /** 把一行文本按宽度折断为多行。 */
    private fun linesOf(
        paint: android.graphics.Paint,
        text: String,
        maxWidth: Int,
    ): List<String> {
        if (text.isEmpty()) return listOf(" ")
        if (paint.measureText(text) <= maxWidth) return listOf(text)
        val result = mutableListOf<String>()
        val arr = text.toCharArray()
        val sb = StringBuilder()
        var i = 0
        while (i < arr.size) {
            sb.append(arr[i])
            if (paint.measureText(sb.toString()) > maxWidth) {
                if (sb.length == 1) { result.add(sb.toString()); sb.clear() }
                else {
                    sb.deleteCharAt(sb.length - 1)
                    result.add(sb.toString())
                    sb.clear()
                    // 当前字符留到下一轮
                    i-- // will re-append arr[i]
                }
            }
            i++
        }
        if (sb.isNotEmpty()) result.add(sb.toString())
        return result
    }

    /** 根据角色 / 时间组装可视化条目。 */
    private data class VisualEntry(val role: String, val time: String, val content: String)

    private fun String.safeFile(): String = this.replace(Regex("[\\\\/:*?\"<>|\\s]+"), "_").take(24).ifBlank { "对话" }

    private fun stamp(): String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())

    private fun roleLabel(role: MessageRole): String = when (role) {
        MessageRole.USER -> "我"
        MessageRole.ASSISTANT -> "AI"
        else -> role.name
    }

    private fun formatTime(millis: Long): String =
        runCatching { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(millis)) }
            .getOrDefault("")

    private fun toMarkdown(title: String, messages: List<ChatMessage>): String =
        buildString {
            appendLine("# $title")
            appendLine()
            appendLine("> 由 AioShell 导出 · ${formatTime(System.currentTimeMillis())}")
            appendLine()
            messages.forEach { m ->
                append("**${roleLabel(m.role)}** · ${formatTime(m.createdAt)}")
                m.reasoningContent?.takeIf { it.isNotBlank() }?.let {
                    appendLine()
                    appendLine("> 💭 思考过程")
                    appendLine(">")
                    appendLine("> ${it.replace("\n", "\n> ")}")
                }
                appendLine()
                appendLine(m.content)
                appendLine()
                appendLine("---")
                appendLine()
            }
        }

    private fun toPlainText(title: String, messages: List<ChatMessage>): String =
        buildString {
            appendLine("【$title】")
            appendLine("由 AioShell 导出 · ${formatTime(System.currentTimeMillis())}")
            appendLine()
            messages.forEach { m ->
                appendLine("${roleLabel(m.role)}（${formatTime(m.createdAt)}）：")
                m.reasoningContent?.takeIf { it.isNotBlank() }?.let {
                    appendLine("　[思考过程] ${it.replace("\n", "\n　")}")
                    appendLine(it)
                }
                appendLine(m.content)
                appendLine()
            }
        }

    private fun toJson(title: String, messages: List<ChatMessage>): String =
        buildString {
            append("{\n")
            append("  \"app\": \"AioShell\",\n")
            append("  \"title\": ${jsonEscape(title)},\n")
            append("  \"exportedAt\": ${jsonEscape(formatTime(System.currentTimeMillis()))},\n")
            append("  \"messages\": [\n")
            messages.forEachIndexed { index, m ->
                append("    {\n")
                append("      \"role\": ${jsonEscape(roleLabel(m.role))},\n")
                append("      \"time\": ${jsonEscape(formatTime(m.createdAt))},\n")
                m.reasoningContent?.takeIf { it.isNotBlank() }?.let {
                    append("      \"reasoning\": ${jsonEscape(it)},\n")
                }
                append("      \"content\": ${jsonEscape(m.content)}\n")
                append("    }")
                if (index != messages.lastIndex) append(",")
                append("\n")
            }
            append("  ]\n")
            append("}\n")
        }

    /** 批量导出 JSON：多个会话聚合为一个根节点。 */
    private fun toJsonMultiple(sessions: List<Pair<String, List<ChatMessage>>>): String =
        buildString {
            append("{\n")
            append("  \"app\": \"AioShell\",\n")
            append("  \"exportedAt\": ${jsonEscape(formatTime(System.currentTimeMillis()))},\n")
            append("  \"conversations\": [\n")
            sessions.forEachIndexed { si, (title, messages) ->
                append("    {\n")
                append("      \"title\": ${jsonEscape(title)},\n")
                append("      \"messages\": [\n")
                messages.forEachIndexed { index, m ->
                    append("        {\n")
                    append("          \"role\": ${jsonEscape(roleLabel(m.role))},\n")
                    append("          \"time\": ${jsonEscape(formatTime(m.createdAt))},\n")
                    m.reasoningContent?.takeIf { it.isNotBlank() }?.let {
                        append("          \"reasoning\": ${jsonEscape(it)},\n")
                    }
                    append("          \"content\": ${jsonEscape(m.content)}\n")
                    append("        }")
                    if (index != messages.lastIndex) append(",")
                    append("\n")
                }
                append("      ]\n")
                append("    }")
                if (si != sessions.lastIndex) append(",")
                append("\n")
            }
            append("  ]\n")
            append("}\n")
        }

    /** 转义为一个合法的 JSON 字符串字面量。 */
    private fun jsonEscape(raw: String): String {
        val sb = StringBuilder("\"")
        for (ch in raw) {
            when (ch) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                else -> if (ch.code < 0x20) sb.append("\\u%04x".format(ch.code)) else sb.append(ch)
            }
        }
        sb.append("\"")
        return sb.toString()
    }
}