package com.aioshell.app.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aioshell.app.core.data.model.ChatMessage
import com.aioshell.app.core.data.model.MessageRole
import com.aioshell.app.core.data.model.MessageStatus
import com.aioshell.app.core.ui.util.copyTextToClipboard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onBack: () -> Unit,
    onGoConfig: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // 新消息/流式更新时自动滚到底部
    LaunchedEffect(ui.messages.size, ui.messages.lastOrNull()?.content?.length) {
        val count = ui.messages.size
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(ui.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                            maxLines = 1)
                        ui.configName?.let { name ->
                            Text(name, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    }
                },
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
                .padding(inner)
                .imePadding(),
        ) {
            if (ui.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (ui.messages.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        "开始一段新对话",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    items(ui.messages.size) { index ->
                        val msg = ui.messages[index]
                        MessageBubble(msg, onCopy = {
                            context.copyTextToClipboard("回复", msg.content)
                        })
                    }
                }
            }

            if (!ui.hasConfig) NoConfigBanner(onGoConfig)

            InputBar(
                value = input,
                onChange = { input = it },
                isStreaming = ui.isStreaming,
                canSend = ui.hasConfig,
                onSend = {
                    viewModel.send(input)
                    input = ""
                },
                onStop = viewModel::stopStreaming,
            )
        }
    }
}

@Composable
private fun MessageBubble(msg: ChatMessage, onCopy: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val isUser = msg.role == MessageRole.USER
    val isError = msg.role == MessageRole.ERROR
    val isAssistant = !isUser
    val isStreaming = isAssistant && msg.status == MessageStatus.SENDING

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = if (isUser) androidx.compose.foundation.layout.Arrangement.End else androidx.compose.foundation.layout.Arrangement.Start,
    ) {
        Box(
            Modifier
                .widthIn(max = 300.dp)
                .background(
                    color = when {
                        isUser -> c.primaryContainer
                        isError -> c.errorContainer
                        else -> c.surfaceContainerHigh
                    },
                    shape = RoundedCornerShape(
                        topStart = 20.dp,
                        topEnd = 20.dp,
                        bottomStart = if (isUser) 20.dp else 6.dp,
                        bottomEnd = if (isUser) 6.dp else 20.dp,
                    ),
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(
                buildString {
                    append(msg.content)
                    if (isStreaming) append("▌")
                },
                style = MaterialTheme.typography.bodyLarge,
                color = when {
                    isUser -> c.onPrimaryContainer
                    isError -> c.onErrorContainer
                    else -> c.onSurface
                },
            )
        }
    }

    // 复制按钮（仅助手 / 错误消息）
    if (isAssistant && !isStreaming && msg.content.isNotBlank()) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, bottom = 6.dp)) {
            IconButton(onClick = onCopy, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Filled.ContentCopy,
                    contentDescription = "复制",
                    tint = c.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun NoConfigBanner(onGoConfig: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "请先配置接口后发送消息",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onGoConfig) { Text("去配置") }
        }
    }
}

@Composable
private fun InputBar(
    value: String,
    onChange: (String) -> Unit,
    isStreaming: Boolean,
    canSend: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    Surface(
        color = c.surfaceContainerHigh,
        shape = MaterialTheme.shapes.large,
        tonalElevation = androidx.compose.ui.unit.Dp(2f),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            OutlinedTextField(
                value = value,
                onValueChange = onChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("输入消息…") },
                maxLines = 5,
                shape = MaterialTheme.shapes.large,
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
            )
            Spacer(Modifier.size(6.dp))
            if (isStreaming) {
                IconButton(onClick = onStop) { Icon(Icons.Filled.Stop, "停止", tint = c.error) }
            } else {
                Button(
                    onClick = onSend,
                    enabled = canSend && value.isNotBlank(),
                    modifier = Modifier.padding(bottom = 6.dp),
                    shape = RoundedCornerShape(50),
                ) { Icon(Icons.Filled.Send, "发送") }
            }
        }
    }
}