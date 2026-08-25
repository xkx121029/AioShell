package com.aioshell.app.feature.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.aioshell.app.core.data.model.ChatMessage
import com.aioshell.app.core.data.model.MessageAttachment
import com.aioshell.app.core.data.model.MessageRole
import com.aioshell.app.core.data.model.MessageStatus
import com.aioshell.app.core.ui.components.AppButton
import com.aioshell.app.core.ui.components.ButtonStyle
import com.aioshell.app.core.ui.components.EmptyState
import com.aioshell.app.core.ui.components.MessageBubble
import com.aioshell.app.core.ui.theme.AppSpacing
import com.aioshell.app.core.ui.theme.AppTheme
import com.aioshell.app.core.ui.util.copyTextToClipboard
import com.aioshell.app.core.ui.components.ErrorState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    onBack: () -> Unit,
    onGoConfig: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var input by rememberSaveable { mutableStateOf("") }
    var actionTarget by remember { mutableStateOf<ChatMessage?>(null) }
    var viewerPath by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val pickImages = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 4),
    ) { uris -> viewModel.onImagesPicked(uris) }

    fun launchPhotoPicker() {
        pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    // 是否停留在底部（用于智能跟随与"回到底部"按钮）
    val isAtBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount > 0 && lastVisible >= info.totalItemsCount - 1
        }
    }

    // 智能跟随：仅在用户停留底部时自动滚动，上翻历史不被强制拉回
    LaunchedEffect(ui.messages.size, ui.messages.lastOrNull()?.content?.length) {
        if (ui.isStreaming && !isAtBottom) return@LaunchedEffect
        val count = ui.messages.size
        if (count > 0 && isAtBottom) listState.animateScrollToItem(count - 1)
    }

    fun scrollToBottom() {
        val count = ui.messages.size
        if (count > 0) scope.launch { listState.animateScrollToItem(count - 1) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(ui.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        ui.configName?.let { name ->
                            Text(name, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
                },
                actions = {
                    // 思考模式快捷开关（模型级）
                    if (ui.hasConfig) {
                        IconButton(onClick = viewModel::toggleReasoning) {
                            Icon(
                                Icons.Outlined.Psychology,
                                contentDescription = if (ui.reasoningEnabled) "关闭思考模式" else "开启思考模式",
                                tint = if (ui.reasoningEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { inner ->
        Column(
            Modifier.fillMaxSize().padding(inner).imePadding(),
        ) {
            when {
                ui.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AppTheme.colors.primary)
                }
                ui.messages.isEmpty() && ui.hasConfig -> EmptyState(
                    icon = Icons.Filled.Send,
                    title = "开始一段新对话",
                    modifier = Modifier.weight(1f),
                )
                else -> Box(Modifier.weight(1f).fillMaxWidth()) {
                    // 流式生成动态状态：告知读屏用户
                    if (ui.isStreaming) {
                        Text(
                            "正在生成回复…",
                            style = MaterialTheme.typography.labelSmall,
                            color = AppTheme.colors.secondary,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 4.dp)
                                .semantics {
                                    liveRegion = LiveRegionMode.Polite
                                    this.contentDescription = "AI 正在生成回复"
                                },
                        )
                    }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        items(ui.messages, key = { it.id }) { msg ->
                            // AI 消息思考面板（仅当模型开启且存在思考内容）
                            if (msg.role == MessageRole.ASSISTANT) {
                                val rc = msg.reasoningContent
                                if (rc != null && rc.isNotBlank() && ui.reasoningEnabled) {
                                    var expanded by remember(msg.id) { mutableStateOf(false) }
                                    ReasoningPanel(
                                        reasoning = rc,
                                        isStreaming = msg.status == MessageStatus.SENDING,
                                        durationMs = msg.reasoningDurationMs?.takeIf { it > 0 },
                                        expanded = expanded,
                                        onToggle = { expanded = !expanded },
                                        modifier = Modifier.padding(bottom = 4.dp),
                                    )
                                }
                            }
                            // 用户消息附件（图片回显）
                            if (msg.role == MessageRole.USER && msg.attachments.isNotEmpty()) {
                                AttachmentRow(msg.attachments, onOpen = { viewerPath = it })
                            }
                            MessageBubble(
                                content = msg.content,
                                isUser = msg.role == MessageRole.USER,
                                isError = msg.role == MessageRole.ERROR,
                                isStreaming = msg.role == MessageRole.ASSISTANT && msg.status == MessageStatus.SENDING,
                                modifier = Modifier.combinedClickable(
                                    onClick = {},
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        if (msg.role == MessageRole.ASSISTANT || msg.role == MessageRole.ERROR) actionTarget = msg
                                    },
                                ),
                            )
                        }
                    }
                    // 上翻历史且出现新消息时显示"回到底部"
                    if (!isAtBottom && ui.messages.isNotEmpty() && !ui.isStreaming) {
                        FloatingActionButton(
                            onClick = ::scrollToBottom,
                            containerColor = AppTheme.colors.surfaceVariant,
                            contentColor = AppTheme.colors.primary,
                            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 12.dp).size(44.dp),
                        ) {
                            Icon(Icons.Filled.ArrowDownward, contentDescription = "回到底部")
                        }
                    }
                }
            }

            if (ui.pendingImages.isNotEmpty()) {
                AttachmentPreviewBar(
                    images = ui.pendingImages,
                    onRemove = viewModel::removePendingImage,
                )
            }

            if (!ui.hasConfig) NoConfigBanner(onGoConfig)

            InputBar(
                value = input,
                onChange = { input = it },
                isStreaming = ui.isStreaming,
                canSend = ui.hasConfig,
                onPickImage = ::launchPhotoPicker,
                onSend = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.send(input)
                    input = ""
                },
                onStop = viewModel::stopStreaming,
            )
        }
    }

    viewerPath?.let { path ->
        ImageViewerDialog(path = path, onDismiss = { viewerPath = null })
    }

    actionTarget?.let { msg ->
        MessageActionMenu(
            message = msg,
            onCopy = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                context.copyTextToClipboard("消息", msg.content)
                actionTarget = null
            },
            onRegenerate = {
                viewModel.regenerate(msg.id)
                actionTarget = null
            },
            onDismiss = { actionTarget = null },
        )
    }
}

