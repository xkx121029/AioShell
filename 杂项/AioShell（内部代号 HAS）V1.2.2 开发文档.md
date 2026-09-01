以下是整合了此前两轮调整的《AioShell（HAS）V1.2.2 开发文档》完整文本。思考模式部分已按"对话页快捷开关 + 单模型（配置档案级）开关"的最终方案重写，其余部分保持与之前一致的实现细节。

---

# AioShell（内部代号 HAS）V1.2.2 开发文档

**文档版本**：V1.2.2
**项目名称**：AioShell（内部代号 HAS）
**目标平台**：Android
**版本主题**：新增代码块增强、思考模式、多模态输入
**文档日期**：2026-08-25

---

## 一、文档说明

### 1.1 版本背景

V1.0 完成功能闭环，V1.1 完成 UI/UX 专项打磨，V1.1.2 完成补丁修复与细节收敛。本版本（V1.2.2）重新回到功能扩展节奏，聚焦三个高价值能力：

1. **代码块增强**：在 V1.1 基础代码块渲染之上，补齐语法高亮、一键复制、折叠展开、语言标签等能力
2. **思考模式**：支持展示推理模型的思考过程（reasoning_content / reasoning），开关按"单模型"粒度控制
3. **多模态输入**：支持图片输入，对接兼容 OpenAI 协议的视觉模型

### 1.2 前置依赖

| 依赖 | 状态 |
|------|------|
| 设计系统（Design Token） | V1.1 已落地 |
| 主题系统（浅色/深色/跟随系统） | V1.1 已落地 |
| 基础组件库 | V1.1 已落地 |
| 基础 Markdown 渲染 | V1.1 已落地 |
| SSE 流式解析 | V1.0 已落地 |
| 补丁修复基线 | V1.1.2 已落地 |

本版本在 V1.1.2 稳定基线上增量开发，不重构既有架构。

---

## 二、版本目标与验收基线

### 2.1 版本目标

- 代码块：语法高亮正确、复制便捷、长代码可折叠，渲染性能可控
- 思考模式：思考内容可流式展示、可折叠，开关按单模型独立控制
- 多模态：图片选择、预览、压缩、发送、历史回显全链路可用

### 2.2 兼容性约束

- 继续兼容 OpenAI 协议、AIO 系列、Ollama
- 纯文本对话行为与 V1.1.2 完全一致，不因多模态改造产生回归
- 不支持图片的旧接口在发送图片时给出明确提示

---

## 三、功能一：代码块增强

### 3.1 功能范围

| 能力 | 说明 |
|------|------|
| 语法高亮 | 覆盖主流语言的关键字、字符串、注释、数字 |
| 一键复制 | 代码块右上角复制按钮 + 复制反馈 |
| 折叠展开 | 长代码默认折叠，可展开全部 |
| 语言标签 | 代码块顶部显示识别到的语言 |
| 行号 | 可选行号显示 |
| 滚动优化 | 长代码横向 / 纵向滚动，性能可控 |

### 3.2 语法高亮方案

**选型决策**

| 方案 | 优点 | 缺点 | 结论 |
|------|------|------|------|
| 引入成熟高亮库 | 覆盖全、质量高 | 包体积大、依赖维护 | 暂不采用 |
| 自研轻量 Tokenizer | 体积小、可控 | 覆盖有限 | 采用 |

采用自研轻量词法分析，覆盖高频语言：Kotlin、Java、Python、JavaScript/TypeScript、JSON、Bash/Shell、SQL。着色规则聚焦四类 Token：**注释、字符串、关键字、数字**。原理是"够用、轻量、可维护"，不为极少数语言引入重型依赖。

**Tokenizer 接口**

```kotlin
interface CodeHighlighter {
    fun highlight(code: String, language: String): AnnotatedString
}
```

**关键字映射（节选）**

