package com.simplestream.app

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

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
    private val stateFlow = MutableStateFlow(UiState(settings = store.load()))
    val state: StateFlow<UiState> = stateFlow

    init { refreshSources() }
    fun setQuery(value: String) { stateFlow.value = stateFlow.value.copy(query = value) }

    fun search() {
        viewModelScope.launch {
            stateFlow.value = stateFlow.value.copy(loading = true, message = null)
            val current = stateFlow.value
            val tmdbItems = tmdb.search(current.query, current.settings)
            val sourceItems = current.sources.flatMap { it.items }.filter { it.title.contains(current.query, true) }
            stateFlow.value = current.copy(results = (tmdbItems + sourceItems).distinctBy { it.id }, loading = false)
        }
    }

    fun saveSettings(apiKey: String, token: String, sourceText: String) {
        val settings = AppSettings(apiKey.trim(), token.trim(), sourceText.lines().map(String::trim).filter(String::isNotBlank))
        store.save(settings)
        stateFlow.value = stateFlow.value.copy(settings = settings, message = "Settings saved")
        refreshSources()
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
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}