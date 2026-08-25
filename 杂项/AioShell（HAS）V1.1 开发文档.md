以下是《AioShell（HAS）V1.1 开发文档》完整文本，面向 Android 工程实现，聚焦 UI / UX 专项优化的技术方案，含架构设计、组件规范与关键代码示例。

---

# AioShell（HAS）V1.1 开发文档

**文档版本**：V1.1
**项目名称**：AioShell（内部代号 HAS）
**目标平台**：Android
**文档性质**：技术开发文档（UI / UX 专项优化）
**文档日期**：2026-08-25

---

## 一、文档说明

### 1.1 文档范围

本文档面向工程实现，描述 V1.1 版本中 UI / UX 专项优化的技术方案。V1.1 不新增核心业务功能，工作重点为：

- 设计系统（Design Token）落地
- 主题系统完善
- 基础组件库抽取
- 三大核心页面重构
- Markdown 渲染
- 动效系统
- 无障碍适配
- 性能优化

### 1.2 前置约定

V1.0 已确定的技术栈保持不变：

| 项 | 选型 |
|----|------|
| 语言 | Kotlin |
| UI | Jetpack Compose |
| 架构 | MVVM + Repository |
| 网络 | OkHttp + Retrofit / Ktor |
| 异步 | Coroutines + Flow |
| 存储 | Room + DataStore |
| 安全 | Android Keystore + EncryptedSharedPreferences |
| DI | Hilt |

V1.1 在现有代码基础上进行重构与优化，不改变业务架构边界。

---

## 二、目标与验收基线

### 2.1 版本目标

在功能范围不变的前提下，实现以下体验升级：

1. 视觉统一：全项目颜色、字体、间距、圆角无硬编码
2. 主题完整：浅色 / 深色 / 跟随系统三种模式无适配断裂
3. 组件复用：基础 UI 组件统一管理，消除重复实现
4. 状态完备：加载、空、错误、成功四类状态全覆盖
5. 体验精细：动效克制流畅，Markdown 渲染优雅，无障碍基础达标

### 2.2 验收基线（性能）

- 长对话（数百条消息）滚动与流式输出无明显掉帧
- 冷启动与内存占用不差于 V1.0
- 深浅色切换无闪烁

---

## 三、设计系统（Design Token）

### 3.1 设计目标

将 V1.0 中散落在各 Composable 的硬编码颜色、字号、间距统一收敛到 Token 层，实现"改一处、全项目生效"。

### 3.2 Token 分类

| Token 类别 | 内容 |
|-----------|------|
| Color | 主色、辅助色、语义色、中性色阶 |
| Typography | 标题、正文、辅助、代码 |
| Spacing | 4dp 网格体系 |
| Radius | 卡片、气泡、按钮、输入框 |
| Elevation | 卡片层级阴影 |
| Motion | 时长与缓动曲线 |

### 3.3 Color Token 实现

颜色 Token 不直接暴露具体色值，而是以语义命名，深浅主题分别映射。

```kotlin
// design/ColorToken.kt
import androidx.compose.ui.graphics.Color

data class AppColorScheme(
    val primary: Color,
    val onPrimary: Color,
    val secondary: Color,
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val onBackground: Color,
    val onSurface: Color,
    val error: Color,
    val onError: Color,
    val success: Color,
    val warning: Color,
    val outline: Color,
    val codeBackground: Color,
    val userBubble: Color,
    val aiBubble: Color,
)

object AppLightColors {
    val scheme = AppColorScheme(
        primary = Color(0xFF4F6EF7),
        onPrimary = Color(0xFFFFFFFF),
        secondary = Color(0xFF6B7280),
        background = Color(0xFFF7F8FA),
        surface = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFFEDEFF3),
        onBackground = Color(0xFF1A1D24),
        onSurface = Color(0xFF1A1D24),
        error = Color(0xFFD92D20),
        onError = Color(0xFFFFFFFF),
        success = Color(0xFF12B76A),
        warning = Color(0xFFF79009),
        outline = Color(0xFFD0D5DD),
        codeBackground = Color(0xFFF1F2F4),
        userBubble = Color(0xFF4F6EF7),
        aiBubble = Color(0xFFEDEFF3),
    )
}

object AppDarkColors {
    val scheme = AppColorScheme(
        primary = Color(0xFF7B93FF),
        onPrimary = Color(0xFF10131A),
        secondary = Color(0xFF9AA1AF),
        background = Color(0xFF0F1115),
        surface = Color(0xFF181B21),
        surfaceVariant = Color(0xFF23272F),
        onBackground = Color(0xFFE6E8EC),
        onSurface = Color(0xFFE6E8EC),
        error = Color(0xFFF04438),
        onError = Color(0xFFFFFFFF),
        success = Color(0xFF32D583),
        warning = Color(0xFFFDB022),
        outline = Color(0xFF3A3F4B),
        codeBackground = Color(0xFF14171C),
        userBubble = Color(0xFF4F6EF7),
        aiBubble = Color(0xFF23272F),
    )
}
```

