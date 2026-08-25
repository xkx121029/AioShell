package com.aioshell.app.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.aioshell.app.core.ui.theme.AppRadius
import com.aioshell.app.core.ui.theme.AppSpacing
import com.aioshell.app.core.ui.theme.AppTheme

/** 统一卡片容器。 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(AppRadius.lg)
    val surface = c.surfaceVariant
    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier.clip(shape),
            colors = CardDefaults.cardColors(containerColor = surface),
        ) {
            content()
        }
    } else {
        Card(
            modifier = modifier.clip(shape),
            colors = CardDefaults.cardColors(containerColor = surface),
        ) {
            content()
        }
    }
}

/** 卡片内容统一内边距。 */
@Composable
fun AppCardContent(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Box(
        Modifier.padding(AppSpacing.lg),
    ) { content() }
}