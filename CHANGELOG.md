# Changelog

本项目遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/) 规范，版本号遵循 [Semantic Versioning](https://semver.org/lang/zh-CN/)。

## [1.1.0] - 2026-08-25

### UI / UX 专项优化（无核心业务功能变更）

- **设计系统（Design Token）**
  - 新增语义化颜色 Token（`AppColorScheme`）与双主题映射（浅色 / 深色）。
  - 新增间距（4dp 网格）、圆角、动效时长 Token。
- **主题系统**
  - 支持浅色 / 深色 / 跟随系统三种模式，选择持久化（DataStore）。
  - 会话页顶栏主题切换入口。
- **基础组件库**
  - 抽取 `AppButton` / `AppTextField` / `AppCard` / `EmptyState` / `LoadingState` / `ErrorState` / `AppDialog` / `MessageBubble` 统一组件。
- **Markdown 渲染**
  - 自研轻量解析器，支持标题 / 列表 / 引用 / 粗体 / 斜体 / 行内代码 / 代码块 / 分隔线。
  - 流式过程中容忍未闭合语法。
- **动效**
  - 消息出现淡入上移、流式光标呼吸动画。
- **页面重构**
  - 会话列表：会话摘要预览、空态区分、统一卡片。
  - 对话页：Markdown 气泡、流式光标、长按菜单（复制 / 重新生成）。
  - 配置页：表单分组、参数滑块（temperature / top_p / max_tokens）、分色连接反馈。
- **无障碍**
  - 图标按钮语义化 contentDescription、触控目标 ≥ 48dp。

### 构建

- 版本号提升至 1.1.0（versionCode 2）。

## [1.0.0] - 2026-08-24

### 新增

- **接口配置**
  - 自定义 Base URL、API Key、模型名。
  - 参数调节：temperature、max_tokens、top_p。
  - 连接测试（校验配置可用性）。
  - 多接口档案保存与切换。
- **会话管理**
  - 多会话创建 / 切换 / 重命名 / 删除。
  - 历史会话本地持久化（Room）。
- **对话交互**
  - 上下文记忆（携带历史消息）。
  - SSE 流式输出（打字机效果），支持中途停止。
  - 复制回复内容。
  - 中文错误提示。
- **数据与安全**
  - API Key 使用 Android Keystore（AES/GCM）加密存储。
  - 对话数据纯本地，无任何对外上传（除用户自配接口）。
  - 无广告、仅申请网络权限。
- **体验**
  - Material 3 Expressive 视觉 + 自研「海玻璃 / 深渊翡翠」双主题。
  - 跟随系统的深色模式。
  - 可切换的启动器应用图标（含用户自定义素材）。

### 构建

- 技术栈：Kotlin / Jetpack Compose / Hilt / Room / DataStore / OkHttp + Ktor SSE。
- minSdk 26，targetSdk 35。