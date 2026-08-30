package com.aioshell.app.feature.config

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aioshell.app.core.data.model.ChatConfig
import com.aioshell.app.core.ui.components.AppDialog
import com.aioshell.app.core.ui.components.EmptyState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigListScreen(
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    onMiscAi: () -> Unit,
    viewModel: ConfigListViewModel = hiltViewModel(),
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<ChatConfig?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("接口配置", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "返回") }
                },
                actions = {
                    IconButton(onClick = onMiscAi) {
                        Icon(Icons.Filled.Settings, contentDescription = "杂项 AI")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) { Icon(Icons.Filled.Add, contentDescription = "新增") }
        },
    ) { inner ->
        if (ui.configs.isEmpty()) {
            EmptyConfigHint(onAdd)
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(ui.configs, key = { it.id }) { cfg ->
                ConfigCard(
                    config = cfg,
                    isActive = cfg.id == ui.activeId,
                    onSelect = { viewModel.setActive(cfg.id) },
                    onEdit = { onEdit(cfg.id) },
                    onDelete = { pendingDelete = cfg },
                )
            }
        }
    }

    pendingDelete?.let { cfg ->
        AppDialog(
            title = "删除配置",
            message = "确定删除「${cfg.name}」吗？该操作不可恢复。",
            onConfirm = {
                viewModel.delete(cfg.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

@Composable
private fun EmptyConfigHint(onAdd: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Text("还没有接口配置", style = MaterialTheme.typography.titleLarge)
            Text(
                "配置一个兼容 OpenAI 协议的接口，即可开始对话",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            androidx.compose.material3.Button(
                onClick = onAdd,
                modifier = Modifier.padding(top = 20.dp),
            ) { Text("新增配置") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfigCard(
    config: ChatConfig,
    isActive: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    Card(
        onClick = onSelect,
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) c.primaryContainer else c.surfaceContainerHigh,
            contentColor = if (isActive) c.onPrimaryContainer else c.onSurface,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    config.name.ifBlank { config.model },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, "编辑") }
                IconButton(onClick = onDelete) { Icon(Icons.Outlined.DeleteOutline, "删除") }
            }
            Text(
                config.baseUrl,
                style = MaterialTheme.typography.bodySmall,
                color = c.onSurfaceVariant.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(Modifier.padding(top = 6.dp)) {
                Text(
                    "模型：${config.model}",
                    style = MaterialTheme.typography.labelMedium,
                    color = c.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                if (isActive) {
                    Surface(
                        color = c.primary,
                        shape = MaterialTheme.shapes.extraSmall,
                    ) {
                        Text(
                            "当前",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = c.onPrimary,
                        )
                    }
                }
            }
        }
    }
}