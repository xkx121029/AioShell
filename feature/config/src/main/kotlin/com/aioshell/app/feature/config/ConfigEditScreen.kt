package com.aioshell.app.feature.config

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aioshell.app.core.ui.components.AppButton
import com.aioshell.app.core.ui.components.AppTextField
import com.aioshell.app.core.ui.components.ButtonStyle
import com.aioshell.app.core.ui.theme.AppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigEditScreen(
    onSaved: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: ConfigEditViewModel = hiltViewModel(),
) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    val f = s.form
    val c = AppTheme.colors

    fun f2s(v: Float) = String.format("%.2f", v).replace(',', '.')
    fun s2f(raw: String, def: Float) = raw.toFloatOrNull() ?: def

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (f.id.isBlank()) "新增配置" else "编辑配置", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(inner)
                .padding(horizontal = 20.dp),
        ) {
            AppTextField(
                value = f.name,
                onValueChange = { viewModel.updateField("name", it) },
                label = "名称（可选）",
                modifier = Modifier.fillMaxWidth(),
            )

            ConfigSection("接口地址", spacingTop = androidx.compose.ui.unit.Dp(20f)) {
                AppTextField(
                    value = f.baseUrl,
                    onValueChange = { viewModel.updateField("baseUrl", it) },
                    label = "Base URL",
                    placeholder = "https://your-api.example.com/v1",
                    supportingText = "兼容 OpenAI 协议的接口根地址",
                    errorText = f.baseUrlError,
                    modifier = Modifier.fillMaxWidth(),
                )
                AppTextField(
                    value = f.model,
                    onValueChange = { viewModel.updateField("model", it) },
                    label = "模型名称",
                    placeholder = "gpt-4o-mini",
                    supportingText = "模型名需与接口服务端一致",
                    errorText = f.modelError,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                )
            }

            ConfigSection("认证") {
                AppTextField(
                    value = f.apiKey,
                    onValueChange = { viewModel.updateField("apiKey", it) },
                    label = "API Key",
                    placeholder = "sk-...",
                    isSecret = true,
                    supportingText = "密钥仅保存在本机，加密存储",
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            ConfigSection("模型参数") {
                ParameterSlider(
                    label = "temperature",
                    value = s2f(f.temperature, 0.7f),
                    onValueChange = { viewModel.updateField("temperature", f2s(it)) },
                    range = 0f..2f,
                    supportingText = "数值越高回复越随机，越低越稳定",
                )
                ParameterSlider(
                    label = "top_p",
                    value = s2f(f.topP, 1f),
                    onValueChange = { viewModel.updateField("topP", f2s(it)) },
                    range = 0f..1f,
                    supportingText = "核采样参数，通常与 temperature 二选一",
                )
                ParameterSlider(
                    label = "max_tokens",
                    value = s2f(f.maxTokens, 2048f),
                    onValueChange = { viewModel.updateField("maxTokens", it.toInt().toString()) },
                    range = 1f..8192f,
                    supportingText = "单次回复的最大 Token 数",
                    valueFormatter = { it.toInt().toString() },
                )
            }

            ConfigSection("思考模式") {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("展示思考过程", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "当该模型返回思考内容时，在对话中展示",
                            style = MaterialTheme.typography.bodySmall,
                            color = c.secondary,
                        )
                    }
                    androidx.compose.material3.Switch(
                        checked = f.reasoningEnabled,
                        onCheckedChange = viewModel::setReasoning,
                    )
                }
            }

            s.testMessage?.let { msg ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 12.dp),
                ) {
                    Icon(
                        if (s.testSuccess == true) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
                        contentDescription = null,
                        tint = if (s.testSuccess == true) c.success else c.error,
                    )
                    Text(
                        msg,
                        modifier = Modifier.padding(start = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (s.testSuccess == true) c.success else c.error,
                    )
                }
            }

            Row(Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 40.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppButton(
                    text = "连接测试",
                    onClick = viewModel::testConnection,
                    loading = s.testing,
                    style = ButtonStyle.SECONDARY,
                    enabled = !s.saving,
                    modifier = Modifier.weight(1f),
                )
                AppButton(
                    text = "保存",
                    onClick = { viewModel.save { onSaved(it) } },
                    loading = s.saving,
                    enabled = !s.testing,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}