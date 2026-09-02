package com.aioshell.app.core.data.repository

import com.aioshell.app.core.data.model.WebSearchResult
import com.aioshell.app.core.data.model.WebSearchSummary
import com.aioshell.app.core.data.network.ApiClient
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 联网搜索仓库：免费、免密钥的数据来源。
 * - 主用 DuckDuckGo Instant Answer API（无需 Key）。
 * - 补充 Wikipedia 搜索 API（中英文，按结果量补充）。
 * 仅做只读检索，结果注入系统提示作为真实上下文，不落库、不上传。
 */
@Singleton
class WebSearchRepository @Inject constructor(private val apiClient: ApiClient) {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 对外入口：抽取查询词 → 检索并合并来源 → 组装结构化上下文。
     * @param query 用户的原文或抽取出的搜索词。
     * @param maxResults 合并后的结果条数上限。
     * 失败/无结果时返回空上下文，绝不抛出，避免阻塞主对话。
     */
    suspend fun search(query: String, maxResults: Int = 6): WebSearchSummary {
        val q = query.trim()
        if (q.isBlank()) return WebSearchSummary("", q)
        val ddg = runCatching { fetchDuckDuckGo(q) }.getOrDefault(emptyList())
        val wiki = runCatching { fetchWikipedia(q) }.getOrDefault(emptyList())
        // 合并且按 URL 去重，DuckDuckGo 优先
        val merged = LinkedHashMap<String, WebSearchResult>()
        (ddg + wiki).forEach { r ->
            if (r.url.isNotBlank() && r.title.isNotBlank() && !merged.containsKey(r.url)) {
                merged[r.url] = r
            }
        }
        val results = merged.values.take(maxResults)
        if (results.isEmpty()) return WebSearchSummary("", q)
        val text = buildString {
            appendLine("以下是针对“${q}”的联网搜索结果，请优先采用其中的实时、可靠信息作答，并标注信息来源链接：")
            results.forEachIndexed { i, r ->
                appendLine("${i + 1}. ${r.title} · ${r.url}")
                if (r.snippet.isNotBlank()) appendLine("   摘要：${r.snippet.take(400)}")
            }
        }
        return WebSearchSummary(text, q, results)
    }

    /** DuckDuckGo Instant Answer：Abstract + RelatedTopics/Results 各取摘要。 */
    private suspend fun fetchDuckDuckGo(query: String): List<WebSearchResult> {
        val url = buildString {
            append("https://api.duckduckgo.com/?q=")
            append(URLEncoder.encode(query, "UTF-8"))
            append("&format=json&no_html=1&skip_disambig=1&t=aioshell")
        }
        val request = okhttp3.Request.Builder().url(url).get().build()
        apiClient.okHttpClient.newCall(request).execute().use { resp ->
            val body = resp.body?.string() ?: return emptyList()
            val root = json.parseToJsonElement(body).jsonObject
            val out = LinkedHashMap<String, WebSearchResult>()
            // 摘要主结果
            val abstractText = (root["AbstractText"] as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty()
            if (abstractText.isNotBlank()) {
                val title = ((root["Heading"] as? kotlinx.serialization.json.JsonPrimitive)?.content)
                    ?.takeIf { it.isNotBlank() } ?: query
                val url = ((root["AbstractURL"] as? kotlinx.serialization.json.JsonPrimitive)?.content).orEmpty()
                if (url.isNotBlank()) out[url] = WebSearchResult(title, url, abstractText)
            }
            // 相关/结果列表：可能嵌套 Topics
            (root["RelatedTopics"] as? JsonArray)?.forEach { item ->
                walkTopic(item, out, query)
            }
            (root["Results"] as? JsonArray)?.forEach { item ->
                walkTopic(item, out, query)
            }
            return out.values.toList()
        }
    }

    /** 递归展开 DuckDuckGo 的 RelatedTopics（可能含嵌套 Topics）。 */
    private fun walkTopic(item: kotlinx.serialization.json.JsonElement, out: MutableMap<String, WebSearchResult>, fallbackTitle: String) {
        when (item) {
            is JsonObject -> {
                val text = (item["Text"] as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty()
                val url = (item["FirstURL"] as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty()
                if (text.isNotBlank() && url.isNotBlank()) {
                    out[url] = WebSearchResult(cleanSnippet(text), url, cleanSnippet(text))
                }
                (item["Topics"] as? JsonArray)?.forEach { sub -> walkTopic(sub, out, fallbackTitle) }
            }
            is JsonArray -> item.forEach { walkTopic(it, out, fallbackTitle) }
            else -> Unit
        }
    }

    /** Wikipedia 搜索：标题 + 摘要片段（去除 HTML 标签）。 */
    private suspend fun fetchWikipedia(query: String): List<WebSearchResult> {
        val list = mutableListOf<WebSearchResult>()
        list += fetchWikipediaLang("zh", query, limit = 4)
        list += fetchWikipediaLang("en", query, limit = 2)
        return list
    }

    private suspend fun fetchWikipediaLang(lang: String, query: String, limit: Int): List<WebSearchResult> {
        val url = buildString {
            append("https://$lang.wikipedia.org/w/api.php?action=query&list=search&srsearch=")
            append(URLEncoder.encode(query, "UTF-8"))
            append("&format=json&srlimit=$limit")
        }
        val request = okhttp3.Request.Builder().url(url).get().build()
        apiClient.okHttpClient.newCall(request).execute().use { resp ->
            val body = resp.body?.string() ?: return emptyList()
            val root = json.parseToJsonElement(body).jsonObject
            val search = root["query"]?.jsonObject?.get("search") as? JsonArray ?: return emptyList()
            return search.mapNotNull { el ->
                val o = el.jsonObject
                val title = (o["title"] as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty()
                if (title.isBlank()) return@mapNotNull null
                // 摘要中可能是纯文本或带链接标签；统一清理
                val snippet = ((o["snippet"] as? kotlinx.serialization.json.JsonPrimitive)?.content).orEmpty()
                val pageUrl = "https://$lang.wikipedia.org/wiki/${URLEncoder.encode(title, "UTF-8")}"
                WebSearchResult(title, pageUrl, cleanSnippet(snippet))
            }
        }
    }

    /** 清理摘要中的 HTML 标签与多余空白。 */
    private fun cleanSnippet(text: String): String =
        text.replace(Regex("<\\/?[a-zA-Z][^>]*>"), "").trim()
}