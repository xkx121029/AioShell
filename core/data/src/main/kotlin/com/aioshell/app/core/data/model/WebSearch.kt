package com.aioshell.app.core.data.model

/** 单条联网搜索结果。 */
data class WebSearchResult(
    val title: String,
    val url: String,
    val snippet: String,
)

/** 联网搜索结果汇总：结构化片段，注入到 system 提示中作为真实上下文。 */
data class WebSearchSummary(
    /** 提示给模型的结构化文本；无结果或失败时为空。 */
    val context: String,
    /** 本次搜索抽取出的查询词。 */
    val query: String,
    val results: List<WebSearchResult> = emptyList(),
)