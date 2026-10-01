package com.simplestream.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

class SourceRepository {
    private val client = OkHttpClient.Builder().retryOnConnectionFailure(true).build()

    suspend fun loadManifest(url: String): SourceManifest = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            require(response.isSuccessful) { "HTTP ${response.code}" }
            parseManifest(url, response.body?.string().orEmpty())
        }
    }

    private fun parseManifest(url: String, text: String): SourceManifest {
        val root = JSONObject(text)
        val name = root.optString("name", root.optString("title", "Source"))
        val array = root.optJSONArray("items") ?: root.optJSONArray("results") ?: JSONArray()
        val items = buildList {
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                add(MediaItem(
                    id = o.optString("id", "${url}#${i}"),
                    title = o.optString("title").ifBlank { o.optString("name", "Untitled") },
                    type = o.optString("media_type", o.optString("type", "Video")),
                    year = o.optString("year").ifBlank { o.optString("release_date").take(4).ifBlank { null } },
                    poster = o.optString("poster").ifBlank { o.optString("posterUrl").ifBlank { null } },
                    streamUrl = o.optString("streamUrl").ifBlank {
                        o.optString("stream_url").ifBlank { o.optString("url").ifBlank { null } }
                    },
                    description = o.optString("description").ifBlank { o.optString("overview").ifBlank { null } }
                ))
            }
        }
        return SourceManifest(name, url, items)
    }

    suspend fun loadAll(urls: List<String>): List<SourceManifest> = coroutineScope {
        urls.distinct().map { url ->
            async(Dispatchers.IO) {
                runCatching { loadManifest(url) }.getOrElse {
                    SourceManifest(url.substringAfterLast('/').ifBlank { "Source" }, url, error = it.message ?: "Load failed")
                }
            }
        }.awaitAll()
    }
}
