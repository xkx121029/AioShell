package com.aioshell.app.core.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.Spring.StiffnessMediumLow
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aioshell.app.core.ui.animation.rememberBlinkingAlpha
import com.aioshell.app.core.ui.markdown.MarkdownText
import com.aioshell.app.core.ui.theme.AppMotion
import com.aioshell.app.core.ui.theme.AppRadius
import com.aioshell.app.core.ui.theme.AppSpacing
import com.aioshell.app.core.ui.theme.AppTheme
import com.aioshell.app.core.ui.theme.LocalChatTextSettings

/**
 * 统一消息气泡（渲染层不依赖数据模型，便于复用）。
 * @param content 消息文本
 * @param isUser 是否为用户消息
 * @param isStreaming 是否正在流式生成（尾部光标）
 * @param isError 是否为错误消息
 * @param animate 是否播放出现动效
 */
@Composable
fun MessageBubble(
    content: String,
    isUser: Boolean,
    modifier: Modifier = Modifier,
    isStreaming: Boolean = false,
    isError: Boolean = false,
    animate: Boolean = false,
) {
    val c = AppTheme.colors
    val chatText = LocalChatTextSettings.current
    val bubbleColor = when {
        isUser -> c.userBubble.copy(alpha = 0.15f)
        else -> c.aiBubble
    }
    val textColor = when {
        isError -> c.error
        isUser -> c.onSurface
        else -> c.onAiBubble
    }
    val bubbleShape = RoundedCornerShape(
        topStart = AppRadius.lg,
        topEnd = AppRadius.lg,
        bottomStart = if (isUser) AppRadius.lg else AppRadius.sm,
        bottomEnd = if (isUser) AppRadius.sm else AppRadius.lg,
    )
    val borderColor = if (isUser) c.primary.copy(alpha = 0.3f) else c.outline.copy(alpha = 0.2f)
    val elevation = if (isUser) 2.dp else 1.dp

    var visible by remember { mutableStateOf(!animate) }
    LaunchedEffect(Unit) { if (animate) visible = true }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(AppMotion.durationNormal)) +
            scaleIn(
                initialScale = 0.92f,
                animationSpec = spring(dampingRatio = 0.76f, stiffness = StiffnessMediumLow),
            ) +
            slideInVertically(tween(AppMotion.durationNormal)) { it / 10 },
    ) {
        BoxWithConstraints(
            modifier = modifier.fillMaxWidth().padding(vertical = AppSpacing.sm),
        ) {
            // 气泡最大宽度随可用宽度缩放（大屏更好看，替代固定 320dp）
            val maxBubble = maxWidth * (if (isUser) 0.80f else 0.88f)
            Row(Modifier.fillMaxWidth()) {
                if (isUser) Spacer(Modifier.weight(1f))
                Column(
                    Modifier
                        .widthIn(max = maxBubble)
                        .shadow(elevation = elevation, shape = bubbleShape)
                        .background(color = bubbleColor, shape = bubbleShape)
                        .border(1.dp, borderColor, bubbleShape)
                        .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.md),
                ) {
                    if (isUser) {
                        Text(
                            content,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = chatText.fontSizeSp.sp,
                                lineHeight = (MaterialTheme.typography.bodyLarge.lineHeight.value + chatText.lineSpacingSp).sp,
                            ),
                            color = textColor,
                            fontWeight = FontWeight.Medium,
                        )
                    } else {
                        MarkdownText(content, textColor = textColor)
                    }
                    if (isStreaming) {
                        val alpha = rememberBlinkingAlpha()
                        Box(Modifier.padding(top = 2.dp).alpha(alpha)) {
                            Text("▍", color = c.primary, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                if (!isUser) Spacer(Modifier.weight(1f))
            }
        }
    }
}
