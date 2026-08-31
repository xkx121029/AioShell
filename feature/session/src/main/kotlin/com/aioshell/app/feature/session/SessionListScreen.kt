package com.aioshell.app.feature.session

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aioshell.app.core.data.export.ExportFormat
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
    onSearch: () -> Unit,
    onGoArchived: () -> Unit,
    onGoStats: () -> Unit,
    onGoAppearance: () -> Unit,
    viewModel: SessionViewModel = hiltViewModel(),
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    var renameTarget by remember { mutableStateOf<SessionUiItem?>(null) }
    var deleteTarget by remember { mutableStateOf<SessionUiItem?>(null) }
    // 多选批量管理
    var selectionMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var batchDeleteConfirm by remember { mutableStateOf(false) }
    var batchArchiveConfirm by remember { mutableStateOf(false) }
    var showExportMenu by remember { mutableStateOf(false) }
    // 顶栏更多菜单（AMOLED 开关 / 备份恢复）
    var showOverflow by remember { mutableStateOf(false) }
    // 顶栏接口快速切换
    var showConfigSwitch by remember { mutableStateOf(false) }
    // 备份 / 恢复
    var restoreConfirm by remember { mutableStateOf(false) }
    val contextForToast = LocalContext.current
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) viewModel.importBackup(uri)
    }
    LaunchedEffect(Unit) {
        viewModel.backupNotices.collect { notice ->
            Toast.makeText(contextForToast, notice.message, Toast.LENGTH_LONG).show()
        }
    }
    val c = AppTheme.colors

    fun exitSelection() {
        selectionMode = false
        selected = emptySet()
    }

    Scaffold(
        containerColor = c.background,
        topBar = {
            if (selectionMode) {
                // 批量管理模式顶栏
                TopAppBar(
                    title = {
                        Text(
                            "已选 ${selected.size} 项",
                            style = MaterialTheme.typography.titleMedium,
                            color = c.onSurface,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = ::exitSelection) {
                            Icon(Icons.Filled.Close, contentDescription = "退出批量管理", tint = c.onSurface)
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            selected = if (selected.size == ui.sessions.size) emptySet() else ui.sessions.map { it.id }.toSet()
                        }) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = "全选 / 取消全选",
                                tint = if (selected.size == ui.sessions.size) c.primary else c.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { showExportMenu = true }, enabled = selected.isNotEmpty()) {
                            Icon(
                                Icons.Filled.Share,
                                contentDescription = "批量导出",
                                tint = if (selected.isEmpty()) c.onSurfaceVariant.copy(alpha = 0.4f) else c.onSurface,
                            )
                        }
                        IconButton(onClick = { batchArchiveConfirm = true }, enabled = selected.isNotEmpty()) {
                            Icon(
                                Icons.Outlined.Archive,
                                contentDescription = "批量归档",
                                tint = if (selected.isEmpty()) c.onSurfaceVariant.copy(alpha = 0.4f) else c.onSurface,
                            )
                        }
                        IconButton(onClick = { batchDeleteConfirm = true }, enabled = selected.isNotEmpty()) {
                            Icon(
                                Icons.Filled.DeleteOutline,
                                contentDescription = "批量删除",
                                tint = if (selected.isEmpty()) c.onSurfaceVariant.copy(alpha = 0.4f) else c.error,
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = c.background),
                )
            } else {
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
                        IconButton(onClick = { selectionMode = true }) {
                            Icon(Icons.Outlined.SelectAll, contentDescription = "批量管理", tint = c.onSurface)
                        }
                        IconButton(onClick = onSearch) {
                            Icon(Icons.Filled.Search, contentDescription = "搜索对话", tint = c.onSurface)
                        }
                        IconButton(onClick = onGoArchived) {
                            Icon(Icons.Filled.Archive, contentDescription = "归档会话", tint = c.onSurface)
                        }
                        IconButton(onClick = onSelectIcon) {
                            Icon(Icons.Filled.Palette, contentDescription = "应用图标", tint = c.onSurface)
                        }
                        // 接口配置：点击弹出快速切换
                        Box {
                            IconButton(onClick = { showConfigSwitch = true }) {
                                Icon(Icons.Filled.Settings, contentDescription = "接口配置", tint = c.onSurface)
                            }
                            DropdownMenu(expanded = showConfigSwitch, onDismissRequest = { showConfigSwitch = false }) {
                                if (ui.configs.isEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("尚未配置接口") },
                                        onClick = { showConfigSwitch = false; onGoConfig() },
                                    )
                                } else {
                                    ui.configs.forEach { cfg ->
                                        DropdownMenuItem(
                                            text = {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(cfg.name, modifier = Modifier.weight(1f), maxLines = 1)
                                                    if (cfg.id == ui.activeConfigId) {
                                                        Icon(Icons.Filled.CheckCircle, contentDescription = "当前", tint = c.primary, modifier = Modifier.size(18.dp))
                                                    }
                                                }
                                            },
                                            onClick = {
                                                showConfigSwitch = false
                                                if (cfg.id != ui.activeConfigId) viewModel.setActiveConfig(cfg.id)
                                            },
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text("管理接口配置…") },
                                        onClick = { showConfigSwitch = false; onSelectConfig() },
                                    )
                                }
                            }
                        }
                        Box {
                            IconButton(onClick = { showOverflow = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "更多", tint = c.onSurface)
                            }
                            DropdownMenu(expanded = showOverflow, onDismissRequest = { showOverflow = false }) {
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("AMOLED 纯黑", modifier = Modifier.weight(1f))
                                            Switch(
                                                checked = ui.amoledPureBlack,
                                                onCheckedChange = { checked ->
                                                    viewModel.setAmoled(checked)
                                                    showOverflow = false
                                                },
                                            )
                                        }
                                    },
                                    onClick = {},
                                )
                                DropdownMenuItem(
                                    text = { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.Backup, null, tint = c.onSurface, modifier = Modifier.size(18.dp)); Spacer(Modifier.size(12.dp)); Text("备份数据") } },
                                    onClick = { showOverflow = false; viewModel.exportBackup() },
                                )
                                DropdownMenuItem(
                                    text = { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.Restore, null, tint = c.onSurface, modifier = Modifier.size(18.dp)); Spacer(Modifier.size(12.dp)); Text("恢复数据") } },
                                    onClick = { showOverflow = false; restoreConfirm = true },
                                )
                                DropdownMenuItem(
                                    text = { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.EmojiEvents, null, tint = c.onSurface, modifier = Modifier.size(18.dp)); Spacer(Modifier.size(12.dp)); Text("会话统计") } },
                                    onClick = { showOverflow = false; onGoStats() },
                                )
                                DropdownMenuItem(
                                    text = { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.Palette, null, tint = c.onSurface, modifier = Modifier.size(18.dp)); Spacer(Modifier.size(12.dp)); Text("界面自定义") } },
                                    onClick = { showOverflow = false; onGoAppearance() },
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = c.background),
                )
            }
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
            else -> {
                // 分组：置顶 / 今日 / 昨日 / 更早
                val pinned = ui.sessions.filter { it.pinned }
                val regular = ui.sessions.filterNot { it.pinned }
                val now = System.currentTimeMillis()
                val today = regular.filter { isSameDay(now, it.updatedAt) }
                val yesterday = regular.filterNot { isSameDay(now, it.updatedAt) }.filter { isSameDay(now - DAY_MS, it.updatedAt) }
                val earlier = regular.filterNot { isSameDay(now, it.updatedAt) }.filterNot { isSameDay(now - DAY_MS, it.updatedAt) }

                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(inner),
                    contentPadding = PaddingValues(horizontal = AppSpacing.md, vertical = AppSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                ) {
                    sessionGroup("置顶", pinned) { item ->
                        SessionListItem(
                            item = item,
                            selectionMode = selectionMode,
                            selected = item.id in selected,
                            onClick = {
                                if (selectionMode) {
                                    selected = if (item.id in selected) selected - item.id else selected + item.id
                                } else onOpenSession(item.id)
                            },
                            onPin = { viewModel.togglePinned(item.id) },
                            onArchive = { viewModel.archive(item.id) },
                            onRename = { renameTarget = item },
                            onDelete = { deleteTarget = item },
                        )
                    }
                    sessionGroup("今天", today) { item ->
                        SessionListItem(
                            item = item,
                            selectionMode = selectionMode,
                            selected = item.id in selected,
                            onClick = {
                                if (selectionMode) {
                                    selected = if (item.id in selected) selected - item.id else selected + item.id
                                } else onOpenSession(item.id)
                            },
                            onPin = { viewModel.togglePinned(item.id) },
                            onArchive = { viewModel.archive(item.id) },
                            onRename = { renameTarget = item },
                            onDelete = { deleteTarget = item },
                        )
                    }
                    sessionGroup("昨天", yesterday) { item ->
                        SessionListItem(
                            item = item,
                            selectionMode = selectionMode,
                            selected = item.id in selected,
                            onClick = {
                                if (selectionMode) {
                                    selected = if (item.id in selected) selected - item.id else selected + item.id
                                } else onOpenSession(item.id)
                            },
                            onPin = { viewModel.togglePinned(item.id) },
                            onArchive = { viewModel.archive(item.id) },
                            onRename = { renameTarget = item },
                            onDelete = { deleteTarget = item },
                        )
                    }
                    sessionGroup("更早", earlier) { item ->
                        SessionListItem(
                            item = item,
                            selectionMode = selectionMode,
                            selected = item.id in selected,
                            onClick = {
                                if (selectionMode) {
                                    selected = if (item.id in selected) selected - item.id else selected + item.id
                                } else onOpenSession(item.id)
                            },
                            onPin = { viewModel.togglePinned(item.id) },
                            onArchive = { viewModel.archive(item.id) },
                            onRename = { renameTarget = item },
                            onDelete = { deleteTarget = item },
                        )
                    }
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

    if (batchDeleteConfirm) {
        AppDialog(
            title = "批量删除",
            message = "确定删除选中的 ${selected.size} 个会话及其全部消息吗？该操作不可恢复。",
            confirmText = "删除",
            onConfirm = {
                viewModel.deleteMany(selected.toList())
                selected = emptySet()
                selectionMode = false
                batchDeleteConfirm = false
            },
            onDismiss = { batchDeleteConfirm = false },
        )
    }

    if (batchArchiveConfirm) {
        AppDialog(
            title = "批量归档",
            message = "将选中的 ${selected.size} 个会话移入归档（可从归档页恢复）。",
            confirmText = "归档",
            onConfirm = {
                viewModel.archiveMany(selected.toList())
                selected = emptySet()
                selectionMode = false
                batchArchiveConfirm = false
            },
            onDismiss = { batchArchiveConfirm = false },
        )
    }

    if (showExportMenu) {
        BatchExportMenu(
            count = selected.size,
            onSelect = { format ->
                showExportMenu = false
                viewModel.exportMany(selected.toList(), format)
            },
            onDismiss = { showExportMenu = false },
        )
    }

    if (restoreConfirm) {
        AppDialog(
            title = "恢复数据",
            message = "将从备份文件恢复接口配置与会话。相同 id 的会话会被覆盖，其余保留。确定继续吗？",
            confirmText = "选择备份文件",
            onConfirm = {
                restoreConfirm = false
                importLauncher.launch(arrayOf("application/json"))
            },
            onDismiss = { restoreConfirm = false },
        )
    }
}

/**
 * 按分组渲染会话列表：无会话的分组自动跳过。
 * @param header 分组标题（空字符串则不显示标题）
 */
private fun androidx.compose.foundation.lazy.LazyListScope.sessionGroup(
    header: String,
    items: List<SessionUiItem>,
    content: @Composable (SessionUiItem) -> Unit,
) {
    if (items.isEmpty()) return
    if (header.isNotBlank()) item(key = "header_$header") { GroupHeader(header) }
    items(items, key = { "${header}_${it.id}" }) { item -> content(item) }
}

/** 分组标题。 */
@Composable
private fun GroupHeader(title: String) {
    val c = AppTheme.colors
    Box(Modifier.padding(top = 6.dp, bottom = 2.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            color = c.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** 是否同一天（用于"今天 / 昨天"分组）。 */
private fun isSameDay(reference: Long, time: Long): Boolean {
    val days = java.util.concurrent.TimeUnit.MILLISECONDS.toDays(reference)
    val daysT = java.util.concurrent.TimeUnit.MILLISECONDS.toDays(time)
    return days == daysT
}

private const val DAY_MS = 24L * 60L * 60L * 1000L

@Composable
private fun themeIcon(mode: ThemeMode): androidx.compose.ui.graphics.vector.ImageVector = when (mode) {
    ThemeMode.LIGHT -> Icons.Filled.LightMode
    ThemeMode.DARK -> Icons.Filled.DarkMode
    ThemeMode.SYSTEM -> Icons.Filled.BrightnessAuto
}

@Composable
private fun SessionListItem(
    item: SessionUiItem,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onPin: () -> Unit,
    onArchive: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val c = AppTheme.colors
    AppCard(onClick = onClick) {
        AppCardContent {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selectionMode) {
                    Checkbox(checked = selected, onCheckedChange = { onClick() })
                    Spacer(Modifier.size(8.dp))
                }
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (item.pinned) {
                            Icon(
                                Icons.Filled.PushPin,
                                contentDescription = "已置顶",
                                tint = c.primary,
                                modifier = Modifier.size(14.dp).padding(end = 4.dp),
                            )
                        }
                        Text(
                            item.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
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
                if (!selectionMode) {
                    IconButton(onClick = onPin) {
                        Icon(
                            if (item.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            contentDescription = if (item.pinned) "取消置顶" else "置顶",
                            tint = if (item.pinned) c.primary else c.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onArchive) {
                        Icon(Icons.Outlined.Archive, contentDescription = "归档", tint = c.onSurfaceVariant)
                    }
                    IconButton(onClick = onRename) {
                        Icon(Icons.Outlined.Edit, contentDescription = "重命名", tint = c.onSurfaceVariant)
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Filled.DeleteOutline, contentDescription = "删除", tint = c.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** 批量导出格式选择菜单。 */
@Composable
private fun BatchExportMenu(
    count: Int,
    onSelect: (ExportFormat) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = AppTheme.colors
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier.fillMaxSize().background(Color.Transparent).padding(AppSpacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom,
        ) {
            Column(
                Modifier
                    .widthIn(max = 360.dp)
                    .background(c.surfaceVariant, RoundedCornerShape(24.dp))
                    .padding(vertical = AppSpacing.sm),
            ) {
                Text(
                    "批量导出 $count 个会话",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = c.onSurface,
                    modifier = Modifier.padding(horizontal = AppSpacing.lg, vertical = AppSpacing.sm),
                )
                ActionItem(Icons.Outlined.PushPin, "Markdown", { onSelect(ExportFormat.MARKDOWN) })
                ActionItem(Icons.Filled.Share, "纯文本", { onSelect(ExportFormat.TEXT) })
                ActionItem(Icons.Filled.MoreVert, "JSON", { onSelect(ExportFormat.JSON) })
                ActionItem(null, "取消", onDismiss, tint = c.secondary)
            }
        }
    }
}

@Composable
private fun ActionItem(icon: androidx.compose.ui.graphics.vector.ImageVector?, label: String, onClick: () -> Unit, tint: Color? = null) {
    val c = AppTheme.colors
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = AppSpacing.lg, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = tint ?: c.onSurface, modifier = Modifier.size(22.dp))
            Spacer(Modifier.size(14.dp))
        }
        Text(label, style = MaterialTheme.typography.bodyLarge, color = tint ?: c.onSurface)
    }
}

@Composable
private fun RenameDialog(
    current: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(current) }
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
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
