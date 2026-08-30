package com.aioshell.app.feature.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aioshell.app.core.ui.theme.AppRadius
import com.aioshell.app.core.ui.theme.AppSpacing
import com.aioshell.app.core.ui.theme.AppTheme

/** 思考过程面板（可折叠）- 带图标和更好的视觉区分。 */
@Composable
fun ReasoningPanel(
    reasoning: String,
    isStreaming: Boolean,
    durationMs: Long?,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = AppTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppRadius.md))
            .background(c.primary.copy(alpha = 0.06f))
            .padding(start = 4.dp),
    ) {
        // 左侧装饰条
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 装饰条
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxWidth(0.15f)
                    .background(c.primary.copy(alpha = if (isStreaming) 0.8f else 0.5f), RoundedCornerShape(1.5.dp))
                    .padding(vertical = AppSpacing.sm),
            )

            Icon(
                imageVector = Icons.Outlined.Psychology,
                contentDescription = null,
                tint = c.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(AppSpacing.sm))
            Text(
                text = buildString {
                    append("思考过程")
                    if (isStreaming) append(" · 思考中…")
                    else if (durationMs != null) append(" · 用时 ${formatDuration(durationMs)}")
                },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = c.primary,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = c.secondary,
                modifier = Modifier.size(18.dp),
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.sm),
            ) {
                Text(
                    text = reasoning,
                    style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                    color = c.onSurfaceVariant,
                    lineHeight = 18.dp,
                )
            }
        }
    }
}

private fun formatDuration(ms: Long): String = "%.1f".format(ms / 1000f) + "s"
