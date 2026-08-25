以下是《AioShell（HAS）V1.1.2 开发文档》完整文本。按语义化版本惯例，v1.1.2 定位为 **v1.1 的补丁迭代版本**：不新增核心功能，聚焦缺陷修复、体验细节打磨与性能微调，让 v1.1 的 UI/UX 成果在真实使用中更稳定、更细腻。如果你想让 v1.1.2 承载其他具体内容，告诉我即可调整。

---

# AioShell（内部代号 HAS）V1.1.2 开发文档

**文档版本**：V1.1.2
**项目名称**：AioShell（内部代号 HAS）
**目标平台**：Android
**版本主题**：补丁迭代（缺陷修复 + 体验细节打磨，不新增核心功能）
**文档日期**：2026-08-25

---

## 一、文档说明

### 1.1 版本定位

V1.1 完成了 UI/UX 专项优化，落地了设计系统、主题系统、基础组件库与三大页面重构。V1.1.2 是 V1.1 发布后的首个补丁迭代，目标是**消除上线后暴露的缺陷、打磨体验细节、微调性能**，为后续 V1.2 的功能扩展打一个干净的基线。

**核心原则：只做修复与打磨，不新增功能，不改变既有交互结构。**

### 1.2 迭代范围

| 类别 | 内容 |
|------|------|
| 缺陷修复 | 主题、滚动、Markdown、表单、内存等已发现问题 |
| 细节优化 | 输入体验、滚动反馈、列表排序、触觉反馈 |
| 性能微调 | 重组范围、列表 Key、冷启动 |
| 无障碍补强 | 焦点、朗读顺序、对比度 |
| 回归测试 | 全量回归 + 发布 |

### 1.3 前置基线

V1.1 已确定的技术栈与架构保持不变：Kotlin + Jetpack Compose + MVVM + Hilt，Room + DataStore，OkHttp + Retrofit/Ktor。本版本不动网络层与数据层结构，仅在 UI 与状态层做修复。

---

## 二、缺陷修复

### 2.1 深浅色切换闪烁

**问题现象**：切换主题时部分组件出现白闪或配色残留。

**原因分析**：部分 Composable 直接从 `isSystemInDarkTheme()` 或缓存色值取色，未统一走 `LocalAppColors`，切换时产生不一致。

**修复方案**

- 全项目清理残留的 `isSystemInDarkTheme()` 直接取色逻辑，统一通过 `AppTheme.colors` 取语义色
- 在主题根节点统一注入，切换时保持业务状态不重建

```kotlin
// 修复前：业务层自行判断暗色，易产生不一致
val bubbleColor = if (isSystemInDarkTheme()) Color(0xFF23272F) else Color(0xFFEDEFF3)

// 修复后：统一走语义 Token
val bubbleColor = AppTheme.colors.aiBubble
```

**验收**：反复切换浅色 / 深色 / 跟随系统，无闪烁、无残留。

### 2.2 主题切换后状态丢失

**问题现象**：切换主题时，对话页滚动位置、输入框草稿被重置。

**原因分析**：主题切换导致根 Composable 重组，部分状态因缺少 `rememberSaveable` 未保留。

**修复方案**

- 滚动位置改用 `rememberLazyListState` 并在根级持有，主题切换不重建
- 输入草稿用 `rememberSaveable` 保存，跨越重组与配置变更

```kotlin
val listState = rememberLazyListState()   // 提升到稳定作用域
var draft by rememberSaveable { mutableStateOf("") }
```

**验收**：切换主题后滚动位置与草稿保留，仅配色变化。

### 2.3 长对话滚动卡顿与不自跟

**问题现象**：消息较多时滚动掉帧；流式输出时列表不会自动跟随到底部。

**修复方案**

- 消息列表补充稳定 Key，避免无谓重组
- 流式输出期间，仅当用户停留在底部时才自动滚动，用户上翻时不强制回底

```kotlin
// 稳定 Key 避免重组
LazyColumn(state = listState) {
    items(messages, key = { it.id }) { message ->
        MessageBubble(message)
    }
}

// 智能跟随：仅底部附近时自动滚动
LaunchedEffect(lastMessageId) {
    val shouldAutoScroll = listState.layoutInfo.visibleItemsInfo.lastOrNull()
        ?.index == messages.lastIndex - 1 || isAtBottom()
    if (shouldAutoScroll) {
        listState.animateScrollToItem(messages.lastIndex)
    }
}
```

**验收**：长对话滚动流畅；用户上翻历史时不被强制拉回底部，处于底部时自动跟随。

### 2.4 Markdown 渲染边缘情况

**问题现象**：未闭合代码块、超长行、特殊字符导致渲染错乱或 UI 溢出。

