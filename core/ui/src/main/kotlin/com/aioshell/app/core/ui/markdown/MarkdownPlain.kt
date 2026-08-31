package com.aioshell.app.core.ui.markdown

/** 把 Markdown 文本粗略转为纯文本（去标记、保留可读内容）。用于"复制为纯文本"。 */
fun markdownToPlainText(raw: String): String {
    if (raw.isBlank()) return raw
    val lines = raw.split("\n")
    return buildString {
        lines.forEach { line ->
            var l = line.trimEnd()
            // 代码围栏整行跳过
            if (l.startsWith("```")) return@forEach
            // 标题
            l = l.replace(Regex("^#{1,6}\\s+"), "")
            // 引用
            l = l.replace(Regex("^>\\s?"), "")
            // 无序列表
            l = l.replace(Regex("^\\s*[-*+]\\s+"), "· ")
            // 有序列表
            l = l.replace(Regex("^\\s*\\d+[.、]\\s+"), "")
            // 行内代码
            l = l.replace("`", "")
            // 粗体 / 斜体 / 下划线式
            l = l.replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1")
            l = l.replace(Regex("\\*([^*]+)\\*"), "$1")
            l = l.replace(Regex("__([^_]+)__"), "$1")
            l = l.replace(Regex("_([^_]+)_"), "$1")
            // 删除线
            l = l.replace(Regex("~~([^~]+)~~"), "$1")
            // 图片 ![alt](url) → alt
            l = l.replace(Regex("!\\[([^\\]]*)]\\([^)]*\\)"), "$1")
            // 链接 [text](url) → text
            l = l.replace(Regex("\\[([^\\]]+)]\\([^)]*\\)"), "$1")
            appendLine(l)
        }
    }.trim()
}