```kotlin
object HighlighterRegistry {
    private val keywords = mapOf(
        "kotlin" to setOf(
            "fun", "val", "var", "if", "else", "when", "for", "while",
            "return", "class", "object", "interface", "data", "private",
            "public", "import", "package", "suspend", "null", "true", "false",
        ),
        "python" to setOf(
            "def", "if", "elif", "else", "for", "while", "return", "import",
            "from", "class", "try", "except", "finally", "with", "as",
            "lambda", "pass", "None", "True", "False", "async", "await",
        ),
        "java" to setOf(
            "public", "private", "protected", "class", "interface", "void",
            "int", "if", "else", "for", "while", "return", "new", "import",
            "package", "static", "final", "try", "catch", "throw", "extends",
        ),
        "javascript" to setOf(
            "const", "let", "var", "function", "if", "else", "for", "while",
            "return", "class", "import", "export", "from", "async", "await",
            "new", "try", "catch", "throw", "null", "true", "false",
        ),
        "sql" to setOf(
            "SELECT", "FROM", "WHERE", "INSERT", "UPDATE", "DELETE", "CREATE",
            "TABLE", "JOIN", "LEFT", "RIGHT", "INNER", "GROUP", "BY", "ORDER",
            "AND", "OR", "NOT", "NULL", "AS", "ON", "LIMIT", "VALUES", "INTO",
        ),
    )

    private val commentTokens = mapOf(
        "kotlin" to listOf("//", "/*", "*/"),
        "java" to listOf("//", "/*", "*/"),
        "python" to listOf("#"),
        "javascript" to listOf("//", "/*", "*/"),
        "sql" to listOf("--", "/*", "*/"),
    )
}
```

**Token 配色（新增进 ColorToken）**

在 `AppColorScheme` 中扩展代码高亮语义色，浅色 / 深色各一套：

| Token | 含义 | 浅色示例 |
|-------|------|----------|
| codeKeyword | 关键字 | `#CF222E` |
| codeString | 字符串 | `#0A3069` |
| codeComment | 注释 | `#6E7781` |
| codeNumber | 数字 | `#0550AE` |

### 3.3 代码块组件

```kotlin
@Composable
fun CodeBlock(
    code: String,
    language: String?,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val clipboard = LocalClipboardManager.current

    // 折叠状态：超过 20 行默认折叠
    val lineCount = remember(code) { code.count { it == '\n' } + 1 }
    var expanded by remember(code) { mutableStateOf(lineCount <= MAX_COLLAPSED_LINES) }
    var copied by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppRadius.md))
            .background(colors.codeBackground),
    ) {
        // 顶栏：语言标签 + 复制按钮
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surfaceVariant)
                .padding(horizontal = AppSpacing.md, vertical = AppSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = language?.uppercase() ?: "CODE",
                style = MaterialTheme.typography.labelSmall,
                color = colors.secondary,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = {
                    clipboard.setText(AnnotatedString(code))
                    copied = true
                },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = if (copied) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
                    contentDescription = "复制代码",
                    tint = colors.secondary,
                )
            }
        }

        // 代码主体：折叠 + 行号 + 横向滚动
        Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            Text(
                text = highlightedCode(code, language ?: ""),
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(AppSpacing.md),
                maxLines = if (expanded) Int.MAX_VALUE else MAX_COLLAPSED_LINES,
                overflow = TextOverflow.Clip,
            )
        }

        // 折叠 / 展开按钮
        if (lineCount > MAX_COLLAPSED_LINES) {
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "收起" else "展开全部（${lineCount} 行）")
            }
        }
    }
}
```

### 3.4 复制实现

使用 Compose 的 `LocalClipboardManager`：

```kotlin
val clipboard = LocalClipboardManager.current
clipboard.setText(AnnotatedString(code))
```

复制后按钮图标切换为对勾，2 秒后恢复。

### 3.5 性能约束

- 高亮结果为 `AnnotatedString`，用 `remember(code, language)` 缓存，避免每次重组重新解析
- 超长代码块（如数万行）不做全量高亮，超过阈值降级为纯文本渲染
- 折叠态默认只渲染首屏可见部分，展开后再渲染全部

---

## 四、功能二：思考模式

### 4.1 背景与协议说明

推理模型（如 DeepSeek R1、o1 系列）在输出最终回复前，会先产生"思考过程"。不同厂商的字段命名存在差异：

| 服务 | 思考字段 |
|------|----------|
| DeepSeek R1 | `reasoning_content` |
| OpenAI o1 | `reasoning` / `reasoning_content` |
| 部分兼容实现 | `thinking` |

客户端需对这些字段做兼容解析，将思考内容与正文内容分流处理。

### 4.2 开关设计原则

思考能力本质是**模型属性**，不是会话属性。因此思考模式开关的粒度确定为**单模型（配置档案级）**：