### 3.4 Spacing / Radius / Motion Token

```kotlin
// design/DimensionToken.kt
object AppSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

object AppRadius {
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val full = 999.dp
}

object AppMotion {
    const val durationFast = 150
    const val durationNormal = 250
    const val durationSlow = 300
}
```

### 3.5 Token 使用约定

- 业务层 Composable 一律引用 Token，禁止硬编码色值 / 字号 / 间距
- 新增颜色必须先进入 `AppColorScheme`，同时补浅色与深色两套映射
- 间距严格使用 4dp 网格，不在 Token 之外临时造尺寸

---

## 四、主题系统

### 4.1 主题定义

在 Token 之上封装 `AppTheme`，统一注入 MaterialTheme，支持浅色、深色、跟随系统。

```kotlin
// theme/AppTheme.kt
@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) AppDarkColors.scheme else AppLightColors.scheme
    val colorScheme = lightColorScheme(
        primary = colors.primary,
        onPrimary = colors.onPrimary,
        secondary = colors.secondary,
        background = colors.background,
        surface = colors.surface,
        surfaceVariant = colors.surfaceVariant,
        onBackground = colors.onBackground,
        onSurface = colors.onSurface,
        error = colors.error,
        onError = colors.onError,
        outline = colors.outline,
    )

    CompositionLocalProvider(
        LocalAppColors provides colors,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            content = content,
        )
    }
}

// 供业务层直接读取自定义语义色（如气泡色、代码块色）
val LocalAppColors = staticCompositionLocalOf { AppLightColors.scheme }

object AppTheme {
    val colors: AppColorScheme
        @Composable get() = LocalAppColors.current
}
```

### 4.2 主题持久化

主题偏好通过 DataStore 保存，取值枚举：`LIGHT` / `DARK` / `SYSTEM`。

```kotlin
enum class ThemeMode { LIGHT, DARK, SYSTEM }
```

在根 Composable 中读取并转换为 `darkTheme` 布尔值：

```kotlin
val themeMode by viewModel.themeMode.collectAsState()
val darkTheme = when (themeMode) {
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
}
```

### 4.3 深浅色适配原则

- 语义色必须成对定义，禁止单主题遗漏
- 图标、气泡、代码块等自定义组件通过 `LocalAppColors` 取色，不能只依赖 MaterialTheme
- 切换主题时避免重建业务状态，保证无闪烁、无数据丢失

---

## 五、基础组件库

### 5.1 组件清单

| 组件 | 职责 |
|------|------|
| AppButton | 主 / 次 / 文字按钮，含禁用与加载态 |
| AppTextField | 统一输入框，含错误提示与说明文案 |
| AppCard | 统一卡片容器 |
| MessageBubble | 用户 / AI 消息气泡 |
| EmptyState | 空状态（含图标、文案、行动按钮） |
| LoadingState | 加载状态（区分全局 / 局部） |
| AppDialog | 统一确认弹层 |
| AppSnackbar | 统一轻提示 |

### 5.2 AppButton 实现

```kotlin
@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    style: ButtonStyle = ButtonStyle.Primary,
) {
    val colors = AppTheme.colors
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(AppRadius.md),
        colors = when (style) {
            ButtonStyle.Primary -> ButtonDefaults.buttonColors(
                containerColor = colors.primary,
                contentColor = colors.onPrimary,
            )
            ButtonStyle.Secondary -> ButtonDefaults.buttonColors(
                containerColor = colors.surfaceVariant,
                contentColor = colors.onSurface,
            )
        },
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = LocalContentColor.current,
            )
        } else {
            Text(text = text)
        }
    }
}
```

### 5.3 EmptyState 实现

统一空状态组件，避免各页面各自实现导致风格分裂。

```kotlin
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    description: String? = null,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    Column(
        modifier = modifier.fillMaxWidth().padding(AppSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = colors.secondary,
        )
        Spacer(Modifier.height(AppSpacing.md))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSurface,
        )
        if (description != null) {
            Spacer(Modifier.height(AppSpacing.sm))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.secondary,
                textAlign = TextAlign.Center,
            )
        }
        if (actionText != null && onAction != null) {
            Spacer(Modifier.height(AppSpacing.lg))
            AppButton(text = actionText, onClick = onAction, style = ButtonStyle.Secondary)
        }
    }
}
```

