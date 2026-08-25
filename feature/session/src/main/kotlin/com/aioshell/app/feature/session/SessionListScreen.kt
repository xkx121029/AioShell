package com.aioshell.app.feature.session

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aioshell.app.core.ui.components.AioConfirmDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionListScreen(
    onOpenSession: (String) -> Unit,
    onGoConfig: () -> Unit,
    onSelectConfig: () -> Unit,
    onSelectIcon: () -> Unit,
    viewModel: SessionViewModel = hiltViewModel(),
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    var renameTarget by remember { mutableStateOf<SessionUiItem?>(null) }
    var deleteTarget by remember { mutableStateOf<SessionUiItem?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("AioShell", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.extraSmall,
                            modifier = Modifier.padding(start = 8.dp),
                        ) {
                            Text(
                                "本地 · 私有",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onSelectIcon) { Icon(Icons.Filled.Palette, contentDescription = "应用图标") }
                    IconButton(onClick = onSelectConfig) { Icon(Icons.Filled.Settings, contentDescription = "接口配置") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    val cfgId = ui.activeConfigId
                    if (cfgId != null) viewModel.createAndGetId(cfgId, onOpenSession)
                    else onGoConfig()
                },
            ) { Icon(Icons.Filled.Add, contentDescription = "新建会话") }
        },
    ) { inner ->
        when {
            ui.loading -> Unit
            !ui.hasConfig -> NoConfigHint(onGoConfig)
            ui.sessions.isEmpty() -> EmptySessionsHint()
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(inner),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(ui.sessions, key = { it.id }) { item ->
                    SessionCard(
                        item = item,
                        onClick = { onOpenSession(item.id) },
                        onRename = { renameTarget = item },
                        onDelete = { deleteTarget = item },
                    )
                }
            }
        }
    }

    renameTarget?.let { item ->
        RenameDialog(
            current = item.title,
            onConfirm = { title -> viewModel.rename(item.id, title); renameTarget = null },
            onDismiss = { renameTarget = null },
        )
    }

    deleteTarget?.let { item ->
        AioConfirmDialog(
            title = "删除会话",
            message = "确定删除「${item.title}」及其全部消息吗？该操作不可恢复。",
            confirmText = "删除",
            onConfirm = { viewModel.delete(item.id); deleteTarget = null },
            onDismiss = { deleteTarget = null },
        )
    }
}

@Composable
private fun NoConfigHint(onGoConfig: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Text("尚未配置接口", style = MaterialTheme.typography.titleLarge)
            Text(
                "AioShell 不内置任何模型，请先配置一个兼容 OpenAI 的接口",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            Button(onClick = onGoConfig, modifier = Modifier.padding(top = 20.dp)) { Text("去配置接口") }
        }
    }
}

@Composable
private fun EmptySessionsHint() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Text("尚无会话", style = MaterialTheme.typography.titleLarge)
            Text(
                "点击右下角按钮开始一段新的对话",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionCard(
    item: SessionUiItem,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = c.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    formatTime(item.updatedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = c.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            IconButton(onClick = onRename) { Icon(Icons.Outlined.Edit, "重命名", tint = c.onSurfaceVariant) }
            IconButton(onClick = onDelete) { Icon(Icons.Outlined.DeleteOutline, "删除", tint = c.onSurfaceVariant) }
        }
    }
}

@Composable
private fun RenameDialog(
    current: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(current) }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Transparent), contentAlignment = Alignment.Center) {
            Column(
                Modifier.widthIn(max = 320.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(28.dp))
                    .padding(24.dp),
            ) {
                Text("重命名会话", style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    label = { Text("名称") },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    shape = MaterialTheme.shapes.medium,
                )
                Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Button(onClick = { onConfirm(text.trim().ifBlank { "未命名" }) }) { Text("确定") }
                }
            }
        }
    }
}

private fun formatTime(millis: Long): String {
    return try {
        val d = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(millis))
        d
    } catch (e: Exception) { "" }
}