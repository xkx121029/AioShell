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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aioshell.app.core.data.model.ThemeMode
import com.aioshell.app.core.data.model.label
import com.aioshell.app.core.ui.components.AppButton
import com.aioshell.app.core.ui.components.AppCard
import com.aioshell.app.core.ui.components.AppCardContent
import com.aioshell.app.core.ui.components.AppDialog
import com.aioshell.app.core.ui.components.ButtonStyle
import com.aioshell.app.core.ui.components.EmptyState
import com.aioshell.app.core.ui.components.LoadingState
import com.aioshell.app.core.ui.theme.AppRadius
import com.aioshell.app.core.ui.theme.AppSpacing
import com.aioshell.app.core.ui.theme.AppTheme

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
    val c = AppTheme.colors

    Scaffold(
        containerColor = c.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "AioShell",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = c.onBackground,
                        )
                        Surface(
                            color = c.primary.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(AppRadius.sm),
                            modifier = Modifier.padding(start = 8.dp),
                        ) {
                            Text(
                                "本地 · 私有",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = c.primary,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::cycleTheme) {
                        Icon(themeIcon(ui.themeMode), contentDescription = ui.themeMode.label, tint = c.onSurface)
                    }
                    IconButton(onClick = onSelectIcon) {
                        Icon(Icons.Filled.Palette, contentDescription = "应用图标", tint = c.onSurface)
                    }
                    IconButton(onClick = onSelectConfig) {
                        Icon(Icons.Filled.Settings, contentDescription = "接口配置", tint = c.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.background),
            )
        },
        floatingActionButton = {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(c.primary)
                    .clickable {
                        val cfgId = ui.activeConfigId
                        if (cfgId != null) viewModel.createAndGetId(cfgId, onOpenSession)
                        else onGoConfig()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = "新建会话",
                    tint = c.onPrimary,
                    modifier = Modifier.size(28.dp),
                )
            }
        },
    ) { inner ->
        when {
            ui.loading -> LoadingState(modifier = Modifier.padding(inner))
            !ui.hasConfig -> Box(Modifier.fillMaxSize().padding(inner), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Filled.Settings,
                    title = "尚未配置接口",
                    description = "AioShell 不内置任何模型，请先配置一个兼容 OpenAI 的接口",
                    actionText = "去配置接口",
                    onAction = onGoConfig,
                )
            }
            ui.sessions.isEmpty() -> Box(Modifier.fillMaxSize().padding(inner), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Filled.ChatBubbleOutline,
                    title = "尚无会话",
                    description = "点击右下角按钮开始一段新的对话",
                )
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(inner),
                contentPadding = PaddingValues(horizontal = AppSpacing.md, vertical = AppSpacing.md),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
            ) {
                items(ui.sessions, key = { it.id }) { item ->
                    SessionListItem(
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
        AppDialog(
            title = "删除会话",
            message = "确定删除「${item.title}」及其全部消息吗？该操作不可恢复。",
            confirmText = "删除",
            onConfirm = { viewModel.delete(item.id); deleteTarget = null },
            onDismiss = { deleteTarget = null },
        )
    }
}

@Composable
private fun themeIcon(mode: ThemeMode): androidx.compose.ui.graphics.vector.ImageVector = when (mode) {
    ThemeMode.LIGHT -> Icons.Filled.LightMode
    ThemeMode.DARK -> Icons.Filled.DarkMode
    ThemeMode.SYSTEM -> Icons.Filled.BrightnessAuto
}

@Composable
private fun SessionListItem(
    item: SessionUiItem,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val c = AppTheme.colors
    AppCard(onClick = onClick) {
        AppCardContent {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        item.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        item.preview.ifBlank { "（暂无消息）" },
                        style = MaterialTheme.typography.bodySmall,
                        color = c.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Text(
                        formatTime(item.updatedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = c.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                IconButton(onClick = onRename) { Icon(Icons.Outlined.Edit, "重命名", tint = c.onSurfaceVariant) }
                IconButton(onClick = onDelete) { Icon(Icons.Outlined.DeleteOutline, "删除", tint = c.onSurfaceVariant) }
            }
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
        Box(Modifier.fillMaxSize().background(Color.Transparent), contentAlignment = Alignment.Center) {
            Column(
                Modifier.widthIn(max = 320.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(28.dp))
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
                    AppButton(text = "取消", onClick = onDismiss, style = ButtonStyle.TEXT)
                    AppButton(text = "确定", onClick = { onConfirm(text.trim().ifBlank { "未命名" }) }, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

private fun formatTime(millis: Long): String =
    runCatching {
        java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(millis))
    }.getOrDefault("")
