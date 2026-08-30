package com.aioshell.app.core.data.model

import kotlinx.serialization.Serializable

/**
 * 杂项 AI 档案：用于对话命名 + 意图分析 + 生成身份/回答风格建议。
 * 与聊天主模型相互独立，用户可单独配置 baseUrl / apiKey / model / prompt。
 */
data class MiscAiConfig(
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    /** 自定义提示词前缀；为空时使用内置默认提示。 */
    val prompt: String = "",
    /** 是否启用杂项 AI（关闭时回退使用当前聊天模型）。 */
    val enabled: Boolean = false,
)

/** 持久化形态：[apiKey] 以密文(base64)保存。 */
@Serializable
data class PersistedMiscAi(
    val baseUrl: String = "",
    val apiKeyCipher: String = "",
    val model: String = "",
    val prompt: String = "",
    val enabled: Boolean = false,
)

/**
 * 杂项 AI 返回的结构化元数据（须返回纯 JSON）。
 * [title] 对话标题；[intent] 用户意图摘要；
 * [identity] 建议主模型采用的身份；[style] 建议主模型采用的回答风格。
 */
@Serializable
data class MiscAiMeta(
    val title: String = "",
    val intent: String = "",
    val identity: String = "",
    val style: String = "",
)