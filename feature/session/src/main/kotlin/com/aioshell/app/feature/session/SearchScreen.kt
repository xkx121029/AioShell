package com.aioshell.app.feature.session

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aioshell.app.core.ui.components.AppCard
import com.aioshell.app.core.ui.components.AppCardContent
import com.aioshell.app.core.ui.components.EmptyState
import com.aioshell.app.core.ui.theme.AppRadius
import com.aioshell.app.core.ui.theme.AppSpacing
import com.aioshell.app.core.ui.theme.AppTheme

/** 对话全文搜索页：跨会话按关键词搜索消息，点击定位到对应会话与消息。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenResult: (sessionId: String, messageId: String?) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val c = AppTheme.colors

    Scaffold(
        containerColor = c.background,
        topBar = {
            // 顶部搜索框，无需单独 TopAppBar
            androidx.compose.material3.TopAppBar(
                title = {
                    OutlinedTextField(
                        value = ui.query,
                        onValueChange = viewModel::onQueryChange,
                        placeholder = { Text("搜索全部对话…", fontFamily = FontFamily.Default) },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(AppRadius.md),
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(inner)) {
            when {
                ui.searching && ui.query.isNotBlank() -> CircularProgressIndicator(
                    color = c.primary,
                    modifier = Modifier.align(Alignment.Center),
                )
                ui.query.isBlank() -> EmptyState(
                    icon = Icons.Filled.Search,
                    title = "输入关键词开始搜索",
                    description = "支持跨所有会话搜索消息正文",
                    modifier = Modifier.align(Alignment.Center),
                )
                ui.hits.isEmpty() -> EmptyState(
                    icon = Icons.Filled.Search,
                    title = "未找到相关消息",
                    description = "换个关键词试试",
                    modifier = Modifier.align(Alignment.Center),
                )
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = AppSpacing.md, vertical = AppSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                ) {
                    items(ui.hits, key = { it.messageId }) { hit ->
                        SearchHitCard(
                            hit = hit,
                            query = ui.query,
                            onClick = { onOpenResult(hit.sessionId, hit.messageId) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchHitCard(hit: SearchHit, query: String, onClick: () -> Unit) {
    val c = AppTheme.colors
    val snippet = summarize(hit.content)
    val roleName = if (hit.role == "user") "我" else "AI"

    AppCard(onClick = onClick) {
        AppCardContent {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = if (hit.role == "user") c.primary.copy(alpha = 0.12f) else c.secondary.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(AppRadius.sm),
                    ) {
                        Text(
                            roleName,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (hit.role == "user") c.primary else c.secondary,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    Text(
                        hit.sessionTitle,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 8.dp).weight(1f),
                    )
                    Text(
                        formatTime(hit.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = c.onSurfaceVariant,
                    )
                }
                Text(
                    snippet,
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

/** 摘要：命中关键词附近的文本片段，代码降级为 [代码]。 */
private fun summarize(content: String): String {
    if (content.contains("```")) return "[代码片段]"
    val compact = content.replace(Regex("\\s+"), " ").trim()
    return if (compact.length > 120) compact.take(120) + "…" else compact
}

private fun formatTime(millis: Long): String =
    runCatching {
        java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(millis))
    }.getOrDefault("")