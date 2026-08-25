package com.aioshell.app.core.data.network

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/** 面向用户展示的网络异常，message 一律使用中文。 */
class ApiException(message: String, val code: Int? = null) : Exception(message)

object ErrorMapper {
    private val json = Json { ignoreUnknownKeys = true }

    fun mapHttp(statusCode: Int, body: String): ApiException {
        val serverMsg = runCatching {
            json.decodeFromString<ErrorEnvelope>(body).error?.message
        }.getOrNull()?.takeIf { it.isNotBlank() }

        val base = serverMsg ?: when (statusCode) {
            401, 403 -> "鉴权失败：请检查 API Key 是否正确"
            404 -> "接口地址错误：未找到对应资源（请确认 Base URL）"
            429 -> "请求过于频繁或额度不足，请稍后再试"
            500, 502, 503, 504 -> "服务端异常，请稍后再试"
            else -> "请求失败（HTTP ${statusCode}）"
        }
        return ApiException(base, statusCode)
    }

    fun fromApiError(err: ApiError): ApiException =
        ApiException(err.message?.takeIf { it.isNotBlank() } ?: "请求返回错误")

    fun mapException(e: Throwable): ApiException = when (e) {
        is ApiException -> e
        is java.net.ConnectException -> ApiException("无法连接到服务器，请检查网络与接口地址")
        is java.net.UnknownHostException -> ApiException("域名无法解析，请检查 Base URL 是否正确")
        is java.net.SocketTimeoutException -> ApiException("连接超时，请稍后再试")
        else -> ApiException(e.message ?: "网络请求异常：${e::class.simpleName}")
    }
}