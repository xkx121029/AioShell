package com.aioshell.app.feature.config

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aioshell.app.core.ui.theme.AppRadius
import com.aioshell.app.core.ui.theme.AppSpacing
import com.aioshell.app.core.ui.theme.AppTheme

/** 配置表单分组 - 带视觉分隔线。 */
@Composable
fun ConfigSection(
    title: String,
    spacingTop: androidx.compose.ui.unit.Dp = AppSpacing.xl,
    content: @Composable () -> Unit,
) {
    Column(Modifier.padding(top = spacingTop)) {
        // 分组标题背景条
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(bottom = AppSpacing.sm),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = AppSpacing.sm)
                    .height(1.dp)
                    .background(AppTheme.colors.outline.copy(alpha = 0.2f)),
            )
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = AppTheme.colors.primary,
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = AppSpacing.sm)
                    .height(1.dp)
                    .background(AppTheme.colors.outline.copy(alpha = 0.2f)),
            )
        }
        content()
    }
}

/** 参数滑块：滑块 + 数值，替代纯文本输入。 */
@Composable
fun ParameterSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float>,
    supportingText: String? = null,
    valueFormatter: (Float) -> String = { String.format("%.2f", it) },
) {
    val c = AppTheme.colors
    Column(Modifier.fillMaxWidth().padding(vertical = AppSpacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                modifier = Modifier.weight(1f),
                color = c.onSurface,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(AppRadius.sm))
                    .background(c.primary.copy(alpha = 0.12f))
                    .padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xs),
            ) {
                Text(
                    valueFormatter(value),
                    style = MaterialTheme.typography.labelLarge,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    color = c.primary,
                )
            }
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
            thumb = {
                SliderDefaults.Thumb(
                    interactionSource = androidx.compose.foundation.interaction.MutableInteractionSource(),
                )
            },
            colors = SliderDefaults.colors(
                activeTrackColor = c.primary,
                inactiveTrackColor = c.outline.copy(alpha = 0.3f),
                thumbColor = c.primary,
            ),
        )
        if (supportingText != null) {
            Text(
                supportingText,
                style = MaterialTheme.typography.bodySmall,
                color = c.secondary,
                modifier = Modifier.padding(top = AppSpacing.xs),
            )
        }
    }
}
