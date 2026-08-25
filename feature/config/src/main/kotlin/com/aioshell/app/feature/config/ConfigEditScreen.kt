package com.aioshell.app.feature.config

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aioshell.app.core.ui.components.AioConfirmDialog
import androidx.compose.runtime.setValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigEditScreen(
    onSaved: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: ConfigEditViewModel = hiltViewModel(),
) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (s.form.id.isBlank()) "新增配置" else "编辑配置", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "返回") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(inner)
                .padding(horizontal = 20.dp),
        ) {
            Field("名称（可选）", s.form.name, "name", viewModel)
            Field("Base URL", s.form.baseUrl, "baseUrl", viewModel, placeholder = "https://api.openai.com/v1")
            Field("API Key", s.form.apiKey, "apiKey", viewModel, isSecret = true, placeholder = "sk-...")
            Field("模型名称", s.form.model, "model", viewModel, placeholder = "gpt-4o-mini")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Field("temperature", s.form.temperature, "temperature", viewModel, isNumber = true, compact = true)
                }
                Column(Modifier.weight(1f)) {
                    Field("top_p", s.form.topP, "topP", viewModel, isNumber = true, compact = true)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Field("max_tokens", s.form.maxTokens, "maxTokens", viewModel, isNumber = true, compact = true)
                }
            }

            s.testMessage?.let {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Icon(
                        if (s.testSuccess == true) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
                        contentDescription = null,
                        tint = if (s.testSuccess == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    )
                    Text(
                        it,
                        modifier = Modifier.padding(start = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (s.testSuccess == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    )
                }
            }

            Row(Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 40.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = viewModel::testConnection,
                    enabled = !s.testing && !s.saving,
                    modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 12.dp),
            ) {
                if (s.testing) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("连接测试")
                }
            }
                Button(
                    onClick = { viewModel.save { onSaved(it) } },
                    enabled = !s.testing && !s.saving,
                    modifier = Modifier.weight(1f),
                ) {
                    if (s.saving) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    } else {
                        Text("保存")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Field(
    label: String,
    value: String,
    key: String,
    viewModel: ConfigEditViewModel,
    placeholder: String = "",
    isSecret: Boolean = false,
    isNumber: Boolean = false,
    compact: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { viewModel.updateField(key, it) },
        label = { Text(label) },
        placeholder = if (placeholder.isNotBlank()) { { Text(placeholder) } } else null,
        singleLine = true,
        visualTransformation = if (isSecret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = if (isNumber) {
            KeyboardOptions(keyboardType = KeyboardType.Decimal)
        } else {
            KeyboardOptions(keyboardType = KeyboardType.Text)
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (compact) 8.dp else 16.dp),
        shape = MaterialTheme.shapes.medium,
    )
}