- 每个配置档案独立控制是否展示思考内容
- 会话通过"当前使用的配置档案"间接获得开关状态
- 切换模型时开关自动跟随，避免同一模型在不同会话里状态割裂

在 AioShell 中，一个"配置档案"= Base URL + API Key + 模型名 + 参数，即用户配置的一个可用模型。因此"单模型开关"落地为"单配置档案开关"。

**开关的双入口设计**

| 入口 | 位置 | 职责 |
|------|------|------|
| 主开关 | 配置页（模型档案编辑页） | 设定该模型的默认展示行为 |
| 快捷开关 | 对话页顶栏 | 即时切换当前模型的展示状态 |

两者指向同一份数据，保证状态一致。

### 4.3 数据模型变更

**ConfigProfile 扩展**

```kotlin
data class ConfigProfile(
    val id: String,
    val name: String,
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val temperature: Float,
    val maxTokens: Int,
    val topP: Float,
    val reasoningEnabled: Boolean = true,   // 新增：本模型是否展示思考
)
```

**MessageEntity 扩展**

```kotlin
@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val role: String,
    val content: String,
    val reasoning: String? = null,          // 思考内容全文
    val reasoningDurationMs: Long? = null,  // 思考耗时
    val timestamp: Long,
)
```

数据库升级需做迁移（Room Migration），从 V1 迁移到 V2，新增字段默认 null，保证老数据不丢失。会话本身不存思考开关，开关仅存于配置档案。

### 4.4 协议层改造

**SSE Delta 结构扩展**

```kotlin
data class StreamDelta(
    val content: String = "",
    val reasoning: String = "",
    val done: Boolean = false,
)
```

**解析逻辑**

```kotlin
fun parseDelta(json: JSONObject): StreamDelta {
    val choices = json.optJSONArray("choices")
    val delta = choices?.optJSONObject(0)?.optJSONObject("delta") ?: return StreamDelta()

    val content = delta.optString("content", "")
    // 按优先级兼容不同字段名
    val reasoning = when {
        delta.has("reasoning_content") -> delta.optString("reasoning_content")
        delta.has("reasoning") -> delta.optString("reasoning")
        delta.has("thinking") -> delta.optString("thinking")
        else -> ""
    }

    val finishReason = choices.optJSONObject(0)?.optString("finish_reason")
    return StreamDelta(
        content = content,
        reasoning = reasoning,
        done = !finishReason.isNullOrEmpty(),
    )
}
```

**流式管道分流**

在 SSE 流处理层，将 reasoning 与 content 分别通过 Flow 发射：

```kotlin
sealed interface ChatStreamEvent {
    data class ReasoningDelta(val text: String) : ChatStreamEvent
    data class ContentDelta(val text: String) : ChatStreamEvent
    data class Done(val totalReasoningMs: Long) : ChatStreamEvent
}

fun flowChatStream(...): Flow<ChatStreamEvent> = flow {
    // 对每个 SSE 数据块解析出 StreamDelta
    // reasoning 非空 -> emit(ReasoningDelta)
    // content 非空 -> emit(ContentDelta)
    // finish -> emit(Done)
}
```

### 4.5 UI 设计

**思考面板组件**

```kotlin
@Composable
fun ReasoningPanel(
    reasoning: String,
    isStreaming: Boolean,
    durationMs: Long?,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val colors = AppTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppRadius.md))
            .background(colors.surfaceVariant),
    ) {
        // 折叠头部：状态图标 + 标题 + 耗时
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = colors.secondary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(AppSpacing.sm))
            Text(
                text = buildString {
                    append("思考过程")
                    if (isStreaming) append(" · 思考中…")
                    else if (durationMs != null) append(" · 用时 ${formatDuration(durationMs)}")
                },
                style = MaterialTheme.typography.labelMedium,
                color = colors.secondary,
                modifier = Modifier.weight(1f),
            )
        }

        // 展开内容：斜体次级样式
        AnimatedVisibility(visible = expanded) {
            Text(
                text = reasoning,
                style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                color = colors.secondary,
                modifier = Modifier.padding(
                    start = AppSpacing.lg,
                    end = AppSpacing.lg,
                    bottom = AppSpacing.md,
                ),
            )
        }
    }
}
```

**布局位置**：思考面板位于 AI 消息气泡上方，作为独立折叠块，与正文气泡视觉分离。流式过程中思考面板与正文气泡分别独立更新，互不阻塞。

