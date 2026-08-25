package com.aioshell.app.feature.config

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.aioshell.app.core.ui.theme.AppSpacing
import com.aioshell.app.core.ui.theme.AppTheme

/** 配置表单分组。 */
@Composable
fun ConfigSection(
    title: String,
    spacingTop: androidx.compose.ui.unit.Dp = AppSpacing.xl,
    content: @Composable () -> Unit,
) {
    Column(Modifier.padding(top = spacingTop)) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = AppTheme.colors.primary,
            modifier = Modifier.padding(bottom = AppSpacing.sm),
        )
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
            Text(label, modifier = Modifier.weight(1f), color = c.onSurface)
            Text(
                valueFormatter(value),
                style = MaterialTheme.typography.labelLarge,
                fontFamily = FontFamily.Monospace,
                color = c.primary,
            )
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(thumbColor = c.primary, activeTrackColor = c.primary),
        )
        if (supportingText != null) {
            Text(
                supportingText,
                style = MaterialTheme.typography.bodySmall,
                color = c.secondary,
            )
        }
    }
}