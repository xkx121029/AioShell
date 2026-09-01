package com.aioshell.app.feature.session

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aioshell.app.core.data.security.SecurityCrypto
import com.aioshell.app.core.data.store.SettingsStore
import com.aioshell.app.core.ui.theme.AppSpacing
import com.aioshell.app.core.ui.theme.AppTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

/** 界面自定义项：强调色 + 聊天字号 + 行距 + Token 单价 + 应用锁。 */
data class AppearanceUi(
    val accent: String? = null,
    val fontSize: Float = 16f,
    val lineSpacing: Float = 0f,
    val tokenInputPrice: Float = 0f,
    val tokenOutputPrice: Float = 0f,
    val lockEnabled: Boolean = false,
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
        combine(
            settings.accentColor,
            settings.chatFontSize,
            settings.chatLineSpacing,
        ) { accent, size, spacing ->
            AppearanceUi(accent = accent, fontSize = size, lineSpacing = spacing)
        },
        combine(
            settings.tokenInputPrice,
            settings.tokenOutputPrice,
            settings.lockEnabled,
        ) { inPrice, outPrice, locked ->
            Triple(inPrice, outPrice, locked)
        },
    ) { a, (inPrice, outPrice, locked) ->
        a.copy(tokenInputPrice = inPrice, tokenOutputPrice = outPrice, lockEnabled = locked)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppearanceUi())

    fun setAccent(hex: String?) = viewModelScope.launch { settings.setAccentColor(hex) }
    fun setFontSize(sp: Float) = viewModelScope.launch { settings.setChatFontSize(sp) }
    fun setLineSpacing(sp: Float) = viewModelScope.launch { settings.setChatLineSpacing(sp) }
    fun setTokenInputPrice(v: Float) = viewModelScope.launch { settings.setTokenInputPrice(v) }
    fun setTokenOutputPrice(v: Float) = viewModelScope.launch { settings.setTokenOutputPrice(v) }

    /** 校验 PIN（用于解绑/重置）。返回是否匹配。 */
    suspend fun verifyPin(pin: String): Boolean {
        val hash = settings.lockPinHash.first()
        return hash == SecurityCrypto.sha256Hex(pin)
    }

    /** 设置（或修改）PIN 并开启应用锁。 */
    fun enableLock(pin: String) = viewModelScope.launch {
        settings.setLockPinHash(SecurityCrypto.sha256Hex(pin))
        settings.setLockEnabled(true)
    }

    /** 关闭应用锁（清空 PIN）。 */
    fun disableLock() = viewModelScope.launch {
        settings.setLockPinHash(null)
        settings.setLockEnabled(false)
    }
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

            TokenPriceSection(ui, viewModel)

            AppLockSection(ui, viewModel)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TokenPriceSection(ui: AppearanceUi, viewModel: AppearanceViewModel) {
    val c = AppTheme.colors
    Section("Token 单价（美元 / 每百万 token）")
    SelectionBox {
        PriceField("输入价格", ui.tokenInputPrice) { viewModel.setTokenInputPrice(it) }
        Spacer(Modifier.height(AppSpacing.md))
        PriceField("输出价格", ui.tokenOutputPrice) { viewModel.setTokenOutputPrice(it) }
        Text(
            "用于在『会话统计』中估算累计费用，0 表示未设定。",
            style = MaterialTheme.typography.labelSmall,
            color = c.onSurfaceVariant,
            modifier = Modifier.padding(top = AppSpacing.sm),
        )
    }
}

@Composable
private fun PriceField(label: String, value: Float, onChange: (Float) -> Unit) {
    val c = AppTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = c.onSurface, modifier = Modifier.weight(1f))
        androidx.compose.material3.OutlinedTextField(
            value = if (value == 0f) "" else value.toString(),
            onValueChange = { onChange(it.toFloatOrNull() ?: 0f) },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = c.onSurface),
            modifier = Modifier.width(120.dp),
        )
    }
}

