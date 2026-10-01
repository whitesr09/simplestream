package com.simplestream.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

class TmdbRepository {
    private val client = OkHttpClient()

    suspend fun search(query: String, settings: AppSettings): List<MediaItem> = withContext(Dispatchers.IO) {
        if (query.isBlank() || settings.tmdbApiKey.isBlank()) return@withContext emptyList()
        val encoded = URLEncoder.encode(query, "UTF-8")
        val base = "https://api.themoviedb.org/3/search/multi?query=$encoded&include_adult=false"
        val builder = Request.Builder()
        if (settings.tmdbAccessToken.isNotBlank()) builder.url(base).header("Authorization", "Bearer ${settings.tmdbAccessToken}")
        else builder.url(base + "&api_key=${settings.tmdbApiKey}")
        client.newCall(builder.build()).execute().use { httpResult ->
            if (!httpResult.isSuccessful) return@withContext emptyList()
            val results = JSONObject(httpResult.body?.string().orEmpty()).optJSONArray("results") ?: return@withContext emptyList()
            buildList {
                for (i in 0 until results.length()) {
                    val o = results.optJSONObject(i) ?: continue
                    val mediaType = o.optString("media_type", "movie")
                    if (mediaType != "movie" && mediaType != "tv") continue
                    val date = o.optString("release_date").ifBlank { o.optString("first_air_date") }
                    add(MediaItem(
                        id = o.optString("id"),
                        title = o.optString("title").ifBlank { o.optString("name", "Untitled") },
                        type = if (mediaType == "tv") "TV" else "Movie",
                        year = date.take(4).ifBlank { null },
                        poster = o.optString("poster_path").takeIf { it.isNotBlank() }?.let { "https://image.tmdb.org/t/p/w500$it" },
                        description = o.optString("overview").ifBlank { null }
                    ))
                }
            }
        }
    }
}