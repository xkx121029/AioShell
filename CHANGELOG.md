# Changelog

本项目遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/) 规范，版本号遵循 [Semantic Versioning](https://semver.org/lang/zh-CN/)。

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