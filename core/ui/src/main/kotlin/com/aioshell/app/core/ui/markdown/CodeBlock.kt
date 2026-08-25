package com.aioshell.app.core.ui.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aioshell.app.core.ui.markdown.highlight.CodeHighlighter
import com.aioshell.app.core.ui.theme.AppRadius
import com.aioshell.app.core.ui.theme.AppSpacing
import com.aioshell.app.core.ui.theme.AppTheme
import kotlinx.coroutines.delay

private const val MAX_COLLAPSED_LINES = 20
private const val HIGHLIGHT_MAX_CHARS = 200_000

/**
 * 增强代码块：语法高亮 / 一键复制 / 折叠展开 / 语言标签 / 横向滚动。
 * 超长代码超过阈值时降级为纯文本，避免高亮开销。
 */
@Composable
fun CodeBlock(
    code: String,
    language: String?,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val clipboard = LocalClipboardManager.current
    val lineCount = remember(code) { code.count { it == '\n' } + 1 }
    var expanded by remember(code) { mutableStateOf(lineCount <= MAX_COLLAPSED_LINES) }
    var copied by remember { mutableStateOf(false) }

    // 高亮结果缓存（大码块降级为纯文本）
    val highlighted = remember(code, language) {
        if (code.length <= HIGHLIGHT_MAX_CHARS) {
            CodeHighlighter.highlight(
                code, language,
                keywordColor = colors.codeKeyword,
                stringColor = colors.codeString,
                commentColor = colors.codeComment,
                numberColor = colors.codeNumber,
            )
        } else {
            AnnotatedString(code)
        }
    }

    LaunchedEffect(copied) {
        if (copied) { delay(2000); copied = false }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppRadius.md))
            .background(colors.codeBackground),
    ) {
        // 顶栏：语言标签 + 复制
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surfaceVariant)
                .padding(horizontal = AppSpacing.md, vertical = AppSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = language?.uppercase() ?: "CODE",
                style = MaterialTheme.typography.labelSmall,
                color = colors.secondary,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = {
                clipboard.setText(AnnotatedString(code))
                copied = true
            }, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = if (copied) Icons.Filled.Check else Icons.Outlined.ContentCopy,
                    contentDescription = "复制代码",
                    tint = colors.secondary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        // 代码主体：横向滚动 + 折叠
        Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            Text(
                text = highlighted,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(AppSpacing.md),
                maxLines = if (expanded) Int.MAX_VALUE else MAX_COLLAPSED_LINES,
                overflow = TextOverflow.Clip,
            )
        }

        if (lineCount > MAX_COLLAPSED_LINES) {
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "收起" else "展开全部（${lineCount} 行）")
            }
        }
    }
}