package com.aioshell.app.feature.session

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aioshell.app.core.data.store.SettingsStore
import com.aioshell.app.core.ui.theme.AppSpacing
import com.aioshell.app.core.ui.theme.AppTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

/** 界面自定义项：强调色 + 聊天字号 + 行距。 */
data class AppearanceUi(
    val accent: String? = null,
    val fontSize: Float = 16f,
    val lineSpacing: Float = 0f,
)

/** 预设强调色（十六进制），覆盖深浅两种主题下都能辨识的常见主色。 */
private val PRESET_ACCENTS = listOf(
    "#0B6B60", // 海玻璃绿（默认）
    "#2F6FED", // 蓝
    "#8A4AF3", // 紫
    "#E85D75", // 玫红
    "#F59E0B", // 琥珀
    "#10B981", // 翠绿
)

@HiltViewModel
class AppearanceViewModel @Inject constructor(
    private val settings: SettingsStore,
) : ViewModel() {
    val ui: StateFlow<AppearanceUi> = combine(
        settings.accentColor,
        settings.chatFontSize,
        settings.chatLineSpacing,
    ) { accent, size, spacing ->
        AppearanceUi(accent = accent, fontSize = size, lineSpacing = spacing)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppearanceUi())

    fun setAccent(hex: String?) = viewModelScope.launch { settings.setAccentColor(hex) }
    fun setFontSize(sp: Float) = viewModelScope.launch { settings.setChatFontSize(sp) }
    fun setLineSpacing(sp: Float) = viewModelScope.launch { settings.setChatLineSpacing(sp) }
}

/**
 * 界面自定义页：自定义强调色、聊天正文字号与行距。
 * 修改即时生效，写入偏好存储。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceScreen(
    onBack: () -> Unit,
    viewModel: AppearanceViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val c = AppTheme.colors

    Scaffold(
        containerColor = c.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "界面自定义",
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
                actions = {
                    IconButton(onClick = {
                        viewModel.setAccent(null)
                        viewModel.setFontSize(16f)
                        viewModel.setLineSpacing(0f)
                    }) {
                        Icon(
                            Icons.Filled.RestartAlt,
                            contentDescription = "恢复默认",
                            tint = if (ui.accent == null && ui.fontSize == 16f && ui.lineSpacing == 0f) {
                                c.onSurfaceVariant.copy(alpha = 0.4f)
                            } else c.onSurface,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.background),
            )
        },
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppSpacing.md, vertical = AppSpacing.md),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.lg),
        ) {
            Section("强调色")
            SelectionBox {
                Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
                    PRESET_ACCENTS.forEach { hex ->
                        val color = parseHex(hex)
                        val selected = ui.accent == hex
                        Box(
                            Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (selected) 3.dp else 1.dp,
                                    color = if (selected) c.onSurface else Color.White.copy(alpha = 0.35f),
                                    shape = CircleShape,
                                )
                                .clickable { viewModel.setAccent(hex) },
                        )
                    }
                }
                Text(
                    "留空下方按钮即可恢复默认主题色",
                    style = MaterialTheme.typography.labelSmall,
                    color = c.onSurfaceVariant,
                    modifier = Modifier.padding(top = AppSpacing.md),
                )
            }

            Section("聊天字号")
            SelectionBox {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("小", color = c.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(end = 8.dp))
                    Slider(
                        value = ui.fontSize,
                        onValueChange = viewModel::setFontSize,
                        valueRange = 13f..20f,
                        modifier = Modifier.weight(1f),
                    )
                    Text("大", color = c.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp))
                }
                Text(
                    "${ui.fontSize.toInt()} sp",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = c.primary,
                )
            }

            Section("行距")
            SelectionBox {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("紧凑", color = c.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(end = 8.dp))
                    Slider(
                        value = ui.lineSpacing,
                        onValueChange = viewModel::setLineSpacing,
                        valueRange = 0f..10f,
                        modifier = Modifier.weight(1f),
                    )
                    Text("宽松", color = c.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp))
                }
                Text(
                    "${ui.lineSpacing.toInt()} sp",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = c.primary,
                )
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    val c = AppTheme.colors
    Text(
        title,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = c.onSurfaceVariant,
    )
}

@Composable
private fun SelectionBox(content: @Composable () -> Unit) {
    val c = AppTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(c.surfaceVariant)
            .padding(AppSpacing.lg),
    ) {
        content()
    }
}

/** 解析 "#RRGGBB" 强调色。 */
private fun parseHex(hex: String): Color {
    val clean = hex.removePrefix("#")
    return runCatching { Color((0xFF000000.toInt() or clean.toInt(16))) }
        .getOrDefault(Color(0xFF0B6B60))
}