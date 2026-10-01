package com.simplestream.app

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.net.URLEncoder

data class UiState(
    val settings: AppSettings = AppSettings(),
    val query: String = "",
    val selectedMovie: MediaItem? = null,
    val personalMovies: List<MediaItem> = emptyList(),
    val continueWatching: List<WatchProgress> = emptyList(),
    val watchlist: List<MediaItem> = emptyList(),
    val favorites: List<MediaItem> = emptyList(),
    val results: List<MediaItem> = emptyList(),
    val homeSections: List<HomeSection> = emptyList(),
    val sources: List<SourceManifest> = emptyList(),
    val loading: Boolean = false,
    val message: String? = null
)

class SimpleStreamViewModel(app: Application) : AndroidViewModel(app) {
    private val store = SettingsStore(app)
    private val playbackRepo = PlaybackRepository(app)
    private val sourceRepo = SourceRepository()
    private val tmdb = TmdbRepository()
    private val client = OkHttpClient.Builder().retryOnConnectionFailure(true).build()

    private val stateFlow = MutableStateFlow(UiState(settings = store.load()))
    val state: StateFlow<UiState> = stateFlow

    init {
        refreshAll()
    }

    fun refreshAll() {
        refreshUserData()
        refreshSources()
    }

    fun refreshUserData() {
        viewModelScope.launch {
            val cw = playbackRepo.getAllContinueWatching()
            val wl = playbackRepo.getWatchlist()
            val fav = playbackRepo.getFavorites()
            stateFlow.value = stateFlow.value.copy(
                continueWatching = cw,
                watchlist = wl,
                favorites = fav
            )
        }
    }

    fun selectMovie(item: MediaItem?) {
        stateFlow.value = stateFlow.value.copy(selectedMovie = item)
    }

    fun getProgress(movieId: String): WatchProgress? = playbackRepo.getProgress(movieId)

    fun isWatchlist(movieId: String): Boolean = playbackRepo.isWatchlist(movieId)

    fun isFavorite(movieId: String): Boolean = playbackRepo.isFavorite(movieId)

    fun toggleWatchlist(item: MediaItem) {
        playbackRepo.toggleWatchlist(item)
        refreshUserData()
    }

    fun toggleFavorite(item: MediaItem) {
        playbackRepo.toggleFavorite(item)
        refreshUserData()
    }

    fun clearProgress(movieId: String) {
        playbackRepo.deleteProgress(movieId)
        refreshUserData()
    }

    fun setQuery(value: String) {
        stateFlow.value = stateFlow.value.copy(query = value)
        if (value.isBlank()) {
            stateFlow.value = stateFlow.value.copy(results = emptyList())
        }
    }

    fun refreshSources() {
        viewModelScope.launch {
            stateFlow.value = stateFlow.value.copy(loading = true)
            val configuredSources = stateFlow.value.settings.sources
            val loadedManifests = if (configuredSources.isNotEmpty()) {
                sourceRepo.loadAll(configuredSources)
            } else emptyList()

            // Combine default personal movie library with loaded manifests
            val manifestMovies = loadedManifests.flatMap { it.items }
            val combined = (DefaultMovieLibrary.items + manifestMovies).distinctBy { it.id }

            // Group into home sections
            val sections = mutableListOf<HomeSection>()
            sections.add(HomeSection("Featured Movies", combined.take(6)))

            val byGenre = combined.groupBy { it.genre?.split(",")?.firstOrNull()?.trim() ?: "General" }
            byGenre.filter { it.value.size >= 2 }.forEach { (genre, list) ->
                sections.add(HomeSection(genre, list))
            }

            stateFlow.value = stateFlow.value.copy(
                sources = loadedManifests,
                personalMovies = combined,
                homeSections = sections,
                loading = false
            )
        }
    }

