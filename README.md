# AioShell（内部代号 HAS）

> 一款安卓平台的**开源、私有、零绑定** AI 对话客户端外壳。

AioShell **不内置任何大模型、不提供任何后端服务**。你只需要自行配置一个兼容 OpenAI 协议的接口（Base URL / API Key / 模型名），就能在多会话中以流式（打字机）方式与你的大模型对话，且**所有对话数据仅保存在本机**。

---

## 特性

- **自由无绑定**：连接任何 OpenAI 兼容服务（AIO 系列、Ollama、自建网关、其他兼容网关）。
- **多接口档案**：保存多套 Base URL / Key / 模型 / 参数配置，一键切换。
- **多会话管理**：创建 / 切换 / 重命名 / 删除，历史会话本地持久化。
- **流式对话（SSE）**：逐块渲染的打字机效果，支持中途停止。
- **上下文记忆**：自动携带历史消息。
- **参数可调**：temperature、max_tokens、top_p。
- **连接测试**：保存前校验配置可用性。
- **本地安全**：API Key 采用 Android Keystore 加密存储，绝不写日志、绝不上传。
- **语音输入**：Vosk 离线语音识别（本地推理不联网），模型应用内引导下载，识别结果本地完成。
- **深色模式**：跟随系统。
- **可切换应用图标**：内置多种启动器图标供选择（含用户自定义素材）。
- **纯净零广告**：仅申请必要权限（网络 / 语音识别时的录音），无后台采集。

## 界面

遵循 **Material 3 Expressive** 视觉规范，采用自研「海玻璃 / 深渊翡翠」双主题配色，拒绝千篇一律的 AI 紫渐变。

## 免责声明 / 合规（必读）

> 本软件仅为前端交互工具，**不内置任何大模型，不提供任何 API 接口服务**。AI 推理能力全部来自用户主动配置的第三方接口。请保证你对所使用的接口拥有合法权限并自行承担相应的内容与合规责任。详见 [PRIVACY.md](PRIVACY.md)。

## 技术栈

| 模块 | 选型 |
| --- | --- |
| 语言 | Kotlin |
| UI | Jetpack Compose + Material 3 |
| 架构 | MVVM + Repository + Hilt |
| 网络 | OkHttp + Ktor（SSE 流式） |
| 异步 | Kotlin Coroutines + Flow |
| 存储 | Room + DataStore |
| 安全 | Android Keystore（AES/GCM） |

```
App（入口 / 导航 / 图标切换）
├── core:data     网络适配层 + SSE / Room / DataStore / Keystore / 仓库
├── core:ui       主题（M3 Expressive）/ 通用组件
├── feature:config  接口配置
├── feature:chat    对话（流式）
└── feature:session 会话列表
```

## 构建

```bash
# 要求：JDK 17 + Android SDK（compileSdk 35）
./gradlew :app:assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

## 使用

1. 打开 App → 右上角「接口配置」。
2. 新增一个档案：填写 Base URL、API Key、模型名与可选参数 →「连接测试」→「保存」。
3. 返回主页，点击右下角「＋」新建会话，开始对话。

## 常见问题

**为什么没有内置任何模型？**
因为项目理念是「做交互层、不做模型层」，由用户完全掌控自己的接口与数据。

**API Key 安全吗？**
安全。Key 仅以密文存于 DataStore（密钥在 Android Keystore），运行时解密后使用，不写日志、不上传第三方。

## 贡献

欢迎任何形式的贡献——提 Issue、修文档、加特性或指正问题皆可。请遵循以下最小约定：

1. Fork 本仓库并新建特性分支。
2. 提交信息使用清晰、语义化的中文或英文描述。
3. 提交前确保 `./gradlew :app:assembleDebug` 可正常构建。
4. 通过 Pull Request 提交，并在描述中说明改动动机。

## License

本项目基于 [Apache License 2.0](LICENSE) 开源，详见 [LICENSE](LICENSE)。

## 更新日志

见 [CHANGELOG.md](CHANGELOG.md)。

## 作者与维护者

- **xkx121029** · GitHub / Gitee：[@xkx121029](https://github.com/xkx121029)