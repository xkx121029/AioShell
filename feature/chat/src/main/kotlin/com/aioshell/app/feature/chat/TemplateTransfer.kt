package com.aioshell.app.feature.chat

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 模板选择结果的跨页面传递：
 * 模板管理页选择"使用"后写入 [pending]，对话页在恢复组合时读取并预填输入框，随后清空。
 * 使用内存态而非导航参数，避免跨页面序列化依赖。
 */
object TemplateTransfer {
    val pending = MutableStateFlow<String?>(null)
}
