package com.simplestream.app

data class StreamVariant(
    val quality: String = "Auto",
    val url: String,
    val mimeType: String? = null,
    val headers: Map<String, String> = emptyMap()
)

data class SubtitleTrack(
    val label: String,
    val url: String,
    val language: String = "en",
    val mimeType: String = "text/vtt"
)

data class MediaItem(
    val id: String,
    val title: String,
    val type: String = "Movie",
    val year: String? = null,
    val poster: String? = null,
    val backdrop: String? = null,
    val streamUrl: String? = null,
    val streams: List<StreamVariant> = emptyList(),
    val subtitles: List<SubtitleTrack> = emptyList(),
    val description: String? = null,
    val genre: String? = null,
    val runtime: String? = null,
    val provider: String? = null,
    val mimeType: String? = null,
    val drmScheme: String? = null,
    val drmLicenseUrl: String? = null,
    val drmHeaders: Map<String, String> = emptyMap()
) {
    val primaryStreamUrl: String?
        get() = streams.firstOrNull()?.url ?: streamUrl

    val allStreams: List<StreamVariant>
        get() = if (streams.isNotEmpty()) streams
        else if (!streamUrl.isNullOrBlank()) listOf(StreamVariant("Default", streamUrl, mimeType))
        else emptyList()

    val isPlayable: Boolean
        get() = allStreams.isNotEmpty()
}

data class WatchProgress(
    val movieId: String,
    val title: String,
    val poster: String? = null,
    val streamUrl: String,
    val positionMs: Long,
    val durationMs: Long,
    val lastWatched: Long = System.currentTimeMillis(),
    val completed: Boolean = false
) {
    val progressFraction: Float
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

    val formattedRemaining: String
        get() {
            val remSec = ((durationMs - positionMs) / 1000).coerceAtLeast(0)
            val min = remSec / 60
            return if (min > 60) "${min / 60}h ${min % 60}m left" else "${min}m left"
        }

    val formattedResume: String
        get() {
            val sec = (positionMs / 1000).coerceAtLeast(0)
            val h = sec / 3600
            val m = (sec % 3600) / 60
            val s = sec % 60
            return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
        }
}

data class SourceManifest(
    val id: String = "",
    val name: String,
    val url: String,
    val items: List<MediaItem> = emptyList(),
    val error: String? = null,
    val enabled: Boolean = true,
    val lastRefreshed: Long = 0L
)

data class StreamEntry(
    val id: String,
    val title: String,
    val url: String,
    val mimeType: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val drmScheme: String? = null,
    val drmLicenseUrl: String? = null,
    val drmHeaders: Map<String, String> = emptyMap(),
    val provider: String = "Manual"
)

data class HomeSection(
    val title: String,
    val items: List<MediaItem>
)

enum class CredentialMode { NONE, API_KEY_QUERY, API_KEY_HEADER, BEARER }

data class DatabaseConfig(
    val id: String,
    val name: String,
    val endpoint: String,
    val searchParameter: String = "query",
    val credentialMode: CredentialMode = CredentialMode.NONE,
    val credential: String = "",
    val enabled: Boolean = true
)

data class SourceConfig(
    val id: String,
    val name: String,
    val url: String,
    val authHeaderName: String = "",
    val authHeaderValue: String = "",
    val enabled: Boolean = true
)

data class AppSettings(
    val tmdbApiKey: String = "",
    val tmdbAccessToken: String = "",
    val sourceUrls: List<String> = emptyList(),
    val sources: List<SourceConfig> = emptyList(),
    val databases: List<DatabaseConfig> = emptyList(),
    val streams: List<StreamEntry> = emptyList()
)