**修复方案**

- 未闭合代码块 / 列表在流式过程中做降级处理，仅渲染已完成片段
- 超长行与超长代码块设置横向滚动与截断，防止撑破布局
- 对特殊字符做转义与边界保护

**验收**：流式输出过程中不断裂、不崩溃；超长内容可滚动不溢出。

### 2.5 配置页表单校验缺陷

**问题现象**：空字段、非法 URL、越界参数时提示缺失或滞后。

**修复方案**

- 统一校验时机：失焦时校验 + 提交时全量校验
- 校验规则集中到 ViewModel，UI 只呈现状态

```kotlin
data class ConfigFormState(
    val baseUrl: String = "",
    val baseUrlError: String? = null,
    val apiKeyError: String? = null,
    val temperatureError: String? = null,
)

fun validate(): Boolean {
    baseUrlError = when {
        baseUrl.isBlank() -> "接口地址不能为空"
        !baseUrl.startsWith("http") -> "接口地址需以 http/https 开头"
        else -> null
    }
    // ...
    return baseUrlError == null && apiKeyError == null && temperatureError == null
}
```

**验收**：非法输入有即时、明确的错误提示，且提示文案一致。

### 2.6 键盘遮挡输入框

**问题现象**：键盘弹起时输入框被遮挡，部分机型无法跟随。

**修复方案**

- 使用 `Modifier.imePadding()` 与 `WindowInsets` 适配键盘
- 对话页采用 `Scaffold` + `imePadding` 保证输入栏始终可见

```kotlin
Scaffold(
    modifier = Modifier.imePadding(),
    // ...
)
```

**验收**：键盘弹起 / 收起时输入框无遮挡，布局平滑跟随。

### 2.7 图片 / Composable 内存释放

**问题现象**：V1.1 虽无多模态，但空态图标、会话列表等存在潜在资源未释放风险。

**修复方案**

- 统一使用 `rememberAsyncImagePainter` 或受管资源加载，避免未受管 Bitmap
- 清理页面销毁时的未释放引用（如 infinite transition 的取消）

**验收**：内存占用稳定，无持续增长；页面反复进出无泄漏。

---

## 三、体验细节优化

### 3.1 输入框多行自适应

输入框从固定单行改为**随内容自动增高**，到达上限后内部滚动。

```kotlin
BasicTextField(
    value = draft,
    onValueChange = { draft = it },
    modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 44.dp, max = 120.dp),
    maxLines = 5,
)
```

**验收**：长文本输入时输入框平滑增高，超限后内部滚动。

### 3.2 滚动到底部按钮

当用户上翻历史且出现新消息时，显示"回到底部"悬浮按钮，替代强制回底。

```kotlin
val isAtBottom by remember {
    derivedStateOf {
        val info = listState.layoutInfo
        val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
        lastVisible >= info.totalItemsCount - 1
    }
}

AnimatedVisibility(visible = !isAtBottom) {
    FloatingActionButton(
        onClick = { scope.launch { listState.animateScrollToItem(messages.lastIndex) } },
        modifier = Modifier.align(Alignment.BottomEnd),
    ) {
        Icon(Icons.Outlined.ArrowDownward, contentDescription = "回到底部")
    }
}
```

**验收**：上翻时按钮出现，点击平滑回底，位于底部时自动隐藏。

### 3.3 会话列表排序与摘要

- 会话按最后活动时间倒序排列，最近的置顶
- 摘要截断规则统一：超过 N 字用省略号，代码内容摘要降级为"[代码]"占位