**默认行为**：默认折叠，避免思考过程过长抢占正文注意力；流式思考时头部显示"思考中…"；思考结束显示"用时 X.Xs"。

**配置页主开关（模型档案编辑页）**

在"模型参数"分区中新增"思考模式"设置项：

```kotlin
ConfigSection(title = "思考模式") {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("展示思考过程", style = MaterialTheme.typography.bodyLarge)
            Text(
                text = "当该模型返回思考内容时，在对话中展示",
                style = MaterialTheme.typography.bodySmall,
                color = AppTheme.colors.secondary,
            )
        }
        Switch(
            checked = profile.reasoningEnabled,
            onCheckedChange = { enabled -> onReasoningToggle(enabled) },
        )
    }
}
```

**对话页快捷开关（顶栏）**

对话页顶栏展示"当前模型"的思考开关状态，点击可快捷切换。注意切换修改的是**当前会话所用配置档案**的 `reasoningEnabled`，而非会话本身：

```kotlin
@Composable
fun ChatTopBar(
    currentProfile: ConfigProfile,
    onToggleReasoning: (Boolean) -> Unit,
) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = sessionTitle,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        IconButton(
            onClick = { onToggleReasoning(!currentProfile.reasoningEnabled) },
            modifier = Modifier
                .size(48.dp)
                .semantics {
                    contentDescription = if (currentProfile.reasoningEnabled) {
                        "关闭 ${currentProfile.model} 的思考模式"
                    } else {
                        "开启 ${currentProfile.model} 的思考模式"
                    }
                },
        ) {
            Icon(
                imageVector = Icons.Outlined.Psychology,
                contentDescription = null,
                tint = if (currentProfile.reasoningEnabled) colors.primary else colors.secondary,
            )
        }
    }
}
```

切换时通过 Snackbar 轻提示，明确"已开启 / 关闭 [模型名] 的思考模式"。

### 4.6 数据流与渲染逻辑

```kotlin
data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val currentProfile: ConfigProfile? = null,   // 当前会话使用的模型档案
    val isStreaming: Boolean = false,
)

// 是否展示思考面板：由当前模型的开关决定
val showReasoning = currentProfile?.reasoningEnabled == true
```

**渲染判断规则**

- 当前模型的 `reasoningEnabled == true` + 接口返回 `reasoning` → 渲染 `ReasoningPanel`
- 当前模型的 `reasoningEnabled == false` → 即使接口返回思考内容，也不渲染面板；思考数据仍存入 `Message.reasoning`，后续打开开关即可重新看到
- 接口未返回 `reasoning` 字段 → 不渲染面板（与开关无关）

**开关切换逻辑**

```kotlin
fun toggleReasoning() {
    val profile = uiState.currentProfile ?: return
    viewModelScope.launch {
        configRepository.setReasoningEnabled(profile.id, !profile.reasoningEnabled)
    }
}
```

`setReasoningEnabled` 更新对应档案并持久化到 DataStore，多个会话共用同一档案时同步生效。

### 4.7 边界情况

| 场景 | 表现 |
|------|------|
| 切换会话，使用不同模型 | 思考开关自动跟随新模型的设置 |
| 多个会话使用同一模型 | 任一处切换开关，所有使用该模型的会话同步生效 |
| 历史消息含思考内容，当前模型已关闭 | 历史思考面板隐藏；重新开启后可见 |
| 非推理模型（无 reasoning 字段） | 开关存在但无面板，可在配置页说明中提示 |
| 会话未绑定配置档案 | 顶栏快捷开关置灰或隐藏，引导先配置 |

---

## 五、功能三：多模态输入

### 5.1 功能范围

| 能力 | 说明 |
|------|------|
| 图片选择 | 相册多选，Android 13+ 使用系统 Photo Picker |
| 图片预览 | 输入框附件缩略图，可移除 |
| 图片压缩 | 限制尺寸与体积，控制请求开销 |
| 协议构造 | 兼容 OpenAI 视觉格式（image_url + data URL） |
| 历史回显 | 消息中图片的展示与本地持久化 |
| 图片放大 | 点击图片全屏查看 |

### 5.2 图片选择

**方案选型**

| 方案 | 权限 | 说明 |
|------|------|------|
| 系统 Photo Picker | Android 13+ 免权限 | 优先 |
| Google Play 回退版 Photo Picker | 旧版本免权限 | 兼容 |
| 自定义图库 | 需存储权限 | 不采用 |

