package com.simplestream.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

class SourceRepository {
    private val client = OkHttpClient()

    suspend fun loadManifest(url: String): SourceManifest = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { httpResult ->
            require(httpResult.isSuccessful) { "HTTP ${httpResult.code}" }
            parseManifest(url, httpResult.body?.string().orEmpty())
        }
    }

    private fun parseManifest(url: String, text: String): SourceManifest {
        val root = JSONObject(text)
        val name = root.optString("name", "Source")
        val array = root.optJSONArray("items") ?: JSONArray()
        val items = buildList {
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                add(MediaItem(
                    id = o.optString("id", url + "#" + i),
                    title = o.optString("title", "Untitled"),
                    type = o.optString("type", "Video"),
                    year = o.optString("year").ifBlank { null },
                    poster = o.optString("poster").ifBlank { null },
                    streamUrl = o.optString("streamUrl").ifBlank { null },
                    description = o.optString("description").ifBlank { null }
                ))
            }
        }
        return SourceManifest(name, url, items)
    }

    suspend fun loadAll(urls: List<String>): List<SourceManifest> =
        urls.mapNotNull { runCatching { loadManifest(it) }.getOrNull() }
}