```kotlin
fun lastMessagePreview(content: String): String {
    return when {
        content.contains("```") -> "[代码]"
        content.length > 40 -> content.take(40) + "…"
        else -> content
    }
}
```

**验收**：列表排序符合直觉，摘要整洁一致。

### 3.4 触觉反馈

关键操作增加轻量触觉反馈，提升操作确认感：发送、复制成功、长按呼出菜单、删除确认。

```kotlin
val haptic = LocalHapticFeedback.current
// 复制成功
haptic.performHapticFeedback(HapticFeedbackType.LongPress)
```

**原则**：仅用于关键确认，不滥用，避免打扰。

**验收**：关键操作有触觉确认，静音模式下不产生异常。

### 3.5 空态与文案统一

- 首启空态、无会话空态、错误态文案统一由文案常量管理，消除各页面表述不一致
- 文案风格统一为"状态 + 可执行动作"句式

**验收**：同类状态在各页面文案风格一致。

---

## 四、性能微调

### 4.1 重组范围收敛

- 将对话页拆分为顶栏、消息列表、输入栏三个独立稳定 Composable，缩小重组范围
- 流式输出仅重组最后一条消息，历史消息保持稳定

**验收**：流式输出时重组范围可控，无整页重组。

### 4.2 列表稳定 Key

- 会话列表、消息列表统一使用稳定 Key（实体 id），避免按位置重组

**验收**：列表项增删时无错位重组与动画闪烁。

### 4.3 冷启动优化

- 首屏先渲染骨架（占位），主题与配置异步加载完成后填充
- 非首屏模块延迟初始化

```kotlin
// 骨架屏：数据未就绪时展示
if (uiState.isLoading) {
    SkeletonList()
} else {
    SessionList(uiState.sessions)
}
```

**验收**：冷启动首屏可见时间不劣于 V1.1，无白屏。

---

## 五、无障碍补强

### 5.1 焦点与朗读顺序

- 对话页焦点顺序：输入框 → 发送按钮 → 顶栏操作
- 消息朗读顺序按时间正序，避免读屏跳跃
- 动态内容（流式输出）不打断读屏，仅在生成完成后播报

**验收**：TalkBack 下核心流程可完整操作，焦点顺序合理。

### 5.2 对比度微调

- 复查辅助文字（`secondary` 色）在深浅色下的对比度，对不达标的色值微调
- 辅助文字对比度目标 ≥ 4.5:1，大字号 ≥ 3:1

**验收**：深浅色下辅助文字均满足 WCAG AA。

### 5.3 动态状态描述

流式生成、连接测试等动态状态通过语义层告知读屏用户。

```kotlin
Modifier.semantics {
    liveRegion = LiveRegionMode.Polite
    contentDescription = "正在生成回复"
}
```

**验收**：读屏能感知动态状态变化，不重复播报。

---

## 六、测试与回归

### 6.1 缺陷回归

对 2.x 全部修复项逐项回归，确认无复发。

### 6.2 全量回归清单

| 模块 | 回归点 |
|------|--------|
| 主题 | 三模式切换、无闪烁、状态保留 |
| 会话列表 | 创建 / 切换 / 重命名 / 删除 / 排序 |
| 对话页 | 流式输出、滚动跟随、回底按钮、复制 |
| 配置页 | 表单校验、连接测试、多档案切换 |
| Markdown | 代码块、列表、未闭合降级、超长内容 |
| 键盘 | 输入框不遮挡、自适应增高 |
| 无障碍 | TalkBack 流程、焦点、对比度 |

### 6.3 性能验收

- 长对话（500 条消息）滚动与流式输出帧率 ≥ 50fps
- 冷启动首屏时间不劣于 V1.1
- 反复进出页面无内存泄漏

### 6.4 兼容性验收

- Android 8.0 至最新版本核心流程可用
- 深浅色下新增与修改组件均正常

---

## 七、发布

### 7.1 发布前核对

- [ ] 全部缺陷修复项回归通过
- [ ] 全量回归清单无阻断问题
- [ ] 性能与无障碍验收达标
- [ ] CHANGELOG 记录本次修复与优化内容
- [ ] 版本号与构建号正确

### 7.2 发布内容

- 更新 CHANGELOG（v1.1.2 修复与优化明细）
- 打包签名 APK
- 发布 GitHub Release，标注为补丁迭代

### 7.3 CHANGELOG 模板

```markdown
## v1.1.2

### 修复
- 修复深浅色切换闪烁与配色残留
- 修复主题切换后滚动位置与草稿丢失
- 修复长对话滚动卡顿与不自跟
- 修复 Markdown 未闭合代码块渲染错乱
- 修复配置页表单校验提示缺失
- 修复键盘遮挡输入框
- 修复潜在内存释放问题

### 优化
- 输入框多行自适应
- 新增"回到底部"按钮
- 会话列表排序与摘要优化
- 关键操作触觉反馈
- 空态与文案统一
- 无障碍焦点、朗读顺序与对比度补强
```

---

## 八、实施顺序

| 顺序 | 步骤 | 依赖 |
|------|------|------|
| 1 | 缺陷修复（主题、状态保留优先） | 无 |
| 2 | 滚动与 Markdown 修复 | 步骤 1 |
| 3 | 表单与键盘修复 | 无 |
| 4 | 细节优化（输入、回底、排序、触觉） | 步骤 2 |
| 5 | 无障碍补强 | 步骤 4 |
| 6 | 性能微调与全量回归 | 全量 |

**关键约束**：缺陷修复优先于细节优化，先保证稳定再谈细腻；回归测试贯穿全流程，避免修复引入新问题。

---

以上为 V1.1.2 完整开发文档。如需进一步展开，我可以补充**缺陷修复的具体 PR 拆分**、**回归测试用例清单**，或**按日分配的迭代排期**。

*内容由 AI 生成仅供参考*