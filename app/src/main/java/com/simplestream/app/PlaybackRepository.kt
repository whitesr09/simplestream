package com.simplestream.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject

class PlaybackRepository(context: Context) {
    private val dbHelper = DatabaseHelper(context)

    fun saveProgress(progress: WatchProgress) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("movie_id", progress.movieId)
            put("title", progress.title)
            put("poster", progress.poster)
            put("stream_url", progress.streamUrl)
            put("position_ms", progress.positionMs)
            put("duration_ms", progress.durationMs)
            put("last_watched", progress.lastWatched)
            put("completed", if (progress.completed) 1 else 0)
        }
        db.insertWithOnConflict("watch_progress", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getProgress(movieId: String): WatchProgress? {
        val db = dbHelper.readableDatabase
        db.query(
            "watch_progress",
            null,
            "movie_id = ?",
            arrayOf(movieId),
            null,
            null,
            null
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                val pos = cursor.getLong(cursor.getColumnIndexOrThrow("position_ms"))
                val dur = cursor.getLong(cursor.getColumnIndexOrThrow("duration_ms"))
                val comp = cursor.getInt(cursor.getColumnIndexOrThrow("completed")) == 1
                return WatchProgress(
                    movieId = cursor.getString(cursor.getColumnIndexOrThrow("movie_id")),
                    title = cursor.getString(cursor.getColumnIndexOrThrow("title")),
                    poster = cursor.getString(cursor.getColumnIndexOrThrow("poster")),
                    streamUrl = cursor.getString(cursor.getColumnIndexOrThrow("stream_url")),
                    positionMs = pos,
                    durationMs = dur,
                    lastWatched = cursor.getLong(cursor.getColumnIndexOrThrow("last_watched")),
                    completed = comp
                )
            }
        }
        return null
    }

    fun getAllContinueWatching(): List<WatchProgress> {
        val db = dbHelper.readableDatabase
        val list = mutableListOf<WatchProgress>()
        db.query(
            "watch_progress",
            null,
            "completed = 0 AND position_ms > 5000",
            null,
            null,
            null,
            "last_watched DESC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(
                    WatchProgress(
                        movieId = cursor.getString(cursor.getColumnIndexOrThrow("movie_id")),
                        title = cursor.getString(cursor.getColumnIndexOrThrow("title")),
                        poster = cursor.getString(cursor.getColumnIndexOrThrow("poster")),
                        streamUrl = cursor.getString(cursor.getColumnIndexOrThrow("stream_url")),
                        positionMs = cursor.getLong(cursor.getColumnIndexOrThrow("position_ms")),
                        durationMs = cursor.getLong(cursor.getColumnIndexOrThrow("duration_ms")),
                        lastWatched = cursor.getLong(cursor.getColumnIndexOrThrow("last_watched")),
                        completed = false
                    )
                )
            }
        }
        return list
    }

    fun deleteProgress(movieId: String) {
        val db = dbHelper.writableDatabase
        db.delete("watch_progress", "movie_id = ?", arrayOf(movieId))
    }

    fun isWatchlist(movieId: String): Boolean {
        val db = dbHelper.readableDatabase
        db.query("watchlist", arrayOf("movie_id"), "movie_id = ?", arrayOf(movieId), null, null, null).use {
            return it.count > 0
        }
    }

    fun toggleWatchlist(item: MediaItem): Boolean {
        val db = dbHelper.writableDatabase
        if (isWatchlist(item.id)) {
            db.delete("watchlist", "movie_id = ?", arrayOf(item.id))
            return false
        } else {
            val values = ContentValues().apply {
                put("movie_id", item.id)
                put("title", item.title)
                put("json_data", serializeItem(item))
                put("added_at", System.currentTimeMillis())
            }
            db.insertWithOnConflict("watchlist", null, values, SQLiteDatabase.CONFLICT_REPLACE)
            return true
        }
    }

    fun getWatchlist(): List<MediaItem> {
        val db = dbHelper.readableDatabase
        val list = mutableListOf<MediaItem>()
        db.query("watchlist", null, null, null, null, null, "added_at DESC").use { cursor ->
            while (cursor.moveToNext()) {
                val json = cursor.getString(cursor.getColumnIndexOrThrow("json_data"))
                deserializeItem(json)?.let { list.add(it) }
            }
        }
        return list
    }

    fun isFavorite(movieId: String): Boolean {
        val db = dbHelper.readableDatabase
        db.query("favorites", arrayOf("movie_id"), "movie_id = ?", arrayOf(movieId), null, null, null).use {
            return it.count > 0
        }
    }

    fun toggleFavorite(item: MediaItem): Boolean {
        val db = dbHelper.writableDatabase
        if (isFavorite(item.id)) {
            db.delete("favorites", "movie_id = ?", arrayOf(item.id))
            return false
        } else {
            val values = ContentValues().apply {
                put("movie_id", item.id)
                put("title", item.title)
                put("json_data", serializeItem(item))
                put("added_at", System.currentTimeMillis())
            }
            db.insertWithOnConflict("favorites", null, values, SQLiteDatabase.CONFLICT_REPLACE)
            return true
        }
    }

    fun getFavorites(): List<MediaItem> {
        val db = dbHelper.readableDatabase
        val list = mutableListOf<MediaItem>()
        db.query("favorites", null, null, null, null, null, "added_at DESC").use { cursor ->
            while (cursor.moveToNext()) {
                val json = cursor.getString(cursor.getColumnIndexOrThrow("json_data"))
                deserializeItem(json)?.let { list.add(it) }
            }
        }
        return list
    }

    private fun serializeItem(item: MediaItem): String {
        return JSONObject().apply {
            put("id", item.id)
            put("title", item.title)
            put("type", item.type)
            put("year", item.year)
            put("poster", item.poster)
            put("backdrop", item.backdrop)
            put("streamUrl", item.streamUrl)
            put("description", item.description)
            put("genre", item.genre)
            put("runtime", item.runtime)
            put("provider", item.provider)
            put("mimeType", item.mimeType)
            val streamsArr = JSONArray()
            item.streams.forEach { s ->
                streamsArr.put(JSONObject().apply {
                    put("quality", s.quality)
                    put("url", s.url)
                    put("mimeType", s.mimeType)
                })
            }
            put("streams", streamsArr)
        }.toString()
    }

    private fun deserializeItem(json: String?): MediaItem? {
        if (json.isNullOrBlank()) return null
        return runCatching {
            val obj = JSONObject(json)
            val streamsList = mutableListOf<StreamVariant>()
            obj.optJSONArray("streams")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val s = arr.optJSONObject(i) ?: continue
                    streamsList.add(StreamVariant(
                        quality = s.optString("quality", "Auto"),
                        url = s.optString("url"),
                        mimeType = s.optString("mimeType").ifBlank { null }
                    ))
                }
            }
            MediaItem(
                id = obj.getString("id"),
                title = obj.getString("title"),
                type = obj.optString("type", "Movie"),
                year = obj.optString("year").ifBlank { null },
                poster = obj.optString("poster").ifBlank { null },
                backdrop = obj.optString("backdrop").ifBlank { null },
                streamUrl = obj.optString("streamUrl").ifBlank { null },
                streams = streamsList,
                description = obj.optString("description").ifBlank { null },
                genre = obj.optString("genre").ifBlank { null },
                runtime = obj.optString("runtime").ifBlank { null },
                provider = obj.optString("provider").ifBlank { null },
                mimeType = obj.optString("mimeType").ifBlank { null }
            )
        }.getOrNull()
    }

    private class DatabaseHelper(context: Context) :
        SQLiteOpenHelper(context, "simplestream_db.db", null, 2) {

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE watch_progress (
                    movie_id TEXT PRIMARY KEY,
                    title TEXT NOT NULL,
                    poster TEXT,
                    stream_url TEXT NOT NULL,
                    position_ms INTEGER NOT NULL,
                    duration_ms INTEGER NOT NULL,
                    last_watched INTEGER NOT NULL,
                    completed INTEGER NOT NULL DEFAULT 0
                )
            """.trimIndent())

            db.execSQL("""
                CREATE TABLE watchlist (
                    movie_id TEXT PRIMARY KEY,
                    title TEXT NOT NULL,
                    json_data TEXT NOT NULL,
                    added_at INTEGER NOT NULL
                )
            """.trimIndent())

            db.execSQL("""
                CREATE TABLE favorites (
                    movie_id TEXT PRIMARY KEY,
                    title TEXT NOT NULL,
                    json_data TEXT NOT NULL,
                    added_at INTEGER NOT NULL
                )
            """.trimIndent())
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            db.execSQL("DROP TABLE IF EXISTS watch_progress")
            db.execSQL("DROP TABLE IF EXISTS watchlist")
            db.execSQL("DROP TABLE IF EXISTS favorites")
            onCreate(db)
        }
    }
}
