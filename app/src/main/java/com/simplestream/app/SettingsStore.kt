package com.simplestream.app

import android.content.Context
import org.json.JSONArray

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("simplestream", Context.MODE_PRIVATE)

    fun load(): AppSettings {
        val arr = JSONArray(prefs.getString("sources", "[]") ?: "[]")
        val sources = buildList {
            for (i in 0 until arr.length()) add(arr.getString(i))
        }
        return AppSettings(
            prefs.getString("tmdb_key", "") ?: "",
            prefs.getString("tmdb_token", "") ?: "",
            sources
        )
    }

    fun save(settings: AppSettings) {
        val arr = JSONArray()
        settings.sourceUrls.distinct().filter(String::isNotBlank).forEach(arr::put)
        prefs.edit()
            .putString("tmdb_key", settings.tmdbApiKey)
            .putString("tmdb_token", settings.tmdbAccessToken)
            .putString("sources", arr.toString())
            .apply()
    }
}