### 5.4 状态反馈统一规范

| 状态 | 组件 | 触发条件 |
|------|------|----------|
| 加载 | LoadingState | 请求中、流式生成中 |
| 空 | EmptyState | 无会话、无消息、首启 |
| 错误 | ErrorState（扩展自 EmptyState） | 网络错误、超时、配置无效 |
| 成功 | AppSnackbar | 保存成功、复制成功、连接成功 |

错误状态必须提供行动入口（重试 / 返回配置），禁止只展示错误文案而无出口。

---

## 六、核心页面重构

### 6.1 会话列表页

#### 信息层级

列表项按"标题 > 时间 / 摘要 > 操作"排序，减少视觉噪音。

```kotlin
@Composable
fun SessionListItem(
    session: Session,
    isActive: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = AppTheme.colors
    val background = if (isActive) colors.surfaceVariant else colors.surface

    // 左滑操作容器
    var offsetX by remember { mutableFloatStateOf(0f) }
    SwipeToReveal(
        offsetX = offsetX,
        onOffsetChange = { offsetX = it },
        actions = listOf(
            SwipeAction("重命名", colors.secondary, onRename),
            SwipeAction("删除", colors.error, onDelete),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(background)
                .clickable(onClick = onClick)
                .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.md),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = session.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(AppSpacing.xs))
                Text(
                    text = session.lastMessagePreview,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
```

#### 空态逻辑

- 首次使用（无任何会话且无配置）：展示引导空态，行动按钮"去配置接口"
- 有配置但无会话：展示空态，行动按钮"新建会话"
- 两者通过 ViewModel 状态区分

### 6.2 对话页

#### 消息气泡

```kotlin
@Composable
fun MessageBubble(
    message: ChatMessage,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val isUser = message.role == Role.USER

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .background(
                    color = if (isUser) colors.userBubble else colors.aiBubble,
                    shape = RoundedCornerShape(
                        topStart = AppRadius.lg,
                        topEnd = AppRadius.lg,
                        bottomStart = if (isUser) AppRadius.lg else AppRadius.sm,
                        bottomEnd = if (isUser) AppRadius.sm else AppRadius.lg,
                    ),
                )
                .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.md),
        ) {
            MarkdownText(content = message.content, isUser = isUser)
        }
    }
}
```

#### 流式输出指示

在 AI 消息流式生成期间，尾部展示光标指示器：

```kotlin
if (message.isStreaming) {
    Text(
        text = "▍",
        color = colors.primary,
        modifier = Modifier
            .alpha(animateAlphaForCursor())
    )
}
```

#### 长按菜单

长按消息呼出操作菜单，复用统一的弹层组件，操作项含"复制"与"重新生成"。

### 6.3 配置页

#### 表单分组

配置项按认知顺序分组，字段带说明文案：

```kotlin
ConfigSection(title = "接口地址") {
    AppTextField(
        value = baseUrl,
        onValueChange = onBaseUrlChange,
        label = "Base URL",
        placeholder = "https://your-api.example.com/v1",
        supportingText = "兼容 OpenAI 协议的接口根地址，以 /v1 结尾",
        isError = baseUrlError != null,
        errorText = baseUrlError,
    )
}

ConfigSection(title = "认证") {
    AppTextField(
        value = apiKey,
        onValueChange = onApiKeyChange,
        label = "API Key",
        isSecret = true,
        supportingText = "密钥仅保存在本机，加密存储",
    )
}

ConfigSection(title = "模型参数") {
    ParameterSlider(
        label = "temperature",
        value = temperature,
        onValueChange = onTemperatureChange,
        range = 0f..2f,
        supportingText = "数值越高回复越随机，越低越稳定",
    )
    ParameterSlider(
        label = "max_tokens",
        value = maxTokens.toFloat(),
        onValueChange = { onMaxTokensChange(it.toInt()) },
        range = 1f..8192f,
        supportingText = "单次回复的最大 Token 数",
    )
}
```

#### 参数滑块组件

滑块 + 数值输入结合，替代纯文本输入：

```kotlin
@Composable
fun ParameterSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float>,
    supportingText: String? = null,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f))
            Text(
                text = formatValue(value),
                style = MaterialTheme.typography.labelLarge,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
        )
        if (supportingText != null) {
            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodySmall,
                color = AppTheme.colors.secondary,
            )
        }
    }
}
```

