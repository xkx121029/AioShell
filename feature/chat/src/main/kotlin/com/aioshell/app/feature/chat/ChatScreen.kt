package com.aioshell.app.feature.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.filled.Mic
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.aioshell.app.core.data.audio.VoiceModelState
import com.aioshell.app.core.data.model.ChatMessage
import com.aioshell.app.core.data.model.MessageAttachment
import com.aioshell.app.core.data.model.MessageRole
import com.aioshell.app.core.data.model.MessageStatus
import com.aioshell.app.core.ui.components.AppButton
import com.aioshell.app.core.ui.components.ButtonStyle
import com.aioshell.app.core.ui.components.EmptyState
import com.aioshell.app.core.ui.components.MessageBubble
import com.aioshell.app.core.ui.theme.AppColorScheme
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

    val voiceModelState by viewModel.voiceModelState.collectAsStateWithLifecycle()
    var showVoiceDialog by remember { mutableStateOf(false) }

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            viewModel.toggleVoice { }
            showVoiceDialog = true
        }
    }

    fun startVoice() {
        val record = android.Manifest.permission.RECORD_AUDIO
        val granted = ContextCompat.checkSelfPermission(context, record) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) {
            viewModel.toggleVoice { }
            showVoiceDialog = true
        } else {
            micPermission.launch(record)
        }
    }

    // 语音识别中，实时回显识别文本到输入框
    LaunchedEffect(ui.speechText) {
        if (ui.isListening) input = ui.speechText
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

            when (val vm = voiceModelState) {
                is VoiceModelState.Downloading -> Text(
                    "正在下载语音模型 ${(vm.progress * 100).toInt()}% …",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppTheme.colors.secondary,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                is VoiceModelState.Error -> Text(
                    vm.message,
                    style = MaterialTheme.typography.labelSmall,
                    color = AppTheme.colors.error,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                else -> Unit
            }

            if (!ui.hasConfig) NoConfigBanner(onGoConfig)

            InputBar(
                value = input,
                onChange = { input = it },
                isStreaming = ui.isStreaming,
                canSend = ui.hasConfig,
                isListening = ui.isListening,
                onPickImage = ::launchPhotoPicker,
                onVoiceToggle = ::startVoice,
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

    if (showVoiceDialog) {
        VoiceInputOverlay(
            speechText = ui.speechText,
            isListening = ui.isListening,
            modelState = voiceModelState,
            onDone = {
                showVoiceDialog = false
                viewModel.toggleVoice { recognized -> input = recognized }
            },
            onCancel = {
                showVoiceDialog = false
                viewModel.cancelVoice()
            },
        )
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
    isListening: Boolean,
    onPickImage: () -> Unit,
    onVoiceToggle: () -> Unit,
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
            IconButton(onClick = onVoiceToggle) {
                Icon(
                    Icons.Filled.Mic,
                    contentDescription = if (isListening) "停止语音输入" else "语音输入",
                    tint = if (isListening) c.error else c.secondary,
                )
            }
            IconButton(onClick = onPickImage) {
                Icon(Icons.Filled.AddPhotoAlternate, "添加图片", tint = c.secondary)
            }
            OutlinedTextField(
                value = value,
                onValueChange = onChange,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp, max = 120.dp)
                    // 长按输入框 → 语音输入（不拦截单击聚焦/打字）
                    .pointerInput(onVoiceToggle) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val longPress = awaitLongPressOrCancellation(down.id)
                            if (longPress != null) onVoiceToggle()
                        }
                    },
                placeholder = { Text(if (isListening) "正在聆听…" else "输入消息…") },
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

/** 液态玻璃语音浮层：长按输入框呼出，上滑取消，到达取消区时文字粒子漂浮。 */
@Composable
private fun VoiceInputOverlay(
    speechText: String,
    isListening: Boolean,
    modelState: VoiceModelState,
    onDone: () -> Unit,
    onCancel: () -> Unit,
) {
    val c = AppTheme.colors
    val density = LocalDensity.current
    val cancelThresholdPx = with(density) { 120.dp.toPx() }
    var dragY by remember { mutableStateOf(0f) } // 负值 = 向上拖动
    val cancelPreview = dragY <= -cancelThresholdPx

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        // 柔和渐变遮罩：中心透光、四周渐暗，衬托玻璃辉光
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        radius = with(density) { 760.dp.toPx() },
                        colors = listOf(Color(0x00000000), Color(0x48000000), Color(0x99000000)),
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            GlassSheet(
                modifier = Modifier
                    .widthIn(max = 340.dp)
                    .offset(y = (dragY.coerceAtMost(0f)).dp)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { dragY = 0f },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragY = (dragY + dragAmount.y).coerceAtLeast(0f)
                            },
                            onDragEnd = {
                                if (cancelPreview) onCancel() else dragY = 0f
                            },
                            onDragCancel = { dragY = 0f },
                        )
                    },
                shape = RoundedCornerShape(40.dp),
            ) {
                Column(
                    Modifier.padding(horizontal = 26.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    GlassCancelPill(cancelPreview)
                    Spacer(Modifier.height(16.dp))

                    GlassMic(cancelPreview = cancelPreview, isListening = isListening)

                    // 识别文本：到达取消区未取消时 → 粒子漂浮效果
                    if (speechText.isNotBlank()) {
                        Box(Modifier.padding(top = 14.dp, bottom = 8.dp)) {
                            if (cancelPreview) {
                                FloatingParticleText(
                                    text = speechText,
                                    color = c.primary,
                                    modifier = Modifier.widthIn(max = 280.dp),
                                )
                            } else {
                                Text(
                                    speechText,
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = c.onSurface,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                )
                            }
                        }
                    } else {
                        Spacer(Modifier.height(24.dp))
                    }

                    Text(
                        when {
                            modelState is VoiceModelState.Downloading ->
                                "正在下载语音模型 ${(modelState.progress * 100).toInt()}%…"
                            modelState is VoiceModelState.Error -> modelState.message
                            else -> if (isListening) "正在聆听…" else "准备就绪"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (modelState is VoiceModelState.Error) c.error else c.secondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    Text(
                        "识别在本地进行 · 上滑取消",
                        style = MaterialTheme.typography.labelSmall,
                        color = c.secondary.copy(alpha = 0.7f),
                    )

                    Spacer(Modifier.height(18.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        GlassButton(
                            text = "取消",
                            primary = false,
                            onClick = onCancel,
                            modifier = Modifier.weight(1f),
                        )
                        GlassButton(
                            text = "完成",
                            primary = true,
                            onClick = onDone,
                            enabled = speechText.isNotBlank(),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

/** 液态玻璃卡片：多层折射渐变 + 顶部内反射高光 + 细描边 + 外光晕。 */
@Composable
private fun GlassSheet(
    modifier: Modifier,
    shape: androidx.compose.ui.graphics.Shape,
    content: @Composable () -> Unit,
) {
    val c = AppTheme.colors
    val dark = c.background.luminance() < 0.5f
    val density = LocalDensity.current
    Box(
        modifier
            .shadow(
                elevation = 28.dp,
                shape = shape,
                ambientColor = c.primary.copy(alpha = 0.22f),
                spotColor = c.primary.copy(alpha = 0.30f),
            )
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (dark) 0.16f else 0.55f),
                        if (dark) Color(0x3323272F) else Color(0xE6FFFFFF),
                        if (dark) Color(0x5523272F) else Color(0xD9FFFFFF),
                    )
                )
            )
            .border(1.dp, Color.White.copy(alpha = if (dark) 0.28f else 0.55f), shape),
    ) {
        // 底层半透表面
        Box(Modifier.matchParentSize().background(c.surface.copy(alpha = if (dark) 0.55f else 0.60f)))
        // 顶部内反射高光
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = if (dark) 0.16f else 0.45f),
                        Color.Transparent,
                    ),
                    startY = 0f,
                    endY = with(density) { 140.dp.toPx() },
                )
            )
        )
        // 主色折射薄雾
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(c.primary.copy(alpha = 0.10f), Color.Transparent))))
        content()
    }
}

