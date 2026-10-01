package com.simplestream.app

data class MediaItem(
    val id: String,
    val title: String,
    val type: String = "Video",
    val year: String? = null,
    val poster: String? = null,
    val streamUrl: String? = null,
    val description: String? = null
)

data class SourceManifest(
    val name: String,
    val url: String,
    val items: List<MediaItem> = emptyList()
)

data class AppSettings(
    val tmdbApiKey: String = "",
    val tmdbAccessToken: String = "",
    val sourceUrls: List<String> = emptyList()
)
