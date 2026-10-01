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
import org.json.JSONTokener
import java.util.concurrent.TimeUnit

class SourceRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    suspend fun loadManifest(url: String, headers: Map<String, String> = emptyMap()): SourceManifest = withContext(Dispatchers.IO) {
        val requestBuilder = Request.Builder().url(url)
        headers.forEach { (k, v) ->
            if (k.isNotBlank() && v.isNotBlank()) {
                requestBuilder.header(k, v)
            }
        }
        client.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                return@withContext SourceManifest(
                    name = url.substringAfterLast('/').ifBlank { "Remote Source" },
                    url = url,
                    error = "HTTP ${response.code}: ${response.message}"
                )
            }
            val body = response.body?.string().orEmpty()
            parseManifest(url, body)
        }
    }

    fun parseManifest(url: String, text: String): SourceManifest {
        val trimmed = text.trim()
        if (trimmed.isBlank()) {
            return SourceManifest(url = url, name = "Empty source", error = "Manifest content is empty")
        }

        val tokener = JSONTokener(trimmed)
        val root = runCatching { tokener.nextValue() }.getOrElse {
            return SourceManifest(url = url, name = "Invalid JSON", error = "Failed to parse JSON: ${it.message}")
        }

        var sourceName = "Personal Library"
        val itemsArray: JSONArray = when (root) {
            is JSONObject -> {
                sourceName = root.optString("name").ifBlank { root.optString("title", "Personal Library") }
                root.optJSONArray("items")
                    ?: root.optJSONArray("movies")
                    ?: root.optJSONArray("results")
                    ?: JSONArray()
            }
            is JSONArray -> root
            else -> return SourceManifest(url = url, name = "Unsupported format", error = "Expected JSON object or array")
        }

        val items = buildList {
            val seenIds = mutableSetOf<String>()
            for (i in 0 until itemsArray.length()) {
                val o = itemsArray.optJSONObject(i) ?: continue
                val rawId = o.optString("id").ifBlank { "${url}#${i}" }
                if (!seenIds.add(rawId)) continue // skip duplicates

                val streams = parseStreams(o)
                val directUrl = o.optString("streamUrl").ifBlank {
                    o.optString("stream_url").ifBlank { o.optString("url").ifBlank { null } }
                }

                val allStreams = if (streams.isNotEmpty()) streams
                else if (!directUrl.isNullOrBlank()) listOf(StreamVariant("Default", directUrl, o.optString("mimeType").ifBlank { null }))
                else emptyList()

                val subtitles = parseSubtitles(o)

                add(
                    MediaItem(
                        id = rawId,
                        title = o.optString("title").ifBlank { o.optString("name", "Untitled") },
                        type = o.optString("type", "Movie"),
                        year = o.optString("year").ifBlank { o.optString("release_date").take(4).ifBlank { null } },
                        poster = o.optString("poster").ifBlank { o.optString("posterUrl").ifBlank { null } },
                        backdrop = o.optString("backdrop").ifBlank { o.optString("backdropUrl").ifBlank { null } },
                        streamUrl = directUrl ?: allStreams.firstOrNull()?.url,
                        streams = allStreams,
                        subtitles = subtitles,
                        description = o.optString("description").ifBlank { o.optString("overview").ifBlank { null } },
                        genre = o.optString("genre").ifBlank { o.optString("genres").ifBlank { null } },
                        runtime = o.optString("runtime").ifBlank { null },
                        provider = o.optString("provider").ifBlank { sourceName },
                        mimeType = o.optString("mimeType").ifBlank { allStreams.firstOrNull()?.mimeType }
                    )
                )
            }
        }

        return SourceManifest(
            name = sourceName,
            url = url,
            items = items,
            error = null,
            lastRefreshed = System.currentTimeMillis()
        )
    }

    private fun parseStreams(obj: JSONObject): List<StreamVariant> {
        val array = obj.optJSONArray("streams") ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val s = array.optJSONObject(i) ?: continue
                val url = s.optString("url")
                if (url.startsWith("http", ignoreCase = true)) {
                    val quality = s.optString("quality").ifBlank { s.optString("name", "Stream ${i + 1}") }
                    val mime = s.optString("mimeType").ifBlank { null }
                    add(StreamVariant(quality = quality, url = url, mimeType = mime))
                }
            }
        }
    }

    private fun parseSubtitles(obj: JSONObject): List<SubtitleTrack> {
        val array = obj.optJSONArray("subtitles") ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val sub = array.optJSONObject(i) ?: continue
                val url = sub.optString("url")
                if (url.isNotBlank()) {
                    val label = sub.optString("label").ifBlank { sub.optString("language", "Subtitle ${i + 1}") }
                    val lang = sub.optString("language", "en")
                    val mime = sub.optString("mimeType", "text/vtt")
                    add(SubtitleTrack(label = label, url = url, language = lang, mimeType = mime))
                }
            }
        }
    }

    suspend fun loadAll(sources: List<SourceConfig>): List<SourceManifest> = coroutineScope {
        sources.filter { it.enabled && it.url.isNotBlank() }.map { config ->
            async(Dispatchers.IO) {
                runCatching {
                    val headers = if (config.authHeaderName.isNotBlank() && config.authHeaderValue.isNotBlank()) {
                        mapOf(config.authHeaderName to config.authHeaderValue)
                    } else emptyMap()
                    loadManifest(config.url, headers).copy(id = config.id, name = config.name.ifBlank { "Personal Library" })
                }.getOrElse {
                    SourceManifest(
                        id = config.id,
                        name = config.name.ifBlank { "Source" },
                        url = config.url,
                        error = it.message ?: "Failed to connect to source"
                    )
                }
            }
        }.awaitAll()
    }
}