采用系统 Photo Picker，通过 `ActivityResultContracts.PickMultipleVisualMedia` 实现，无需申请存储权限，契合产品"最少权限"承诺。

```kotlin
val pickImages = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = MAX_IMAGES),
) { uris: List<Uri> ->
    viewModel.onImagesSelected(uris)
}
```

### 5.3 图片处理

**压缩策略**

| 项 | 策略 |
|----|------|
| 最长边 | 限制 1536px（平衡清晰度与体积） |
| 格式 | 统一转 JPEG |
| 质量 | 80 |
| 单图上限 | 约 500KB |
| 多图上限 | 4 张 |

**压缩实现（节选）**

```kotlin
object ImageProcessor {
    fun compress(context: Context, uri: Uri): File {
        val bitmap = loadBitmap(context, uri, maxSide = 1536)
        val outFile = File(context.filesDir, "attachments/${System.currentTimeMillis()}.jpg")
        outFile.parentFile?.mkdirs()
        outFile.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
        }
        bitmap.recycle()
        return outFile
    }

    fun encodeToBase64(file: File): String {
        val bytes = file.readBytes()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
}
```

图片统一存储到应用私有目录 `filesDir/attachments/`，不暴露给其他应用，符合本地化隐私原则。

### 5.4 协议构造

**OpenAI 兼容视觉格式**

纯文本消息 content 为字符串；含图片消息 content 为数组：

```json
{
  "role": "user",
  "content": [
    { "type": "text", "text": "请描述这张图" },
    {
      "type": "image_url",
      "image_url": { "url": "data:image/jpeg;base64,<BASE64>" }
    }
  ]
}
```

**构造逻辑**

```kotlin
fun buildUserContent(text: String, images: List<String>): Any {
    // images 为空时返回纯字符串，保持最大兼容
    if (images.isEmpty()) return text

    val parts = mutableListOf<Map<String, Any>>()
    if (text.isNotBlank()) {
        parts.add(mapOf("type" to "text", "text" to text))
    }
    images.forEach { base64 ->
        parts.add(
            mapOf(
                "type" to "image_url",
                "image_url" to mapOf(
                    "url" to "data:image/jpeg;base64,$base64",
                ),
            )
        )
    }
    return parts
}
```

**兼容性提示**

- 本地 Ollama 的图片字段为 `images`（base64 数组），网络适配层需做差异化映射
- 抽象统一的 `MultimodalContentBuilder`，根据目标服务类型生成不同请求结构，业务层不感知差异

### 5.5 数据模型变更

图片不作为 base64 存库，以文件路径引用存储，消息与图片一对多：

```kotlin
@Entity(tableName = "message_attachments")
data class MessageAttachment(
    @PrimaryKey val id: String,
    val messageId: String,
    val localPath: String,   // 本地图片文件路径
    val mimeType: String,
    val orderIndex: Int,
)
```

**数据库迁移**：新增 `message_attachments` 表，从 V2 迁移到 V3。

### 5.6 UI 设计

**输入框附件区**

输入框上方展示已选图片缩略图，横向排列，每张带移除按钮：

```kotlin
@Composable
fun AttachmentPreviewBar(
    attachments: List<Attachment>,
    onRemove: (String) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
        items(attachments, key = { it.id }) { attachment ->
            Box {
                Image(
                    painter = rememberAsyncImagePainter(attachment.localPath),
                    contentDescription = "已选图片",
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(AppRadius.sm)),
                    contentScale = ContentScale.Crop,
                )
                IconButton(
                    onClick = { onRemove(attachment.id) },
                    modifier = Modifier
                        .size(20.dp)
                        .align(Alignment.TopEnd),
                ) {
                    Icon(Icons.Outlined.Close, contentDescription = "移除图片", modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}
```

**消息内图片展示**

历史消息中的图片在气泡内展示，点击全屏查看（自研轻量查看器，不引入重型图片库）。

**输入按钮**：输入框左侧增加图片附件按钮，点击唤起 Photo Picker。

---

## 六、数据模型与数据库迁移总览

### 6.1 变更汇总

| 变更 | 内容 | 迁移版本 |
|------|------|----------|
| Message 扩展 | 新增 reasoning、reasoningDurationMs | V1 → V2 |
| 新增表 | message_attachments | V2 → V3 |
| ConfigProfile 扩展 | 新增 reasoningEnabled | DataStore（非 SQL） |

