package com.simplestream.app

data class MediaItem(
    val id: String,
    val title: String,
    val type: String = "Video",
    val year: String? = null,
    val poster: String? = null,
    val streamUrl: String? = null,
    val description: String? = null,
    val provider: String? = null,
    val mimeType: String? = null,
    val drmScheme: String? = null,
    val drmLicenseUrl: String? = null,
    val drmHeaders: Map<String, String> = emptyMap()
)

data class SourceManifest(
    val name: String,
    val url: String,
    val items: List<MediaItem> = emptyList(),
    val error: String? = null
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

data class AppSettings(
    val tmdbApiKey: String = "",
    val tmdbAccessToken: String = "",
    val sourceUrls: List<String> = emptyList(),
    val databases: List<DatabaseConfig> = emptyList(),
    val streams: List<StreamEntry> = emptyList()
)
