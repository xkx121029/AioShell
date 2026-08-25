package com.aioshell.app.core.data.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 统一底层 HTTP 客户端。
 * - [okHttpClient]：用于 SSE 流式长连接（手动解析，避免对 Ktor SSE 版本 API 的依赖）。
 * - [ktorClient]：用于普通请求 / 连接测试。
 * 仅负责建连，鉴权头、请求体由 [ChatApi] 依据用户配置构建。
 */
@Singleton
class ApiClient @Inject constructor() {

    val okHttpClient: okhttp3.OkHttpClient by lazy {
        okhttp3.OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(0, java.util.concurrent.TimeUnit.MILLISECONDS) // 流式长连接，不设读超时
            .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    val ktorClient: HttpClient by lazy {
        HttpClient(OkHttp) {
            engine { preconfigured = okHttpClient }
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    encodeDefaults = true
                })
            }
            install(HttpTimeout) {
                connectTimeoutMillis = 15_000
            }
        }
    }
}