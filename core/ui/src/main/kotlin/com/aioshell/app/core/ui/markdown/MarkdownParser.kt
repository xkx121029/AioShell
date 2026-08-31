package com.aioshell.app.core.ui.markdown

/** 轻量 Markdown 块。 */
sealed interface MarkdownBlock
data class ParagraphBlock(val raw: String) : MarkdownBlock
data class HeadingBlock(val level: Int, val raw: String) : MarkdownBlock
data class BulletBlock(val items: List<String>) : MarkdownBlock

/** 有序列表：保留序号便于展示。 */
data class OrderedBlock(val items: List<Pair<Int, String>>) : MarkdownBlock

/** 任务列表项：已勾选 / 未勾选 + 文字。 */
data class TaskListBlock(val items: List<Pair<Boolean, String>>) : MarkdownBlock

/** Markdown 表格。 */
data class TableBlock(
    val headers: List<String>,
    val rows: List<List<String>>,
) : MarkdownBlock

data class QuoteBlock(val raw: String) : MarkdownBlock
data class CodeBlockData(val language: String?, val code: String) : MarkdownBlock
object DividerBlock : MarkdownBlock

/**
 * 轻量 Markdown 解析器：覆盖高频语法（标题 / 列表 / 任务列表 / 表格 / 引用 / 代码块 / 分隔线）。
 * 对未闭合代码块做降级处理，容忍流式过程中的不完整输入。
 */
object MarkdownParser {

    private val headingRe = Regex("^(#{1,6})\\s+(.*)$")
    private val bulletRe = Regex("^([-*+])\\s(.*)$")
    private val orderedRe = Regex("^\\d+\\.\\s(.*)$")
    private val dividerRe = Regex("^\\s*(-{3,}|\\*{3,}|_{3,})\\s*$")
    /** 任务列表项：- [ ] / - [x] / - [X]。 */
    private val taskRe = Regex("^\\s*[-*+]\\s+\\[([ xX]?)]\\s+(.*)$")
    /** 分隔行（含冒号对齐语法），用于识别表格。 */
    private val separatorRowRe = Regex("^\\s*\\|?[\\s:|-]+\\|\\s*$")

    fun parse(md: String): List<MarkdownBlock> {
        val blocks = mutableListOf<MarkdownBlock>()
        val lines = md.split('\n')
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trimEnd()
            if (line.isBlank()) { i++; continue }

            // 代码块（容忍未闭合）
            if (line.startsWith("```")) {
                val lang = line.drop(3).trim().takeIf { it.isNotBlank() }
                val buf = StringBuilder()
                i++
                while (i < lines.size) {
                    val l = lines[i]
                    if (l.trimStart().startsWith("```")) { i++; break }
                    buf.append(l).append('\n')
                    i++
                }
                blocks.add(CodeBlockData(lang, buf.toString().trimEnd('\n')))
                continue
            }

            // 标题
            val h = headingRe.find(line)
            if (h != null) {
                blocks.add(HeadingBlock(h.groupValues[1].length, h.groupValues[2]))
                i++
                continue
            }

            // 引用
            if (line.startsWith(">")) {
                blocks.add(QuoteBlock(line.drop(1).trim()))
                i++
                continue
            }

            // 表格：当前行是表头，下一行是分隔行
            if (line.contains('|') && i + 1 < lines.size && isTableSeparator(lines[i + 1])) {
                val headers = splitTableRow(line)
                i += 2
                val rows = mutableListOf<List<String>>()
                while (i < lines.size) {
                    val r = lines[i].trimEnd()
                    if (r.isBlank()) break
                    if (!r.contains('|')) break
                    rows.add(splitTableRow(r))
                    i++
                }
                blocks.add(TableBlock(headers, rows))
                continue
            }

            // 任务列表
            val taskMatch = taskRe.find(line)
            if (taskMatch != null) {
                val items = mutableListOf<Pair<Boolean, String>>()
                var mark = parseTaskChecks(taskMatch.groupValues[1])
                var text = taskMatch.groupValues[2]
                items.add(if (mark) true to text else false to text)
                i++
                while (i < lines.size) {
                    val m = taskRe.find(lines[i].trimEnd()) ?: break
                    val checked = parseTaskChecks(m.groupValues[1])
                    items.add(if (checked) true to m.groupValues[2] else false to m.groupValues[2])
                    i++
                }
                blocks.add(TaskListBlock(items))
                continue
            }

            // 无序列表
            val bulletMatch = bulletRe.find(line)
            if (bulletMatch != null) {
                val items = mutableListOf(bulletMatch.groupValues[2])
                i++
                while (i < lines.size) {
                    val m = bulletRe.find(lines[i].trimEnd()) ?: break
                    items.add(m.groupValues[2])
                    i++
                }
                blocks.add(BulletBlock(items))
                continue
            }

            // 有序列表
            val orderedMatch = orderedRe.find(line)
            if (orderedMatch != null) {
                val items = mutableListOf<Pair<Int, String>>()
                var num = 1
                items.add(num to orderedMatch.groupValues[1])
                i++
                while (i < lines.size) {
                    val m = orderedRe.find(lines[i].trimEnd()) ?: break
                    num++
                    items.add(num to m.groupValues[1])
                    i++
                }
                blocks.add(OrderedBlock(items))
                continue
            }

            // 分隔线
            if (dividerRe.matches(line)) {
                blocks.add(DividerBlock)
                i++
                continue
            }

            // 普通段落：收集直到空行或遇到新块起点
            val para = mutableListOf(line)
            i++
            while (i < lines.size) {
                val l = lines[i].trimEnd()
                if (l.isBlank()) break
                if (l.startsWith("```") || l.startsWith(">") || headingRe.containsMatchIn(l) ||
                    bulletRe.containsMatchIn(l) || orderedRe.containsMatchIn(l) || dividerRe.matches(l)
                ) break
                para.add(l)
                i++
            }
            blocks.add(ParagraphBlock(para.joinToString("\n")))
        }
        return blocks
    }

    private fun isTableSeparator(raw: String): Boolean {
        val t = raw.trim()
        if (!t.contains('|')) return false
        // 去掉首尾竖线后，剩余内容应为 --- 或 :--- 形式
        return t.trim('|', ' ').split('|').all { cell -> Regex("^:?-{2,}:?$").matches(cell.trim()) }
    }

    private fun splitTableRow(raw: String): List<String> =
        raw.trim().trim('|').split('|').map { it.trim() }

    private fun parseTaskChecks(c: String): Boolean = c.isNotBlank()
}