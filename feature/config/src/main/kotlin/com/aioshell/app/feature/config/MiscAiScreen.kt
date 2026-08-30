package com.aioshell.app.feature.config

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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

/**
 * 杂项 AI 配置：独立于聊天主模型，用于 对话命名 + 意图分析 + 生成身份/回答风格建议。
 * 关闭时，对话首条消息将回退使用当前聊天模型完成该任务。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MiscAiScreen(
    onBack: () -> Unit,
    viewModel: MiscAiViewModel = hiltViewModel(),
) {
    val s by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("杂项 AI", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
                },
                actions = {
                    IconButton(onClick = onBack) {
                        Text("完成", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 4.dp))
                    }
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
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("启用杂项 AI", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "用于新建对话时命名、分析意图，并生成身份与回答风格建议",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTheme.colors.secondary,
                    )
                }
                Switch(checked = s.enabled, onCheckedChange = viewModel::setEnabled)
            }

            Spacer(Modifier.padding(top = 18.dp))

            AppTextField(
                value = s.baseUrl,
                onValueChange = { viewModel.update("baseUrl", it) },
                label = "Base URL",
                placeholder = "https://your-api.example.com/v1",
                supportingText = "杂项 AI 的接口根地址（可不同于聊天模型）",
                modifier = Modifier.fillMaxWidth(),
            )
            AppTextField(
                value = s.model,
                onValueChange = { viewModel.update("model", it) },
                label = "模型名称",
                placeholder = "gpt-4o-mini",
                supportingText = "用于命名与意图分析的轻量模型更省资源",
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            )
            AppTextField(
                value = s.apiKey,
                onValueChange = { viewModel.update("apiKey", it) },
                label = "API Key",
                placeholder = "sk-...",
                isSecret = true,
                supportingText = "密钥仅保存在本机，加密存储",
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            )
            AppTextField(
                value = s.prompt,
                onValueChange = { viewModel.update("prompt", it) },
                label = "自定义提示词（可选）",
                placeholder = "你是一名资深对话分析助手…",
                supportingText = "留空则使用内置默认提示；始终要求返回纯 JSON",
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            )

            Row(Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 40.dp), horizontalArrangement = Arrangement.End) {
                AppButton(
                    text = "保存",
                    onClick = { viewModel.save(onBack) },
                    loading = s.saving,
                    style = ButtonStyle.PRIMARY,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}