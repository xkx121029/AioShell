package com.aioshell.app.feature.session

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.outlined.PushPin
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
import com.aioshell.app.core.ui.components.AppCard
import com.aioshell.app.core.ui.components.AppCardContent
import com.aioshell.app.core.ui.components.AppDialog
import com.aioshell.app.core.ui.components.EmptyState
import com.aioshell.app.core.ui.components.LoadingState
import com.aioshell.app.core.ui.theme.AppSpacing
import com.aioshell.app.core.ui.theme.AppTheme

/**
 * 归档会话页：查看已归档的会话，可恢复回主列表或彻底删除。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchivedScreen(
    onBack: () -> Unit,
    onOpenSession: (String) -> Unit,
    viewModel: SessionViewModel = hiltViewModel(),
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val c = AppTheme.colors
    var deleteTarget by remember { mutableStateOf<SessionUiItem?>(null) }

    Scaffold(
        containerColor = c.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "归档会话（${ui.archived.size}）",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = c.onSurface,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = c.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.background),
            )
        },
    ) { inner ->
        when {
            ui.loading -> LoadingState(modifier = Modifier.padding(inner))
            ui.archived.isEmpty() -> Box(Modifier.fillMaxSize().padding(inner), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Filled.Archive,
                    title = "暂无归档会话",
                    description = "在主列表归档的会话会显示在这里，可随时恢复",
                )
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(inner),
                contentPadding = PaddingValues(horizontal = AppSpacing.md, vertical = AppSpacing.md),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
            ) {
                items(ui.archived, key = { it.id }) { item ->
                    AppCard(onClick = { onOpenSession(item.id) }) {
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
                                        "归档于 ${formatArchivedTime(item.updatedAt)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = c.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 4.dp),
                                    )
                                }
                                IconButton(onClick = { viewModel.restore(item.id) }) {
                                    Icon(
                                        Icons.Filled.Unarchive,
                                        contentDescription = "恢复",
                                        tint = c.primary,
                                    )
                                }
                                IconButton(onClick = { deleteTarget = item }) {
                                    Icon(
                                        Icons.Filled.DeleteOutline,
                                        contentDescription = "删除",
                                        tint = c.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    deleteTarget?.let { item ->
        AppDialog(
            title = "删除归档会话",
            message = "确定彻底删除「${item.title}」及其全部消息吗？该操作不可恢复。",
            confirmText = "删除",
            onConfirm = { viewModel.delete(item.id); deleteTarget = null },
            onDismiss = { deleteTarget = null },
        )
    }
}

private fun formatArchivedTime(millis: Long): String =
    runCatching {
        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(millis))
    }.getOrDefault("")
