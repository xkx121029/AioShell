package com.aioshell.app.feature.chat

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aioshell.app.core.data.repository.PromptTemplate
import com.aioshell.app.core.ui.components.AppButton
import com.aioshell.app.core.ui.components.AppCard
import com.aioshell.app.core.ui.components.AppCardContent
import com.aioshell.app.core.ui.components.AppDialog
import com.aioshell.app.core.ui.components.ButtonStyle
import com.aioshell.app.core.ui.components.EmptyState
import com.aioshell.app.core.ui.theme.AppRadius
import com.aioshell.app.core.ui.theme.AppSpacing
import com.aioshell.app.core.ui.theme.AppTheme

/**
 * 提示词模板库：内置模板 + 用户自定义模板。
 * 点击模板卡片「使用」→ 返回对话页并预填输入框；支持新增 / 编辑 / 删除。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplatesScreen(
    onBack: () -> Unit,
    onUseTemplate: (String) -> Unit,
    viewModel: TemplatesViewModel = hiltViewModel(),
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val c = AppTheme.colors
    var editing by remember { mutableStateOf<PromptTemplate?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<PromptTemplate?>(null) }

    Scaffold(
        containerColor = c.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("提示词模板", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("内置模板 · 可自定义", style = MaterialTheme.typography.labelSmall, color = c.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
                },
                actions = {
                    IconButton(onClick = { creating = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "新增模板", tint = c.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.background),
            )
        },
    ) { inner ->
        when {
            ui.loading -> Box(Modifier.fillMaxSize().padding(inner))
            ui.templates.isEmpty() -> Box(Modifier.fillMaxSize().padding(inner), contentAlignment = Alignment.Center) {
                EmptyState(icon = Icons.Filled.Notes, title = "暂无模板", description = "点击右上角新增一个提示词模板")
            }
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(inner),
                contentPadding = PaddingValues(horizontal = AppSpacing.md, vertical = AppSpacing.md),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
            ) {
                items(ui.templates, key = { it.id }) { t ->
                    TemplateCard(
                        template = t,
                        onUse = { onUseTemplate(t.content) },
                        onEdit = { editing = t },
                        onDelete = { if (!t.builtIn) deleteTarget = t },
                    )
                }
            }
        }
    }

    if (creating) {
        TemplateEditDialog(
            title = "",
            category = "",
            content = "",
            isNew = true,
            onConfirm = { title, category, content ->
                viewModel.add(title, content, category)
                creating = false
            },
            onDismiss = { creating = false },
        )
    }

    editing?.let { t ->
        TemplateEditDialog(
            title = t.title,
            category = t.category,
            content = t.content,
            isNew = false,
            onConfirm = { title, category, content ->
                viewModel.update(t.id, title, content, category)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }

    deleteTarget?.let { t ->
        AppDialog(
            title = "删除模板",
            message = "确定删除模板「${t.title}」吗？",
            confirmText = "删除",
            onConfirm = { viewModel.delete(t.id); deleteTarget = null },
            onDismiss = { deleteTarget = null },
        )
    }
}

@Composable
private fun TemplateCard(
    template: PromptTemplate,
    onUse: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val c = AppTheme.colors
    AppCard(onClick = onUse) {
        AppCardContent {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = if (template.builtIn) c.primary.copy(alpha = 0.12f) else c.secondary.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(AppRadius.sm),
                    ) {
                        Text(
                            template.category,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (template.builtIn) c.primary else c.secondary,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    Text(
                        if (template.builtIn) "内置" else "自定义",
                        style = MaterialTheme.typography.labelSmall,
                        color = c.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Outlined.Edit, "编辑", tint = c.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    }
                    if (!template.builtIn) {
                        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Outlined.DeleteOutline, "删除", tint = c.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                Text(
                    template.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    template.content,
                    style = MaterialTheme.typography.bodySmall,
                    color = c.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/** 新增 / 编辑模板的内嵌对话框。 */
@Composable
private fun TemplateEditDialog(
    title: String,
    category: String,
    content: String,
    isNew: Boolean,
    onConfirm: (title: String, category: String, content: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var titleText by remember { mutableStateOf(title) }
    var categoryText by remember { mutableStateOf(category) }
    var contentText by remember { mutableStateOf(content) }
    val c = AppTheme.colors

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(Modifier.fillMaxSize().background(Color.Transparent), contentAlignment = Alignment.Center) {
            Column(
                Modifier
                    .widthIn(max = 360.dp)
                    .background(c.surfaceVariant, RoundedCornerShape(28.dp))
                    .padding(24.dp),
            ) {
                Text(if (isNew) "新增模板" else "编辑模板", style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(
                    value = titleText,
                    onValueChange = { titleText = it },
                    label = { Text("名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
                OutlinedTextField(
                    value = categoryText,
                    onValueChange = { categoryText = it },
                    label = { Text("分类") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = contentText,
                    onValueChange = { contentText = it },
                    label = { Text("内容") },
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                Text(
                    "提示：内容中可包含 {{text}} 占位符，使用时替换为光标处选中文本",
                    style = MaterialTheme.typography.labelSmall,
                    color = c.secondary,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.End) {
                    AppButton(text = "取消", onClick = onDismiss, style = ButtonStyle.TEXT)
                    AppButton(
                        text = "确定",
                        onClick = { onConfirm(titleText, categoryText, contentText) },
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}