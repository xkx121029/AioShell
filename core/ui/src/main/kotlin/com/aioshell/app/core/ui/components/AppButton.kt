package com.aioshell.app.core.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aioshell.app.core.ui.theme.AppRadius
import com.aioshell.app.core.ui.theme.AppTheme

enum class ButtonStyle { PRIMARY, SECONDARY, TEXT }

/** 统一按钮：主 / 次 / 文字，含禁用与加载态，触控高度 48dp。 */
@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    style: ButtonStyle = ButtonStyle.PRIMARY,
) {
    val c = AppTheme.colors
    val containerColor = when (style) {
        ButtonStyle.PRIMARY -> c.primary
        ButtonStyle.SECONDARY -> c.surfaceVariant
        ButtonStyle.TEXT -> c.surface
    }
    val contentColor = when (style) {
        ButtonStyle.PRIMARY -> c.onPrimary
        ButtonStyle.SECONDARY -> c.onSurface
        ButtonStyle.TEXT -> c.primary
    }
    val shape = RoundedCornerShape(AppRadius.md)

    @Composable
    fun Content() {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = LocalContentColor.current,
            )
        } else {
            Text(text)
        }
    }

    when (style) {
        ButtonStyle.TEXT -> TextButton(
            onClick = onClick,
            enabled = enabled && !loading,
            modifier = modifier,
            shape = shape,
        ) { Content() }

        else -> Button(
            onClick = onClick,
            enabled = enabled && !loading,
            modifier = modifier,
            shape = shape,
            colors = ButtonDefaults.buttonColors(
                containerColor = containerColor,
                contentColor = contentColor,
                disabledContainerColor = containerColor.copy(alpha = 0.4f),
            ),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
        ) { Content() }
    }
}

/** 便捷：连接测试/保存等带加载态的按钮封装。 */
@Composable
fun AppLoadingButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    style: ButtonStyle = ButtonStyle.PRIMARY,
) {
    AppButton(text = text, onClick = onClick, modifier = modifier, loading = loading, style = style)
}