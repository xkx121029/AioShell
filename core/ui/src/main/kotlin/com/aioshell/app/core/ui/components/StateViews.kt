package com.aioshell.app.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import com.aioshell.app.core.ui.theme.AppSpacing
import com.aioshell.app.core.ui.theme.AppTheme

/** 统一空状态。 */
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
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = c.secondary,
        )
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = c.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = AppSpacing.md),
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
                style = ButtonStyle.SECONDARY,
                modifier = Modifier.padding(top = AppSpacing.lg),
            )
        }
    }
}

/** 全屏居中加载态。 */
@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            modifier = Modifier.size(32.dp),
            strokeWidth = 3.dp,
            color = AppTheme.colors.primary,
        )
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