/** 上滑取消提示胶囊（玻璃）。 */
@Composable
private fun GlassCancelPill(cancelPreview: Boolean) {
    val c = AppTheme.colors
    val tint = if (cancelPreview) c.error else c.secondary.copy(alpha = 0.55f)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(tint.copy(alpha = 0.12f))
            .border(1.dp, tint.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Icon(
            imageVector = if (cancelPreview) Icons.Filled.Mic else Icons.Filled.Refresh,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(13.dp),
        )
        Spacer(Modifier.size(6.dp))
        Text(
            if (cancelPreview) "松手取消" else "上滑取消",
            style = MaterialTheme.typography.labelSmall,
            color = tint,
        )
    }
}

/** 液态玻璃麦克风：玻璃球体高光 + 声波脉冲 + 外缘辉光。 */
@Composable
private fun GlassMic(cancelPreview: Boolean, isListening: Boolean) {
    val c = AppTheme.colors
    val active = isListening && !cancelPreview
    val density = LocalDensity.current
    val circlePx = with(density) { 80.dp.toPx() }
    Box(Modifier.size(140.dp), contentAlignment = Alignment.Center) {
        if (active) SoundWaveRings(c.primary)

        // 外缘辉光
        Box(
            Modifier
                .size(96.dp)
                .shadow(18.dp, RoundedCornerShape(48), spotColor = c.primary.copy(alpha = if (active) 0.55f else 0.08f)),
        )
        // 玻璃球体
        Box(
            Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(40.dp))
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = if (cancelPreview || active) 0.9f else 0.75f),
                            baseColor(c, cancelPreview, active),
                        ),
                        center = Offset(circlePx * 0.32f, circlePx * 0.24f),
                        radius = circlePx,
                    )
                )
                .border(1.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(40.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Mic,
                contentDescription = null,
                tint = glassIconColor(c, cancelPreview, active),
                modifier = Modifier.size(34.dp),
            )
        }
        // 球体顶部受光弧
        Box(
            Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(40.dp))
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.55f), Color.Transparent),
                        center = Offset(circlePx * 0.5f, circlePx * 0.08f),
                        radius = circlePx * 0.6f,
                    )
                )
        )
    }
}