### 6.2 迁移实现

```kotlin
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE messages ADD COLUMN reasoning TEXT")
        db.execSQL("ALTER TABLE messages ADD COLUMN reasoningDurationMs INTEGER")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS message_attachments (
                id TEXT PRIMARY KEY NOT NULL,
                messageId TEXT NOT NULL,
                localPath TEXT NOT NULL,
                mimeType TEXT NOT NULL,
                orderIndex INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}
```

**原则**：所有迁移只做结构增量，不破坏既有数据；升级时老会话、老消息完整保留。ConfigProfile 存于 DataStore，新增 `reasoningEnabled` 字段需提供默认值（true）的兼容读取。

---

## 七、目录结构变更

```
app/
├── components/                  # 基础组件库（V1.1 已有）
│   └── CodeBlock.kt             # 新增：增强代码块
├── markdown/                    # Markdown 渲染（V1.1 已有）
│   ├── MarkdownText.kt
│   ├── CodeBlockRenderer.kt     # 新增：代码块渲染
│   └── highlight/               # 新增：语法高亮
│       ├── CodeHighlighter.kt
│       ├── HighlighterRegistry.kt
│       └── TokenScanner.kt
├── feature/
│   ├── chat/
│   │   ├── ChatScreen.kt
│   │   ├── ChatInputBar.kt
│   │   ├── ChatTopBar.kt        # 新增：含思考模式快捷开关
│   │   ├── ReasoningPanel.kt    # 新增：思考面板
│   │   ├── AttachmentPreviewBar.kt  # 新增：附件预览
│   │   └── ImageViewer.kt       # 新增：图片全屏查看
│   └── config/
│       ├── ConfigScreen.kt
│       └── ReasoningToggle.kt   # 新增：模型级思考开关
├── core/
│   ├── network/
│   │   ├── StreamParser.kt      # 扩展：reasoning 解析
│   │   └── MultimodalContentBuilder.kt  # 新增：多模态请求构造
│   ├── database/
│   │   ├── MessageEntity.kt     # 扩展：reasoning 字段
│   │   ├── MessageAttachment.kt # 新增：附件实体
│   │   └── Migrations.kt        # 新增：数据库迁移
│   ├── config/
│   │   └── ConfigProfile.kt     # 扩展：reasoningEnabled 字段
│   └── image/
│       ├── ImageProcessor.kt    # 新增：图片压缩与编码
│       └── ImagePicker.kt       # 新增：Photo Picker 封装
└── MainActivity.kt
```

---

## 八、测试策略

### 8.1 功能测试

| 模块 | 测试点 |
|------|--------|
| 代码块 | 各语言高亮正确、复制成功、长代码折叠、语言标签显示 |
| 思考模式 | reasoning 流式展示、折叠交互、耗时统计、开关控制、模型级独立 |
| 多模态 | 图片选择、压缩、发送、历史回显、图片移除、放大查看 |

### 8.2 单元测试

- `TokenScanner`：注释、字符串、关键字、数字的 Token 识别正确性
- `parseDelta`：reasoning_content / reasoning / thinking 三种字段名均能正确解析
- `buildUserContent`：纯文本返回字符串，含图返回数组，结构正确
- `ImageProcessor`：压缩后尺寸与体积符合预期

### 8.3 集成测试

- 推理模型：reasoning 流式 + content 流式交替输出时，UI 分流正确、无乱序
- 视觉模型：文本 + 图片混合请求正确构造并返回
- 数据库：V1 老数据升级到 V3 后完整保留
- 思考开关：切换模型时开关跟随正确，多会话共用同一档案时同步生效

### 8.4 兼容性测试

- 不支持图片的旧接口发送图片时，给出明确错误提示而非崩溃
- 非推理模型（无 reasoning 字段）行为与 V1.1.2 完全一致
- Android 8.0 至最新版本，Photo Picker 在各版本可用

---

## 九、实施顺序

