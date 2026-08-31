package com.aioshell.app.feature.chat

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/** Markdown 快捷格式动作。 */
enum class MarkdownAction {
    /** 加粗 **加粗** */
    BOLD,

    /** 斜体 *斜体* */
    ITALIC,

    /** 行内代码 `代码` */
    CODE,

    /** 代码块 ``` ``` */
    CODE_BLOCK,

    /** 标题 ## */
    HEADING,

    /** 链接 [文字](url) */
    LINK,

    /** 无序列表 - 项 */
    LIST,
}

/**
 * 在光标处应用 Markdown 格式：
 * - 有选区时包裹选区；
 * - 无选区时插入占位文字并把光标定位到占位末尾；
 * - 行级动作（标题/列表）作用于当前行行首，再次触发可取消。
 */
fun applyMarkdown(input: TextFieldValue, action: MarkdownAction): TextFieldValue {
    val text = input.text
    val start = input.selection.min
    val end = input.selection.max

    // 行级格式：作用于当前行行首
    if (action == MarkdownAction.HEADING || action == MarkdownAction.LIST) {
        val lineStart = text.lastIndexOf('\n', (start - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
        val marker = if (action == MarkdownAction.HEADING) "## " else "- "
        val prefix = text.substring(lineStart, start)
        val newText: String
        val cursor: Int
        if (prefix.startsWith(marker)) {
            // 已存在标记 → 移除（切换）
            newText = text.removeRange(lineStart, lineStart + marker.length)
            cursor = (start - marker.length).coerceAtLeast(lineStart)
        } else {
            newText = text.substring(0, lineStart) + marker + text.substring(lineStart)
            cursor = start + marker.length
        }
        return TextFieldValue(newText, TextRange(cursor.coerceIn(0, newText.length)))
    }

    // 选区级格式：包裹选区，无选区时插入占位文字
    val (prefix, placeholder, suffix) = when (action) {
        MarkdownAction.BOLD -> Triple("**", "加粗文字", "**")
        MarkdownAction.ITALIC -> Triple("*", "斜体文字", "*")
        MarkdownAction.CODE -> Triple("`", "代码", "`")
        MarkdownAction.CODE_BLOCK -> Triple("```\n", "代码", "\n```")
        MarkdownAction.LINK -> Triple("[", "链接文字", "](https://)")
        else -> Triple("", "", "")
    }
    val selected = text.substring(start, end)
    val inner = selected.ifBlank { placeholder }
    val newText = buildString {
        append(text.substring(0, start))
        append(prefix)
        append(inner)
        append(suffix)
        append(text.substring(end))
    }
    val cursor = if (selected.isNotBlank()) {
        start + prefix.length + inner.length + suffix.length
    } else {
        // 光标停留在占位文字末尾
        start + prefix.length + inner.length
    }
    return TextFieldValue(newText, TextRange(cursor.coerceIn(0, newText.length)))
}
