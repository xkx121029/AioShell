package com.aioshell.app.core.ui.components

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.aioshell.app.core.ui.theme.AppRadius
import com.aioshell.app.core.ui.theme.AppSpacing
import com.aioshell.app.core.ui.theme.AppTheme

/** 统一输入框：含错误提示与支持性说明文案。 */
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    supportingText: String? = null,
    errorText: String? = null,
    isSecret: Boolean = false,
    isNumber: Boolean = false,
) {
    val c = AppTheme.colors
    val isError = errorText != null
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = if (placeholder.isNotBlank()) { { Text(placeholder) } } else null,
        singleLine = true,
        isError = isError,
        supportingText = {
            if (isError) {
                Text(errorText!!)
            } else if (supportingText != null) {
                Text(supportingText, color = c.secondary)
            }
        },
        visualTransformation = if (isSecret) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = if (isNumber) KeyboardOptions(keyboardType = KeyboardType.Decimal)
        else KeyboardOptions(keyboardType = KeyboardType.Text),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = c.primary,
            errorBorderColor = c.error,
        ),
    )
}

/** 分组标题小节内部的标题（表单分组用）。 */
@Composable
fun FormLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = AppTheme.colors.primary,
        modifier = Modifier,
    )
}

val FormVerticalSpace: androidx.compose.ui.unit.Dp get() = AppSpacing.md