package com.aioshell.app.core.data.network

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 多模态请求内容构造：抽象 OpenAI 兼容视觉格式。
 * 纯文本返回字符串，含图返回 content 数组（text + image_url）。
 */
object MultimodalContentBuilder {

    /** 将文本与图片(base64)构造为 message content。 */
    fun buildUserContent(text: String, imageBase64s: List<String>): JsonElement {
        if (imageBase64s.isEmpty()) return JsonPrimitive(text)
        val parts = buildList<JsonElement> {
            if (text.isNotBlank()) {
                add(buildJsonObject { put("type", "text"); put("text", text) })
            }
            imageBase64s.forEach { b64 ->
                add(
                    buildJsonObject {
                        put("type", "image_url")
                        put(
                            "image_url",
                            buildJsonObject { put("url", "data:image/jpeg;base64,$b64") },
                        )
                    }
                )
            }
        }
        return JsonArray(parts)
    }
}