@Composable
private fun NoConfigBanner(onGoConfig: () -> Unit) {
    val c = AppTheme.colors
    Surface(color = c.surfaceVariant, shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "请先配置接口后发送消息",
                style = MaterialTheme.typography.bodyMedium,
                color = c.onSurface,
                modifier = Modifier.weight(1f),
            )
            AppButton(text = "去配置", onClick = onGoConfig, style = ButtonStyle.TEXT)
        }
    }
}

@Composable
private fun InputBar(
    value: String,
    onChange: (String) -> Unit,
    isStreaming: Boolean,
    canSend: Boolean,
    onPickImage: () -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    val c = AppTheme.colors
    Surface(
        color = c.surfaceVariant,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)) {
            IconButton(onClick = onPickImage) {
                Icon(Icons.Filled.AddPhotoAlternate, "添加图片", tint = c.secondary)
            }
            OutlinedTextField(
                value = value,
                onValueChange = onChange,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp, max = 120.dp),
                placeholder = { Text("输入消息…") },
                maxLines = 5,
                shape = MaterialTheme.shapes.large,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                ),
            )
            Spacer(Modifier.size(6.dp))
            if (isStreaming) {
                IconButton(onClick = onStop) { Icon(Icons.Filled.Stop, "停止", tint = c.error) }
            } else {
                androidx.compose.material3.Button(
                    onClick = onSend,
                    enabled = canSend && (value.isNotBlank() || true),
                    modifier = Modifier.padding(bottom = 4.dp),
                    shape = RoundedCornerShape(50),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                ) { Icon(Icons.Filled.Send, contentDescription = "发送") }
            }
        }
    }
}

/** 历史/回显消息的附件图片行。 */
@Composable
private fun AttachmentRow(attachments: List<MessageAttachment>, onOpen: (String) -> Unit) {
    LazyRow(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(attachments) { a ->
            Box(Modifier.widthIn(max = 150.dp)) {
                AsyncImage(
                    model = java.io.File(a.localPath),
                    contentDescription = "消息图片",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(120.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onOpen(a.localPath) },
                )
            }
        }
    }
}

/** 待发送图片预览。 */
@Composable
private fun AttachmentPreviewBar(images: List<UiImage>, onRemove: (String) -> Unit) {
    LazyRow(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(images, key = { it.path }) { img ->
            Box {
                AsyncImage(
                    model = java.io.File(img.path),
                    contentDescription = "已选图片",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)),
                )
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "移除图片",
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(20.dp)
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        .clickable { onRemove(img.path) },
                )
            }
        }
    }
}

/** 图片全屏查看器。 */
@Composable
private fun ImageViewerDialog(path: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.9f)).clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = java.io.File(path),
                contentDescription = "查看图片",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(16.dp),
            )
        }
    }
}

/** 长按消息的操作菜单（复制 / 重新生成）。 */
@Composable
private fun MessageActionMenu(
    message: ChatMessage,
    onCopy: () -> Unit,
    onRegenerate: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = AppTheme.colors
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxSize().background(Color.Transparent).padding(AppSpacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Bottom,
        ) {
            Column(
                Modifier
                    .widthIn(max = 360.dp)
                    .background(c.surfaceVariant, RoundedCornerShape(24.dp))
                    .padding(vertical = AppSpacing.sm),
            ) {
                ActionItem(Icons.Filled.ContentCopy, "复制", onCopy)
                if (message.content.isNotBlank() && message.role != MessageRole.USER) {
                    ActionItem(Icons.Filled.Refresh, "重新生成", onRegenerate)
                }
                ActionItem(null, "取消", onDismiss, tint = c.secondary)
            }
        }
    }
}

@Composable
private fun ActionItem(icon: ImageVector?, label: String, onClick: () -> Unit, tint: Color? = null) {
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