| 顺序 | 步骤 | 依赖 |
|------|------|------|
| 1 | 数据模型扩展 + 数据库迁移（reasoning、attachments） | 无 |
| 2 | SSE 解析扩展（reasoning 分流） | 步骤 1 |
| 3 | 思考模式 UI（ReasoningPanel + 模型级开关） | 步骤 2 |
| 4 | 语法高亮 Tokenizer + 代码块增强 | 无 |
| 5 | 多模态协议构造（MultimodalContentBuilder） | 步骤 1 |
| 6 | 图片选择 + 压缩 + 附件 UI | 步骤 5 |
| 7 | 全功能联调与测试 | 全量 |

**关键约束**：数据模型与协议层先行，UI 层后行，避免 UI 依赖未定型的底层结构。思考模式开关需先落地 ConfigProfile 字段与 Repository 层，再做双入口 UI。

---

## 十、V1.2.2 验收清单

**代码块验收**

- [ ] 主流语言语法高亮正确，颜色区分清晰
- [ ] 一键复制成功，反馈明确
- [ ] 超过 20 行代码默认折叠，可展开 / 收起
- [ ] 长代码滚动流畅，高亮结果有缓存

**思考模式验收**

- [ ] 推理模型思考内容流式展示
- [ ] 思考面板可折叠，默认折叠
- [ ] 思考耗时显示正确
- [ ] 模型级开关独立生效，切换模型自动跟随
- [ ] 对话页快捷开关与配置页主开关状态一致
- [ ] 非推理模型不显示思考面板，无回归

**多模态验收**

- [ ] 图片选择（Photo Picker）可用，免权限
- [ ] 图片压缩后体积与尺寸符合预期
- [ ] 图片发送协议结构正确，模型正常返回
- [ ] 历史消息图片正常回显、可放大
- [ ] 图片可移除，发送逻辑正确

**兼容性与回归验收**

- [ ] 纯文本对话行为与 V1.1.2 一致
- [ ] 老数据升级后完整保留
- [ ] 不支持图片的接口给出明确提示
- [ ] 深浅色下新增组件均适配正常

---

以上为 V1.2.2 完整开发文档，已整合"对话页快捷开关 + 单模型开关"的思考模式方案。如需进一步展开，我可以补充：**语法高亮 Token 扫描的完整状态机设计**、**思考模式与正文交替输出的乱序处理方案**、**多模态 Ollama 与 OpenAI 协议的差异适配表**，或 **V1.2.2 的 PR 拆分与代码审查清单**。

---

# 版本演进记录（V1.3 → V1.8）

> 本文档正文为 V1.2.2 基线。自 V1.3 起按功能主题持续增量开发，以下为本仓库当前（V1.8.0，versionCode 10）所汇聚的后续版本演进摘要。

## V1.3–V1.6（交互与视觉迭代）

- 语音输入光场可视化（输入框光源 / 竖向声波条 / 空中实时文字）
- 语音浮层深度优化为液态玻璃（Liquid Glass）视觉
- 用户消息气泡靠右修正
- 语音识别（Vosk 离线）、提示词模板库、对话全文搜索

## V1.7.0（会话管理 / 数据 / 统计 / 界面自定义）

- 多会话批量管理（多选批量导出 / 归档 / 删除）
- 对话置顶与分组、对话归档、自动生成标题、输入草稿自动保存
- 数据备份与恢复（JSON 全量导出 / 覆盖式恢复）
- AI 消息富文本渲染（Markdown 表格 / 任务列表 / 有序列表）
- 会话统计页、多接口快速切换、界面自定义（强调色 / 字号 / 行距）
- 数据库迁移至 V6（归档 / 草稿字段）

## V1.8.0（消息 / 效率 / 数据与安全）

- 消息编辑与重新生成：长按编辑，编辑用户消息后自动截断后续并重新生成 AI 回复
- 消息收藏 / 星标：消息级星标 + 收藏集中查看
- 会话标签与筛选、会话复制与合并
- 上下文长度控制（限制参与请求的历史消息数量）
- 快捷指令 / 斜杠命令（`/` 命令面板 + 模板一键填入）
- AI 回复自动朗读（TTS，可开关）
- 会话级模型覆盖（单个会话覆盖全局模型）
- 长图（PNG）/ PDF 导出（Canvas 长图 + PdfDocument 分页）
- Token 用量与费用估算（统计页按单价估算）
- 应用锁（PIN，SecurityCrypto SHA-256 哈希，启动锁屏）
- 定时提醒（AlarmManager 精确提醒 + 通知渠道）
- 数据库迁移至 V7（tags / modelOverride / starred 字段）

*内容由 AI 生成仅供参考*