    fun search() {
        val query = stateFlow.value.query.trim()
        if (query.isBlank()) return
        viewModelScope.launch {
            stateFlow.value = stateFlow.value.copy(loading = true, message = null)
            val current = stateFlow.value

            // 1. First search personal movie library
            val localMatches = current.personalMovies.filter {
                it.title.contains(query, ignoreCase = true) ||
                (it.genre?.contains(query, ignoreCase = true) == true) ||
                (it.description?.contains(query, ignoreCase = true) == true)
            }

            // 2. Query configured external databases (e.g. TVmaze, Archive) or TMDB if keys configured
            val externalMatches = coroutineScope {
                val jobs = mutableListOf<kotlinx.coroutines.Deferred<List<MediaItem>>>()
                if (current.settings.tmdbApiKey.isNotBlank() || current.settings.tmdbAccessToken.isNotBlank()) {
                    jobs += async { tmdb.search(query, current.settings) }
                }
                current.settings.databases.filter { it.enabled && it.endpoint.isNotBlank() }.forEach { db ->
                    jobs += async { searchDatabase(db, query) }
                }
                jobs.awaitAll().flatten()
            }

            val allResults = (localMatches + externalMatches).distinctBy { it.id }
            stateFlow.value = current.copy(
                results = allResults,
                loading = false,
                message = if (allResults.isEmpty()) "No movies found matching \"$query\"" else null
            )
        }
    }

