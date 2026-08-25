package com.aioshell.app.core.data.network

/** 流式对话事件：思考内容与正文内容分流。 */
sealed interface ChatStreamEvent {
    /** 思考内容增量。 */
    data class ReasoningDelta(val text: String) : ChatStreamEvent
    /** 正文内容增量。 */
    data class ContentDelta(val text: String) : ChatStreamEvent
    /** 流结束（携带总思考耗时）。 */
    data class Done(val reasoningDurationMs: Long) : ChatStreamEvent
}