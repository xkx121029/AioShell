package com.aioshell.app.core.ui.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.aioshell.app.core.ui.theme.AppRadius
import com.aioshell.app.core.ui.theme.AppSpacing
import com.aioshell.app.core.ui.theme.AppTheme

/**
 * 轻量 Markdown 渲染器。
 * 支持：标题 / 段落 / 列表 / 引用 / 代码块 / 粗体 / 斜体 / 行内代码 / 链接。
 * 流式解析容忍未闭合语法，避免渲染错乱。
 */
@Composable
fun MarkdownText(
    content: String,
    modifier: Modifier = Modifier,
    textColor: Color? = null,
) {
    val c = AppTheme.colors
    val baseColor = textColor ?: c.onSurface
    val baseStyle = MaterialTheme.typography.bodyLarge
    val blocks = MarkdownParser.parse(content)
    val codeBg = c.codeBackground.copy(alpha = 0.6f)
    val linkColor = c.primary

    Column(modifier = modifier) {
        blocks.forEach { block ->
            when (block) {
                is HeadingBlock -> {
                    val style = when (block.level) {
                        1 -> MaterialTheme.typography.headlineSmall
                        2 -> MaterialTheme.typography.titleLarge
                        else -> MaterialTheme.typography.titleMedium
                    }
                    Text(
                        parseInline(block.raw, baseColor, codeBg, linkColor),
                        style = style.copy(color = baseColor, fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                    )
                }
                is ParagraphBlock -> {
                    Text(
                        parseInline(block.raw, baseColor, codeBg, linkColor),
                        style = baseStyle.copy(color = baseColor),
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                is BulletBlock -> {
                    block.items.forEach { item ->
                        Row(Modifier.padding(bottom = 2.dp), verticalAlignment = Alignment.Top) {
                            Box(
                                Modifier
                                    .padding(top = 9.dp, start = 2.dp, end = 8.dp)
                                    .size(6.dp)
                                    .clip(RoundedCornerShape(AppRadius.full))
                                    .background(baseColor.copy(alpha = 0.6f)),
                            )
                            Text(
                                parseInline(item, baseColor, codeBg, linkColor),
                                style = baseStyle.copy(color = baseColor),
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                is QuoteBlock -> {
                    Row(Modifier.padding(vertical = 2.dp)) {
                        Box(
                            Modifier
                                .width(3.dp)
                                .padding(end = AppSpacing.sm)
                                .clip(RoundedCornerShape(AppRadius.full))
                                .background(c.secondary),
                        )
                        Text(
                            parseInline(block.raw, baseColor.copy(alpha = 0.85f), codeBg, linkColor),
                            style = baseStyle.copy(color = baseColor.copy(alpha = 0.85f)),
                        )
                    }
                }
                is CodeBlockData -> {
                    CodeBlock(code = block.code, language = block.language, modifier = Modifier.padding(vertical = 4.dp))
                }
                DividerBlock -> {
                    HorizontalDivider(
                        color = c.outline.copy(alpha = 0.5f),
                        modifier = Modifier.padding(vertical = AppSpacing.sm),
                    )
                }
            }
        }
    }
}

/** 行内标记解析：`code`、**粗体**、*斜体*、[链接](url)。 */
private fun parseInline(
    raw: String,
    baseColor: Color,
    codeBackground: Color,
    linkColor: Color,
): AnnotatedString = buildAnnotatedString {
    val codeParts = raw.split('`')
    codeParts.forEachIndexed { idx, part ->
        if (part.isEmpty()) return@forEachIndexed
        if (idx % 2 == 1) {
            withStyle(
                SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    color = baseColor,
                    background = codeBackground,
                ),
            ) { append(part) }
            return@forEachIndexed
        }
        appendStyled(part, baseColor, linkColor)
    }
}

private fun AnnotatedString.Builder.appendStyled(
    part: String,
    baseColor: Color,
    linkColor: Color,
) {
    var i = 0
    val n = part.length
    while (i < n) {
        val ch = part[i]
        when {
            ch == '*' && i + 1 < n && part[i + 1] == '*' -> {
                val close = part.indexOf("**", i + 2)
                if (close > i + 1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = baseColor)) { append(part.substring(i + 2, close)) }
                    i = close + 2
                } else { append('*'); i++ }
            }
            ch == '*' -> {
                val close = part.indexOf('*', i + 1)
                if (close > i + 1) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = baseColor)) { append(part.substring(i + 1, close)) }
                    i = close + 1
                } else { append('*'); i++ }
            }
            ch == '[' -> {
                val cb = part.indexOf(']', i + 1)
                if (cb > i + 1 && cb + 1 < n && part[cb + 1] == '(') {
                    val cp = part.indexOf(')', cb + 2)
                    if (cp > cb + 2) {
                        append(part.substring(i + 1, cb))
                        i = cp + 1
                    } else { append(ch); i++ }
                } else { append(ch); i++ }
            }
            else -> { append(ch); i++ }
        }
    }
}