    private suspend fun searchDatabase(db: DatabaseConfig, query: String): List<MediaItem> = runCatching {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val separator = if (db.endpoint.contains("?")) "&" else "?"
        var url = db.endpoint
        if (url.contains("{query}", true)) url = url.replace("{query}", encoded, ignoreCase = true)
        else url += separator + db.searchParameter + "=" + encoded

        val requestBuilder = Request.Builder().url(url)
        when (db.credentialMode) {
            CredentialMode.API_KEY_QUERY -> if (db.credential.isNotBlank()) {
                requestBuilder.url(url + if (url.contains("?")) "&api_key=" + db.credential else "?api_key=" + db.credential)
            }
            CredentialMode.API_KEY_HEADER -> if (db.credential.isNotBlank()) requestBuilder.header("X-API-Key", db.credential)
            CredentialMode.BEARER -> if (db.credential.isNotBlank()) requestBuilder.header("Authorization", "Bearer " + db.credential)
            CredentialMode.NONE -> Unit
        }
        client.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            parseGenericResults(db.name, response.body?.string().orEmpty())
        }
    }.getOrDefault(emptyList())

    private fun parseGenericResults(provider: String, text: String): List<MediaItem> {
        val root = runCatching { JSONTokener(text).nextValue() }.getOrNull() ?: return emptyList()
        val array = when (root) {
            is JSONArray -> root
            is JSONObject -> root.optJSONArray("results")
                ?: root.optJSONArray("items")
                ?: root.optJSONArray("data")
                ?: root.optJSONObject("response")?.optJSONArray("docs")
                ?: JSONArray()
            else -> JSONArray()
        }
        return buildList {
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                add(MediaItem(
                    id = provider + ":" + o.optString("id", i.toString()),
                    title = o.optString("title").ifBlank { o.optString("name", "Untitled") },
                    type = o.optString("type", o.optString("media_type", "Movie")),
                    year = o.optString("year").ifBlank { o.optString("release_date").take(4).ifBlank { null } },
                    poster = o.optString("poster").ifBlank { o.optString("poster_url").ifBlank {
                        o.optJSONObject("image")?.let { image -> image.optString("original").ifBlank { image.optString("medium") } }.orEmpty()
                    } },
                    streamUrl = o.optString("streamUrl").ifBlank { o.optString("url").ifBlank { null } },
                    description = o.optString("description").ifBlank { o.optString("overview").ifBlank { null } },
                    provider = provider
                ))
            }
        }
    }

    fun playMovie(item: MediaItem, variant: StreamVariant? = null, positionMs: Long = 0L) {
        val chosenStream = variant ?: item.allStreams.firstOrNull() ?: return
        val context = getApplication<Application>()
        val intent = Intent(context, PlayerActivity::class.java).apply {
            putExtra("movieId", item.id)
            putExtra("title", item.title)
            putExtra("poster", item.poster)
            putExtra("url", chosenStream.url)
            putExtra("mimeType", chosenStream.mimeType ?: item.mimeType)
            putExtra("positionMs", positionMs)
            putExtra("drmScheme", item.drmScheme)
            putExtra("drmLicenseUrl", item.drmLicenseUrl)
            item.subtitles.firstOrNull()?.let { sub ->
                putExtra("subtitleUrl", sub.url)
                putExtra("subtitleLanguage", sub.language)
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun playStreamEntry(entry: StreamEntry) {
        val context = getApplication<Application>()
        val intent = Intent(context, PlayerActivity::class.java).apply {
            putExtra("movieId", entry.id)
            putExtra("title", entry.title)
            putExtra("url", entry.url)
            putExtra("mimeType", entry.mimeType)
            putExtra("drmScheme", entry.drmScheme)
            putExtra("drmLicenseUrl", entry.drmLicenseUrl)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun addPersonalSource(name: String, url: String, headerName: String = "", headerValue: String = "") {
        val current = stateFlow.value.settings
        val newSource = SourceConfig(
            id = "src_" + System.currentTimeMillis(),
            name = name.trim().ifBlank { "Personal Library" },
            url = url.trim(),
            authHeaderName = headerName.trim(),
            authHeaderValue = headerValue.trim(),
            enabled = true
        )
        val updatedSources = current.sources + newSource
        val newSettings = current.copy(sources = updatedSources, sourceUrls = updatedSources.map { it.url })
        store.save(newSettings)
        stateFlow.value = stateFlow.value.copy(settings = newSettings, message = "Source added")
        refreshSources()
    }

    fun deleteSource(sourceId: String) {
        val current = stateFlow.value.settings
        val updatedSources = current.sources.filterNot { it.id == sourceId }
        val newSettings = current.copy(sources = updatedSources, sourceUrls = updatedSources.map { it.url })
        store.save(newSettings)
        stateFlow.value = stateFlow.value.copy(settings = newSettings, message = "Source removed")
        refreshSources()
    }

    fun attachStreamToMovie(item: MediaItem, streamUrl: String) {
        val current = stateFlow.value
        val updatedItem = item.copy(
            streamUrl = streamUrl,
            streams = listOf(StreamVariant("Manual Stream", streamUrl))
        )
        // If this movie was selected, update selection
        if (current.selectedMovie?.id == item.id) {
            stateFlow.value = current.copy(selectedMovie = updatedItem)
        }
        // Also save to added streams
        addStream(StreamEntry(
            id = "stream_" + item.id,
            title = item.title,
            url = streamUrl,
            provider = "Attached to ${item.title}"
        ))
    }

    fun addStream(entry: StreamEntry) {
        val s = stateFlow.value.settings
        val updated = (s.streams.filterNot { it.id == entry.id } + entry).distinctBy { it.id }
        val next = s.copy(streams = updated)
        store.save(next)
        stateFlow.value = stateFlow.value.copy(settings = next, message = "Stream saved")
    }

    fun deleteStream(streamId: String) {
        val s = stateFlow.value.settings
        val updated = s.streams.filterNot { it.id == streamId }
        val next = s.copy(streams = updated)
        store.save(next)
        stateFlow.value = stateFlow.value.copy(settings = next, message = "Stream removed")
    }

    fun saveSettings(apiKey: String, token: String, sourceText: String, databases: List<DatabaseConfig> = stateFlow.value.settings.databases) {
        val old = stateFlow.value.settings
        val legacyUrls = sourceText.lines().map(String::trim).filter(String::isNotBlank).distinct()
        val sources = legacyUrls.mapIndexed { i, url ->
            old.sources.firstOrNull { it.url == url } ?: SourceConfig("src_$i", "Library ${i + 1}", url)
        }
        val settings = AppSettings(
            tmdbApiKey = apiKey.trim(),
            tmdbAccessToken = token.trim(),
            sourceUrls = legacyUrls,
            sources = sources,
            databases = databases,
            streams = old.streams
        )
        store.save(settings)
        stateFlow.value = stateFlow.value.copy(settings = settings, message = "Settings saved")
        refreshSources()
    }
}
