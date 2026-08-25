package com.aioshell.app.core.data.network

import com.aioshell.app.core.data.model.ChatConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
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
     * 流式对话，逐块 emit 增量文本。
     * 出错时异常将被映射为面向用户的 [ApiException]。
     */
    fun streamChat(config: ChatConfig, messages: List<RequestMessage>): Flow<String> = callbackFlow {
        val request = Request.Builder()
            .url(buildEndpoint(config.baseUrl))
            .post(buildRequestBody(config, messages).toRequestBody(JSON_MEDIA))
            .header("Authorization", "Bearer ${config.apiKey}")
            .header("Accept", "text/event-stream")
            .build()

        val call = okHttpClient.newCall(request)

        // 保存 ProducerScope 引用，在 IO 线程读流并推送
        val producer = this

        val job = launch(Dispatchers.IO) {
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
                        val delta = parseDelta(data) ?: continue
                        if (!producer.isClosedForSend) producer.trySend(delta)
                    }
                    producer.close()
                }
            } catch (e: Exception) {
                producer.close(ErrorMapper.mapException(e))
            }
        }
        awaitClose { job.cancel(); call.cancel() }
    }

    /** 解析单个 SSE data 块，提取增量文本；块非法或为错误时返回 null（错误抛出异常）。 */
    private fun parseDelta(data: String): String? {
        return try {
            val chunk = json.decodeFromString<ChatCompletionChunk>(data)
            chunk.error?.let { err -> throw ErrorMapper.fromApiError(err) }
            chunk.choices?.firstOrNull()?.delta?.content
        } catch (e: ApiException) {
            throw e
        } catch (e: Exception) {
            // 某些服务端返回非 JSON 说明性内容，忽略该块
            null
        }
    }

    /**
     * 连接测试：非流式发起一次最小请求，校验配置可用性。
     */
    suspend fun testConnection(config: ChatConfig): Boolean {
        val body = ChatCompletionRequest(
            model = config.model,
            messages = listOf(RequestMessage("user", "ping")),
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