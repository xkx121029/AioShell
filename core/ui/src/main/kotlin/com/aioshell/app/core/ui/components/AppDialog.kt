package com.aioshell.app.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aioshell.app.core.ui.theme.AppRadius
import com.aioshell.app.core.ui.theme.AppSpacing
import com.aioshell.app.core.ui.theme.AppTheme

/** 应用内嵌入式确认弹层（不触发系统 Dialog，契合产品偏好）。 */
@Composable
fun AppDialog(
    title: String,
    message: String,
    confirmText: String = "确定",
    dismissText: String = "取消",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = AppTheme.colors
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier.fillMaxSize().background(Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .widthIn(max = 320.dp)
                    .background(
                        color = c.surfaceVariant,
                        shape = RoundedCornerShape(AppRadius.xl),
                    )
                    .padding(24.dp),
            ) {
                TextStyled(title, style = androidx.compose.material3.MaterialTheme.typography.titleLarge, color = c.onSurface)
                TextStyled(
                    message,
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    color = c.secondary,
                    modifier = Modifier.padding(top = AppSpacing.md),
                )
                Row(
                    Modifier.fillMaxWidth().padding(top = AppSpacing.xl),
                    horizontalArrangement = Arrangement.End,
                ) {
                    AppButton(text = dismissText, onClick = onDismiss, style = ButtonStyle.TEXT)
                    AppButton(text = confirmText, onClick = onConfirm, modifier = Modifier.padding(start = AppSpacing.sm))
                }
            }
        }
    }
}

@Composable
private fun TextStyled(
    text: String,
    style: androidx.compose.ui.text.TextStyle,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    androidx.compose.material3.Text(text, style = style, color = color, modifier = modifier)
}