private fun baseColor(c: AppColorScheme, cancelPreview: Boolean, active: Boolean): Color =
    if (cancelPreview) c.error else if (active) c.primary else c.surfaceVariant

private fun glassIconColor(c: AppColorScheme, cancelPreview: Boolean, active: Boolean): Color =
    if (cancelPreview || active) c.onPrimary else c.secondary

/** 玻璃胶囊按钮。 */
@Composable
private fun GlassButton(
    text: String,
    primary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val c = AppTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .clip(RoundedCornerShape(26.dp))
            .background(
                if (primary) {
                    Brush.linearGradient(listOf(c.primary, c.primary.copy(alpha = 0.82f)))
                } else {
                    Brush.linearGradient(listOf(Color.White.copy(alpha = 0.20f), Color.White.copy(alpha = 0.06f)))
                }
            )
            .border(1.dp, Color.White.copy(alpha = if (primary) 0.6f else 0.3f), RoundedCornerShape(26.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(vertical = 12.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (primary) c.onPrimary else c.onSurface,
        )
    }
}

/** 粒子漂浮文本：把每个字符作为粒子，在原位置轻轻漂浮。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FloatingParticleText(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        text.take(60).toCharArray().forEachIndexed { index, ch ->
            if (ch == ' ' || ch == '\n') {
                Spacer(Modifier.widthIn(min = 8.dp))
                return@forEachIndexed
            }
            ParticleChar(ch.toString(), color, seed = index)
        }
    }
}

/** 单个字符粒子：缓慢上下/左右漂浮 + 透明度呼吸。 */
@Composable
private fun ParticleChar(char: String, color: Color, seed: Int) {
    val t = rememberInfiniteTransition(label = "particle_$seed")
    val dy by t.animateFloat(
        initialValue = -2.5f,
        targetValue = 2.5f,
        animationSpec = infiniteRepeatable(tween(900 + (seed % 5) * 160), RepeatMode.Reverse),
        label = "dy_$seed",
    )
    val dx by t.animateFloat(
        initialValue = -1.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(tween(1100 + (seed % 4) * 200), RepeatMode.Reverse),
        label = "dx_$seed",
    )
    val alpha by t.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700 + (seed % 3) * 150), RepeatMode.Reverse),
        label = "a_$seed",
    )
    Text(
        char,
        style = MaterialTheme.typography.headlineSmall,
        color = color.copy(alpha = alpha),
        modifier = Modifier.offset(x = dx.dp, y = dy.dp),
    )
}

/** 声波脉冲环：录音时的轻量持续动画。 */
@Composable
private fun SoundWaveRings(color: Color) {
    val transition = rememberInfiniteTransition(label = "sonar")
    val radius by transition.animateFloat(
        initialValue = 36f,
        targetValue = 64f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Restart),
        label = "sonar_r",
    )
    val alpha by transition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Restart),
        label = "sonar_a",
    )
    Canvas(Modifier.size(132.dp)) {
        drawCircle(color = color, radius = radius, alpha = alpha, style = Stroke(width = 2.dp.toPx()))
        drawCircle(color = color.copy(alpha = 0.25f), radius = radius * 0.7f, style = Stroke(width = 1.dp.toPx()))
    }
}