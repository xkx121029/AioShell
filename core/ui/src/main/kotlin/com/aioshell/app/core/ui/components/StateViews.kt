package com.aioshell.app.core.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import com.aioshell.app.core.ui.theme.AppRadius
import com.aioshell.app.core.ui.theme.AppSpacing
import com.aioshell.app.core.ui.theme.AppTheme

/** 统一空状态 - 带图标容器。 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val c = AppTheme.colors
    Column(
        modifier = modifier.fillMaxWidth().padding(AppSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // 图标容器
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(c.primary.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = c.primary,
            )
        }

        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = c.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = AppSpacing.lg),
        )
        if (description != null) {
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = c.secondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = AppSpacing.sm),
            )
        }
        if (actionText != null && onAction != null) {
            AppButton(
                text = actionText,
                onClick = onAction,
                style = ButtonStyle.PRIMARY,
                modifier = Modifier.padding(top = AppSpacing.lg),
            )
        }
    }
}

/** 全屏居中加载态 - 带呼吸动画。 */
@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    val transition = rememberInfiniteTransition(label = "loading")
    val alpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "alpha",
    )

    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                modifier = Modifier.size(40.dp).alpha(alpha),
                strokeWidth = 3.dp,
                color = c.primary,
            )
        }
    }
}

/** 错误状态：必须提供行动入口（重试 / 返回），禁止只展示文案。 */
@Composable
fun ErrorState(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    retryText: String = "重试",
    icon: ImageVector? = null,
) {
    EmptyState(
        modifier = modifier,
        title = "出错了",
        description = message,
        icon = icon ?: Icons.Filled.ErrorOutline,
        actionText = if (onRetry != null) retryText else null,
        onAction = onRetry,
    )
}