#### 连接测试反馈

连接测试结果分状态展示：进行中（Loading）、成功（成功色 + 耗时）、失败（错误色 + 原因）。原因需分类展示，如网络不可达、鉴权失败（401）、接口不存在（404）、超时等，而非笼统的"失败"。

---

## 七、Markdown 渲染

### 7.1 方案选型

AI 回复通常包含 Markdown 内容。V1.1 采用"基础解析 + 有限语法支持"策略，覆盖高频语法：标题、列表、引用、粗体 / 斜体、行内代码、代码块、链接。

两种实现路径：

| 方案 | 适用 | 说明 |
|------|------|------|
| 引入三方库（如 markdown 解析库） | 快速落地 | 复用成熟解析，需评估包体积与更新维护 |
| 自研轻量解析 | 可控性优先 | 仅支持高频语法，避免复杂 AST |

建议：若追求上线效率，优先评估成熟库；若对包体积与安全有强要求，自研有限语法解析器，仅覆盖上述高频语法。

### 7.2 代码块渲染

代码块独立容器，深色代码背景 + 等宽字体，长代码可横向滚动：

```kotlin
@Composable
fun CodeBlock(
    code: String,
    language: String?,
) {
    val colors = AppTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppRadius.md))
            .background(colors.codeBackground)
            .horizontalScroll(rememberScrollState()),
    ) {
        if (language != null) {
            Text(
                text = language,
                style = MaterialTheme.typography.labelSmall,
                color = colors.secondary,
                modifier = Modifier.padding(AppSpacing.sm),
            )
        }
        Text(
            text = code,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(AppSpacing.sm),
        )
    }
}
```

### 7.3 流式渲染兼容

流式输出时 Markdown 内容不完整，解析需容忍不闭合语法，避免渲染异常。策略：对未闭合的代码块 / 列表做降级处理，仅渲染已完成片段，防止 UI 抖动。

---

## 八、动效系统

### 8.1 设计原则

- 动效服务于反馈与连续性，不做炫技
- 时长控制在 150–300ms
- 统一缓动曲线（优先 `FastOutSlowInEasing`）

### 8.2 关键场景实现

**消息出现（淡入 + 上移）**

```kotlin
@Composable
fun AnimatedMessageAppearance(content: @Composable () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(AppMotion.durationNormal)) +
            slideInVertically(
                initialOffsetY = { it / 8 },
                animationSpec = tween(AppMotion.durationNormal),
            ),
    ) {
        content()
    }
}
```

**会话切换过渡**

使用 `AnimatedContent` 做页面级平滑过渡：

```kotlin
AnimatedContent(
    targetState = currentSessionId,
    transitionSpec = {
        fadeIn(tween(AppMotion.durationFast)) togetherWith
            fadeOut(tween(AppMotion.durationFast))
    },
    label = "session_switch",
) { sessionId ->
    ChatScreen(sessionId = sessionId)
}
```

**流式光标**

```kotlin
val infinite = rememberInfiniteTransition(label = "cursor")
val alpha by infinite.animateFloat(
    initialValue = 1f,
    targetValue = 0.2f,
    animationSpec = infiniteRepeatable(
        animation = tween(500),
        repeatMode = RepeatMode.Reverse,
    ),
    label = "cursor_alpha",
)
```

### 8.3 性能约束

- 动效只作用于局部节点，避免触发整棵 Composable 树重组
- 列表项动效使用 `Modifier.animateItem()`（若引入）而非全局动画
- 流式输出更新与动画解耦，防止打字机过程叠加动画开销

---

## 九、无障碍适配

### 9.1 适配项

| 项 | 标准 | 实现方式 |
|----|------|----------|
| 对比度 | WCAG AA（4.5:1） | Token 取值时校验对比度 |
| 触控目标 | ≥ 48dp | 统一按钮与图标点击区域尺寸 |
| 内容描述 | 图标按钮可朗读 | `Modifier.semantics { contentDescription = ... }` |
| 字体缩放 | 不破坏布局 | 用 `sp` 单位 + 自适应布局，避免固定高度截断 |
| 键盘可达 | 核心操作可完成 | 焦点顺序与 `focusable` 设置 |

### 9.2 图标按钮示例

```kotlin
IconButton(
    onClick = onCopy,
    modifier = Modifier
        .size(48.dp)
        .semantics { contentDescription = "复制回复内容" },
) {
    Icon(Icons.Outlined.ContentCopy, contentDescription = null)
}
```

图标按钮的 `contentDescription` 放在语义层，`Icon` 本身置 null，避免重复朗读。

