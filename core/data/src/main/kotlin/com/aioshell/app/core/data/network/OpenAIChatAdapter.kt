package com.aioshell.app.core.data.network

import com.aioshell.app.core.data.model.ChatConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * OpenAI 兼容协议适配器：负责构建 /chat/completions 请求，并解析 SSE 流式响应。
 * 纯 OkHttp 实现，避免对 Ktor SSE 版本 API 的耦合。
 */
class OpenAIChatAdapter(
    private val okHttpClient: okhttp3.OkHttpClient,
) {

    private companion object {
        const val CHAT_PATH = "/chat/completions"
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
        }
    }

    /** 归一化接口地址，追加 /chat/completions。 */
    fun buildEndpoint(baseUrl: String): String {
        val trimmed = baseUrl.trim().trimEnd('/')
        return "$trimmed$CHAT_PATH"
    }

    private fun buildRequestBody(config: ChatConfig, messages: List<RequestMessage>): String {
        val body = ChatCompletionRequest(
            model = config.model,
            messages = messages,
            temperature = config.temperature,
            maxTokens = config.maxTokens,
            topP = config.topP,
            stream = true,
        )
        return json.encodeToString(body)
    }

    /**
     * 流式对话，逐块 emit 增量文本（仅正文）。
     * 出错时异常将被映射为面向用户的 [ApiException]。
     */
    fun streamChat(config: ChatConfig, messages: List<RequestMessage>): Flow<String> =
        streamChatEvents(config, messages).filterIsInstance<ChatStreamEvent.ContentDelta>().map { it.text }

/**
     * 流式对话（含思考内容分流）。逐块 emit [ChatStreamEvent]。
     * 出错时异常将被映射为面向用户的 [ApiException]。
     */
    fun streamChatEvents(config: ChatConfig, messages: List<RequestMessage>): Flow<ChatStreamEvent> = callbackFlow {
        val request = Request.Builder()
            .url(buildEndpoint(config.baseUrl))
            .post(buildRequestBody(config, messages).toRequestBody(JSON_MEDIA))
            .header("Authorization", "Bearer ${config.apiKey}")
            .header("Accept", "text/event-stream")
            .build()

        val call = okHttpClient.newCall(request)
        val producer = this

        val job = launch(Dispatchers.IO) {
            val reasoningStart = System.currentTimeMillis()
            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        val errBody = response.body?.string().orEmpty()
                        producer.close(ErrorMapper.mapHttp(response.code, errBody))
                        return@use
                    }
                    val source = response.body?.source()
                    if (source == null) { producer.close(); return@use }
                    while (true) {
                        val line = source.readUtf8Line() ?: break
                        val trimmed = line.trim()
                        if (!trimmed.startsWith("data:")) continue
                        val data = trimmed.substring(5).trim()
                        if (data.isBlank()) continue
                        if (data == "[DONE]") break
                        val event = parseDelta(data) ?: continue
                        if (producer.isClosedForSend) continue
                        when (event) {
                            is ChatStreamEvent.ContentDelta -> producer.trySend(event)
                            is ChatStreamEvent.ReasoningDelta -> producer.trySend(event)
                            is ChatStreamEvent.Done -> { producer.trySend(event); break }
                        }
                    }
                    producer.trySend(ChatStreamEvent.Done(System.currentTimeMillis() - reasoningStart))
                    producer.close()
                }
            } catch (e: Exception) {
                producer.close(ErrorMapper.mapException(e))
            }
        }
        awaitClose { job.cancel(); call.cancel() }
    }

    /** 解析单个 SSE data 块，分流 reasoning / content；块非法或为错误时返回 null（错误抛出异常）。 */
    private fun parseDelta(data: String): ChatStreamEvent? {
        return try {
            val chunk = json.decodeFromString<ChatCompletionChunk>(data)
            chunk.error?.let { err -> throw ErrorMapper.fromApiError(err) }
            val delta = chunk.choices?.firstOrNull()?.delta
            delta?.content?.takeIf { it.isNotEmpty() }?.let { return ChatStreamEvent.ContentDelta(it) }
            val reasoning = delta?.let {
                it.reasoningContent ?: it.reasoning ?: it.thinking
            }?.takeIf { it.isNotEmpty() }
            reasoning?.let { return ChatStreamEvent.ReasoningDelta(it) }
            val finish = chunk.choices?.firstOrNull()?.finishReason
            if (!finish.isNullOrEmpty()) ChatStreamEvent.Done(0L) else null
        } catch (e: ApiException) {
            throw e
        } catch (e: Exception) {
            // 某些服务端返回非 JSON 说明性内容，忽略该块
            null
        }
    }
    /**
     * 非流式补全：一次性返回正文文本。
     * 供"杂项 AI"（命名/意图分析）等需要完整 JSON 结果的场景使用。
     * 出错时抛出映射好的 [ApiException]。
     */
    suspend fun complete(config: ChatConfig, messages: List<RequestMessage>): String {
        val body = ChatCompletionRequest(
            model = config.model,
            messages = messages,
            temperature = config.temperature,
            maxTokens = config.maxTokens,
            topP = config.topP,
            stream = false,
        )
        val request = Request.Builder()
            .url(buildEndpoint(config.baseUrl))
            .post(json.encodeToString(body).toRequestBody(JSON_MEDIA))
            .header("Authorization", "Bearer ${config.apiKey}")
            .build()
        return withContext(Dispatchers.IO) {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string().orEmpty()
                    throw ErrorMapper.mapHttp(response.code, errBody)
                }
                val text = response.body?.string().orEmpty()
                val resp = runCatching { json.decodeFromString<ChatCompletionResponse>(text) }
                    .getOrElse {
                        val env = runCatching { json.decodeFromString<ErrorEnvelope>(text) }.getOrNull()
                        env?.error?.let { throw ErrorMapper.fromApiError(it) }
                        throw ErrorMapper.mapHttp(response.code, text)
                    }
                resp.error?.let { throw ErrorMapper.fromApiError(it) }
                resp.choices?.firstOrNull()?.message?.content ?: ""
            }
        }
    }

    suspend fun testConnection(config: ChatConfig): Boolean {
        val body = ChatCompletionRequest(
            model = config.model,
            messages = listOf(RequestMessage("user", kotlinx.serialization.json.JsonPrimitive("ping"))),
            temperature = config.temperature,
            maxTokens = config.maxTokens,
            topP = config.topP,
            stream = false,
        )
        val request = Request.Builder()
            .url(buildEndpoint(config.baseUrl))
            .post(json.encodeToString(body).toRequestBody(JSON_MEDIA))
            .header("Authorization", "Bearer ${config.apiKey}")
            .build()

        kotlinx.coroutines.withContext(Dispatchers.IO) {
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) return@withContext true
                val errBody = response.body?.string().orEmpty()
                throw ErrorMapper.mapHttp(response.code, errBody)
            }
        }
        return true
    }
}