package com.aioshell.app.feature.session

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.SendToMobile
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aioshell.app.core.data.repository.SessionRepository
import com.aioshell.app.core.ui.components.LoadingState
import com.aioshell.app.core.ui.theme.AppSpacing
import com.aioshell.app.core.ui.theme.AppTheme
import java.util.Locale

/** 会话统计页：会话数 / 消息数 / 字数 / 用量估算。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onBack: () -> Unit,
    viewModel: SessionViewModel = hiltViewModel(),
) {
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val c = AppTheme.colors
    LaunchedEffect(Unit) { viewModel.loadStats() }

    Scaffold(
        containerColor = c.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "会话统计",
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
        val s = stats
        if (s == null) {
            LoadingState(modifier = Modifier.padding(inner))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(inner),
                contentPadding = PaddingValues(horizontal = AppSpacing.md, vertical = AppSpacing.md),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
            ) {
                item(key = "breakdown") {
                    StatRow(Icons.Filled.EmojiEvents, "会话总数", s.sessions.toString())
                }
                itemsIndexed(statItems(s)) { index, item ->
                    StatRow(item.icon, item.label, item.value)
                }
                item(key = "note") {
                    Text(
                        "Token 为按字符粗略估算的参考值，便于了解大致用量，非精确计费。",
                        style = MaterialTheme.typography.labelSmall,
                        color = c.onSurfaceVariant,
                        modifier = Modifier.padding(top = AppSpacing.sm),
                    )
                }
            }
        }
    }
}

private data class StatRowItem(val icon: ImageVector, val label: String, val value: String)

private fun statItems(s: SessionRepository.SessionStats): List<StatRowItem> = listOf(
    StatRowItem(Icons.Filled.Forum, "消息总数", s.messages.toString()),
    StatRowItem(Icons.Filled.SendToMobile, "我发出的消息", s.userMessages.toString()),
    StatRowItem(Icons.Filled.SmartToy, "AI 回复消息", s.aiMessages.toString()),
    StatRowItem(Icons.Filled.Edit, "总字符数", String.format(Locale.getDefault(), "%,d", s.totalChars)),
    StatRowItem(Icons.Filled.Speed, "累计 Token（估）", String.format(Locale.getDefault(), "%,d", s.estimatedTokens)),
)

@Composable
private fun StatRow(icon: ImageVector, label: String, value: String) {
    val c = AppTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = AppSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = c.primary, modifier = Modifier.width(28.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = c.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = c.onSurface,
        )
    }
}