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
    val results: List<MediaItem> = emptyList(),
    val sources: List<SourceManifest> = emptyList(),
    val loading: Boolean = false,
    val message: String? = null
)

class SimpleStreamViewModel(app: Application) : AndroidViewModel(app) {
    private val store = SettingsStore(app)
    private val sourceRepo = SourceRepository()
    private val tmdb = TmdbRepository()
    private val client = OkHttpClient.Builder().retryOnConnectionFailure(true).build()
    private val stateFlow = MutableStateFlow(UiState(settings = store.load()))
    val state: StateFlow<UiState> = stateFlow

    init { refreshSources() }

    fun setQuery(value: String) { stateFlow.value = stateFlow.value.copy(query = value) }

    fun search() {
        val query = stateFlow.value.query.trim()
        if (query.isBlank()) return
        viewModelScope.launch {
            stateFlow.value = stateFlow.value.copy(loading = true, message = null)
            val current = stateFlow.value
            val results = coroutineScope {
                val jobs = mutableListOf<kotlinx.coroutines.Deferred<List<MediaItem>>>()
                jobs += async { tmdb.search(query, current.settings) }
                current.settings.databases.filter { it.enabled && it.endpoint.isNotBlank() }.forEach { db ->
                    jobs += async { searchDatabase(db, query) }
                }
                jobs.awaitAll().flatten()
            }
            val sourceItems = current.sources.flatMap { it.items }.filter { it.title.contains(query, true) }
            stateFlow.value = current.copy(
                results = (results + sourceItems).distinctBy { it.id },
                loading = false,
                message = if (results.isEmpty() && sourceItems.isEmpty()) "No results found" else null
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
                ?: JSONArray()
            else -> JSONArray()
        }
        return buildList {
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                add(MediaItem(
                    id = provider + ":" + o.optString("id", i.toString()),
                    title = o.optString("title").ifBlank { o.optString("name", "Untitled") },
                    type = o.optString("type", o.optString("media_type", "Video")),
                    year = o.optString("year").ifBlank { o.optString("release_date").take(4).ifBlank { null } },
                    poster = o.optString("poster").ifBlank { o.optString("poster_url").ifBlank { null } },
                    streamUrl = o.optString("streamUrl").ifBlank { o.optString("url").ifBlank { null } },
                    description = o.optString("description").ifBlank { o.optString("overview").ifBlank { null } },
                    provider = provider
                ))
            }
        }
    }

    fun saveSettings(apiKey: String, token: String, sourceText: String, databases: List<DatabaseConfig> = stateFlow.value.settings.databases) {
        val settings = AppSettings(
            apiKey.trim(), token.trim(),
            sourceText.lines().map(String::trim).filter(String::isNotBlank).distinct(),
            databases
        )
        store.save(settings)
        stateFlow.value = stateFlow.value.copy(settings = settings, message = "Settings saved")
        refreshSources()
    }

    fun saveDatabases(databases: List<DatabaseConfig>) {
        val s = stateFlow.value.settings
        saveSettings(s.tmdbApiKey, s.tmdbAccessToken, s.sourceUrls.joinToString("\n"), databases)
    }

    fun refreshSources() {
        viewModelScope.launch {
            val urls = stateFlow.value.settings.sourceUrls
            stateFlow.value = stateFlow.value.copy(loading = true)
            val loaded = sourceRepo.loadAll(urls)
            stateFlow.value = stateFlow.value.copy(sources = loaded, loading = false)
        }
    }

    fun play(item: MediaItem) {
        val url = item.streamUrl ?: return
        getApplication<Application>().startActivity(Intent(getApplication(), PlayerActivity::class.java).apply {
            putExtra("url", url)
            putExtra("title", item.title)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}