---

## 十、性能优化

### 10.1 列表与消息渲染

- 会话列表、消息列表使用 `LazyColumn`，禁用嵌套滚动冲突
- 消息项使用稳定 Key，避免无谓重组

```kotlin
LazyColumn {
    items(
        items = messages,
        key = { it.id },
    ) { message ->
        MessageBubble(message)
    }
}
```

- 流式输出时仅重组"正在生成"的最后一条消息，历史消息保持稳定

### 10.2 重组范围控制

- 将大页面拆分为细粒度 Composable，缩小重组范围
- 使用 `remember` / `derivedStateOf` 缓存派生状态
- 避免在 Composable 内创建高开销对象

### 10.3 冷启动优化

- 首屏只渲染必要内容，非首屏模块延迟初始化
- 配置读取与主题加载使用异步加载，先渲染骨架，再填充数据

### 10.4 内存控制

- 长对话消息使用分页加载（LazyColumn 懒加载天然支持）
- 代码块、Markdown 渲染结果按需缓存，避免重复解析

---

## 十一、测试策略

### 11.1 测试分层

| 层 | 内容 | 覆盖点 |
|----|------|--------|
| 单元测试 | Token 映射、主题枚举、Markdown 解析 | 语义色成对、语法解析正确 |
| 组件测试 | 基础组件渲染 | 按钮状态、空态展示、气泡对齐 |
| 界面测试 | 页面状态切换 | 深浅色、空态 / 错误态、表单校验 |
| 性能测试 | 长对话流畅度 | 滚动帧率、流式输出卡顿 |

### 11.2 关键测试用例

- 深浅色切换后，所有自定义组件颜色正确切换，无残留浅色元素
- 空态 / 错误态在无数据、网络异常、配置无效三种场景下正确展示
- Markdown 未闭合代码块在流式过程中不崩溃、不渲染错乱
- 长对话（500 条消息）滚动无明显掉帧
- 字体缩放至最大时，核心页面布局不溢出

### 11.3 性能基准

- 流式输出时 UI 线程帧率目标 ≥ 50fps
- 冷启动首屏可见时间不差于 V1.0
- 长对话场景内存峰值不差于 V1.0

---

## 十二、目录结构

```
app/
├── design/                      # 设计系统
│   ├── ColorToken.kt
│   ├── DimensionToken.kt
│   └── TypographyToken.kt
├── theme/                       # 主题系统
│   ├── AppTheme.kt
│   └── ThemeMode.kt
├── components/                  # 基础组件库
│   ├── AppButton.kt
│   ├── AppTextField.kt
│   ├── AppCard.kt
│   ├── MessageBubble.kt
│   ├── EmptyState.kt
│   ├── LoadingState.kt
│   ├── AppDialog.kt
│   └── AppSnackbar.kt
├── markdown/                    # Markdown 渲染
│   ├── MarkdownText.kt
│   ├── CodeBlock.kt
│   └── MarkdownParser.kt
├── animation/                   # 动效
│   ├── AppAnimations.kt
│   └── CursorAnimation.kt
├── feature/
│   ├── session/                 # 会话列表
│   │   ├── SessionListScreen.kt
│   │   └── SessionListItem.kt
│   ├── chat/                    # 对话页
│   │   ├── ChatScreen.kt
│   │   └── ChatInputBar.kt
│   └── config/                  # 配置页
│       ├── ConfigScreen.kt
│       ├── ConfigSection.kt
│       └── ParameterSlider.kt
└── MainActivity.kt
```

---

## 十三、实施顺序建议

| 顺序 | 步骤 | 依赖 |
|------|------|------|
| 1 | 落地 Color / Dimension Token | 无 |
| 2 | 落地主题系统与持久化 | 步骤 1 |
| 3 | 抽取基础组件库 | 步骤 1、2 |
| 4 | 重构会话列表页 | 步骤 3 |
| 5 | 重构对话页 + Markdown | 步骤 3 |
| 6 | 重构配置页 | 步骤 3 |
| 7 | 动效与无障碍 | 步骤 4–6 |
| 8 | 性能优化与测试 | 全量 |

**关键约束**：先做设计系统与组件库，再做页面重构，避免各页面各自为政导致二次返工。

---

以上为 V1.1 完整开发文档。如需进一步展开，我可以补充：**Markdown 解析器的状态机设计**、**连接测试的错误码映射表**、**完整的 Compose 组件 API 清单**，或 **V1.1 的 PR 拆分与代码审查清单**。

*内容由 AI 生成仅供参考*