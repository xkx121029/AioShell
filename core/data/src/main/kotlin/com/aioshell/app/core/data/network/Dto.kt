package com.aioshell.app.core.data.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** OpenAI 兼容协议请求体。 */
@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<RequestMessage>,
    @SerialName("temperature") val temperature: Float? = null,
    @SerialName("max_tokens") val maxTokens: Int? = null,
    @SerialName("top_p") val topP: Float? = null,
    val stream: Boolean = true,
)

@Serializable
data class RequestMessage(
    val role: String,
    val content: String,
)

/** 流式分块。 */
@Serializable
data class ChatCompletionChunk(
    val id: String? = null,
    val choices: List<Choice>? = null,
    val usage: Usage? = null,
    val error: ApiError? = null,
)

@Serializable
data class Choice(
    val index: Int? = null,
    val delta: Delta? = null,
    @SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable
data class Delta(
    val role: String? = null,
    val content: String? = null,
)

@Serializable
data class Usage(
    @SerialName("prompt_tokens") val promptTokens: Int? = null,
    @SerialName("completion_tokens") val completionTokens: Int? = null,
)

/** 非流式错误响应体。 */
@Serializable
data class ErrorEnvelope(
    val error: ApiError? = null,
)

@Serializable
data class ApiError(
    val message: String? = null,
    val type: String? = null,
    val code: String? = null,
    val param: JsonElement? = null,
)

/** 连接测试 / 非流式请求的响应。 */
@Serializable
data class ChatCompletionResponse(
    val id: String? = null,
    val model: String? = null,
    val choices: List<ContentChoice>? = null,
    val error: ApiError? = null,
)

@Serializable
data class ContentChoice(
    @SerialName("finish_reason") val finishReason: String? = null,
    val message: ResponseMessage? = null,
)

@Serializable
data class ResponseMessage(
    val role: String? = null,
    val content: String? = null,
)