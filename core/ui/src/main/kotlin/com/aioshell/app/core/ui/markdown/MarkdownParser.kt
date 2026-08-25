package com.aioshell.app.core.ui.markdown

/** 轻量 Markdown 块。 */
sealed interface MarkdownBlock
data class ParagraphBlock(val raw: String) : MarkdownBlock
data class HeadingBlock(val level: Int, val raw: String) : MarkdownBlock
data class BulletBlock(val items: List<String>) : MarkdownBlock
data class QuoteBlock(val raw: String) : MarkdownBlock
data class CodeBlockData(val language: String?, val code: String) : MarkdownBlock
object DividerBlock : MarkdownBlock

/**
 * 轻量 Markdown 解析器：覆盖高频语法（标题 / 列表 / 引用 / 代码块 / 分隔线）。
 * 对未闭合代码块做降级处理，容忍流式过程中的不完整输入。
 */
object MarkdownParser {

    private val headingRe = Regex("^(#{1,6})\\s+(.*)$")
    private val bulletRe = Regex("^([-*+])\\s(.*)$")
    private val orderedRe = Regex("^\\d+\\.\\s(.*)$")
    private val dividerRe = Regex("^\\s*(-{3,}|\\*{3,}|_{3,})\\s*$")

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
                val items = mutableListOf(orderedMatch.groupValues[1])
                i++
                while (i < lines.size) {
                    val m = orderedRe.find(lines[i].trimEnd()) ?: break
                    items.add(m.groupValues[1])
                    i++
                }
                blocks.add(BulletBlock(items))
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
}