@Composable
private fun AppLockSection(ui: AppearanceUi, viewModel: AppearanceViewModel) {
    val c = AppTheme.colors
    var showDialog by remember { mutableStateOf(false) }
    Section("应用锁（PIN）")
    SelectionBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (ui.lockEnabled) "已开启：启动时需输入密码" else "未开启",
                style = MaterialTheme.typography.bodyMedium,
                color = c.onSurface,
                modifier = Modifier.weight(1f),
            )
            AddressButton(
                text = if (ui.lockEnabled) "修改 / 取消" else "去设置",
                onClick = { showDialog = true },
            )
        }
    }
    if (showDialog) {
        PinDialog(
            isSetup = !ui.lockEnabled,
            viewModel = viewModel,
            onDismiss = { showDialog = false },
        )
    }
}

/** PIN 设置对话框：首设（两次输入）或已有（校验后修改/关闭）。 */
@Composable
private fun PinDialog(
    isSetup: Boolean,
    viewModel: AppearanceViewModel,
    onDismiss: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    // 阶段：verify 校验旧密码 → ask 校验后询问操作 → set 录入新密码
    var step by remember { mutableStateOf(if (isSetup) "set" else "verify") }
    val c = AppTheme.colors

    val scope = androidx.compose.runtime.rememberCoroutineScope()

    when (step) {
        "verify" -> androidx.compose.material3.AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("验证当前密码") },
            text = {
                Column {
                    androidx.compose.material3.OutlinedTextField(
                        value = pin,
                        onValueChange = { pin = it.filter { ch -> ch.isDigit() }.take(6); error = null },
                        label = { Text("当前 PIN") },
                        singleLine = true,
                        isError = error != null,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword,
                        ),
                    )
                    if (error != null) Text(error!!, color = c.error, style = MaterialTheme.typography.labelSmall)
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    scope.launch {
                        if (pin.length < 4) { error = "PIN 至少 4 位"; return@launch }
                        if (viewModel.verifyPin(pin)) {
                            error = null
                            pin = ""
                            step = "ask"
                        } else {
                            error = "密码错误"
                        }
                    }
                }) { Text("确定") }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("取消") } },
        )

        "ask" -> androidx.compose.material3.AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("应用锁已开启") },
            text = { Text("密码验证通过。是否关闭应用锁？") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { viewModel.disableLock(); onDismiss() }) {
                    Text("关闭锁")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { step = "set"; pin = ""; confirmPin = "" }) {
                    Text("修改密码")
                }
            },
        )

        "set" -> androidx.compose.material3.AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(if (isSetup) "设置 PIN" else "修改 PIN") },
            text = {
                Column {
                    androidx.compose.material3.OutlinedTextField(
                        value = pin,
                        onValueChange = { pin = it.filter { ch -> ch.isDigit() }.take(6); error = null },
                        label = { Text("6 位数字 PIN") },
                        singleLine = true,
                        isError = error != null,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword,
                        ),
                    )
                    if (!isSetup) {
                        Spacer(Modifier.height(AppSpacing.sm))
                        androidx.compose.material3.OutlinedTextField(
                            value = confirmPin,
                            onValueChange = { confirmPin = it.filter { ch -> ch.isDigit() }.take(6) },
                            label = { Text("确认新 PIN") },
                            singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword,
                            ),
                        )
                    }
                    if (error != null) Text(error!!, color = c.error, style = MaterialTheme.typography.labelSmall)
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    if (pin.length < 4) { error = "PIN 至少 4 位"; return@TextButton }
                    if (!isSetup && confirmPin != pin) { error = "两次输入不一致"; return@TextButton }
                    viewModel.enableLock(pin); onDismiss()
                }) { Text("保存") }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("取消") } },
        )
    }
}

@Composable
private fun AddressButton(text: String, onClick: () -> Unit) {
    val c = AppTheme.colors
    androidx.compose.material3.TextButton(onClick = onClick) {
        Text(text, color = c.primary, fontWeight = FontWeight.SemiBold)
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