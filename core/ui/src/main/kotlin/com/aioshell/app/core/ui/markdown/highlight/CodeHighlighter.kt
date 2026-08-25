package com.aioshell.app.core.ui.markdown.highlight

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle

/**
 * 轻量语法高亮：覆盖 关键字 / 字符串 / 注释 / 数字。
 * 输出 [AnnotatedString]，通过调用方 remember 缓存避免重复解析。
 */
object CodeHighlighter {

    fun highlight(
        code: String,
        language: String?,
        keywordColor: Color,
        stringColor: Color,
        commentColor: Color,
        numberColor: Color,
    ): AnnotatedString {
        val lang = HighlighterRegistry.normalize(language) ?: return AnnotatedString(code)
        val lineComment = HighlighterRegistry.lineCommentOf(lang)
        val blockComment = HighlighterRegistry.hasBlockComment(lang)

        return buildAnnotatedString {
            var inBlock = false
            val lines = code.split('\n')
            lines.forEachIndexed { li, line ->
                var i = 0
                var inString: Char? = null
                var inLineComment = false
                while (i < line.length) {
                    when {
                        inLineComment -> {
                            // 逐字加入，直到行尾（保持换行结构）
                            append(line[i]); i++
                        }
                        inString != null -> {
                            val ch = line[i]
                            if (ch == '\\' && i + 1 < line.length) {
                                withStyle(SpanStyle(color = stringColor, fontFamily = FontFamily.Monospace)) {
                                    append(line[i]); append(line[i + 1])
                                }
                                i += 2
                            } else {
                                withStyle(SpanStyle(color = stringColor, fontFamily = FontFamily.Monospace)) { append(ch) }
                                if (ch == inString) inString = null
                                i++
                            }
                        }
                        inBlock -> {
                            if (line.startsWith("*/", i)) {
                                withStyle(SpanStyle(color = commentColor, fontFamily = FontFamily.Monospace)) {
                                    append("*/"); i += 2
                                }
                                inBlock = false
                            } else {
                                withStyle(SpanStyle(color = commentColor, fontFamily = FontFamily.Monospace)) { append(line[i]); i++ }
                            }
                        }
                        lineComment != null && line.startsWith(lineComment, i) -> {
                            withStyle(SpanStyle(color = commentColor, fontFamily = FontFamily.Monospace)) { append(line.substring(i)) }
                            i = line.length
                            inLineComment = true
                        }
                        blockComment && line.startsWith("/*", i) -> {
                            withStyle(SpanStyle(color = commentColor, fontFamily = FontFamily.Monospace)) { append("/*") }
                            i += 2
                            inBlock = true
                        }
                        line[i] == '"' || line[i] == '\'' -> {
                            inString = line[i]
                            withStyle(SpanStyle(color = stringColor, fontFamily = FontFamily.Monospace)) { append(line[i]) }
                            i++
                        }
                        line[i].isDigit() || (line[i] == '-' && i + 1 < line.length && line[i + 1].isDigit()) -> {
                            val start = i
                            while (i < line.length && (line[i].isLetterOrDigit() || line[i] == '.' || line[i] == '_' || line[i] == 'x' || line[i] == 'X')) i++
                            withStyle(SpanStyle(color = numberColor, fontFamily = FontFamily.Monospace)) {
                                append(line.substring(start, i))
                            }
                        }
                        line[i].isLetter() || line[i] == '_' -> {
                            val start = i
                            while (i < line.length && (line[i].isLetterOrDigit() || line[i] == '_')) i++
                            val word = line.substring(start, i)
                            val color = if (HighlighterRegistry.isKeyword(lang, word)) keywordColor else null
                            if (color != null) {
                                withStyle(SpanStyle(color = color, fontFamily = FontFamily.Monospace)) { append(word) }
                            } else {
                                withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(word) }
                            }
                        }
                        else -> {
                            withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(line[i]) }
                            i++
                        }
                    }
                }
                if (li < lines.lastIndex) append('\n')
            }
        }
    }
}