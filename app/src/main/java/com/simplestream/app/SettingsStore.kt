package com.simplestream.app

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("simplestream", Context.MODE_PRIVATE)

    fun load(): AppSettings {
        val legacyUrls = JSONArray(prefs.getString("sources", "[]") ?: "[]").let { arr ->
            buildList { for (i in 0 until arr.length()) add(arr.getString(i)) }
        }

        val sourcesJson = prefs.getString("source_configs", "[]") ?: "[]"
        val sources = parseSourceConfigs(sourcesJson, legacyUrls)

        val databases = JSONArray(prefs.getString("databases", "[]") ?: "[]").let { arr ->
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    add(DatabaseConfig(
                        id = o.optString("id"),
                        name = o.optString("name", "Database"),
                        endpoint = o.optString("endpoint"),
                        searchParameter = o.optString("searchParameter", "query"),
                        credentialMode = runCatching { CredentialMode.valueOf(o.optString("credentialMode", "NONE")) }.getOrDefault(CredentialMode.NONE),
                        credential = decrypt(o.optString("credential")),
                        enabled = o.optBoolean("enabled", true)
                    ))
                }
            }
        }

        return AppSettings(
            tmdbApiKey = decrypt(prefs.getString("tmdb_key", "") ?: ""),
            tmdbAccessToken = decrypt(prefs.getString("tmdb_token", "") ?: ""),
            sourceUrls = sources.map { it.url },
            sources = sources,
            databases = databases,
            streams = loadStreams()
        )
    }

    private fun parseSourceConfigs(json: String, legacyUrls: List<String>): List<SourceConfig> {
        val list = mutableListOf<SourceConfig>()
        runCatching {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                list.add(SourceConfig(
                    id = o.optString("id", "source_$i"),
                    name = o.optString("name", "Personal Library"),
                    url = o.optString("url"),
                    authHeaderName = o.optString("authHeaderName"),
                    authHeaderValue = decrypt(o.optString("authHeaderValue")),
                    enabled = o.optBoolean("enabled", true)
                ))
            }
        }
        if (list.isEmpty() && legacyUrls.isNotEmpty()) {
            legacyUrls.forEachIndexed { i, url ->
                list.add(SourceConfig(
                    id = "src_$i",
                    name = url.substringAfterLast('/').substringBefore('?').ifBlank { "Personal Library" },
                    url = url
                ))
            }
        }
        return list
    }

    private fun loadStreams(): List<StreamEntry> {
        val arr = JSONArray(prefs.getString("streams", "[]") ?: "[]")
        if (arr.length() == 0) {
            val demoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
            return listOf(StreamEntry("demo_bbb", "Big Buck Bunny", demoUrl, "video/mp4", provider = "Blender Foundation demo"))
        }
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                add(StreamEntry(
                    id = o.optString("id", "stream_$i"),
                    title = o.optString("title", "Stream"),
                    url = o.optString("url"),
                    mimeType = o.optString("mimeType").ifBlank { null },
                    provider = o.optString("provider", "Manual")
                ))
            }
        }
    }

    fun save(settings: AppSettings) {
        val sourcesArr = JSONArray().apply {
            settings.sources.forEach { s ->
                put(JSONObject().apply {
                    put("id", s.id)
                    put("name", s.name)
                    put("url", s.url)
                    put("authHeaderName", s.authHeaderName)
                    put("authHeaderValue", encrypt(s.authHeaderValue))
                    put("enabled", s.enabled)
                })
            }
        }

        val legacySources = JSONArray().apply {
            settings.sources.map { it.url }.distinct().filter(String::isNotBlank).forEach(::put)
        }

        val databases = JSONArray().apply {
            settings.databases.distinctBy { it.id.ifBlank { it.name } }.forEach {
                put(JSONObject().apply {
                    put("id", it.id)
                    put("name", it.name)
                    put("endpoint", it.endpoint)
                    put("searchParameter", it.searchParameter)
                    put("credentialMode", it.credentialMode.name)
                    put("credential", encrypt(it.credential))
                    put("enabled", it.enabled)
                })
            }
        }

        prefs.edit()
            .putString("tmdb_key", encrypt(settings.tmdbApiKey))
            .putString("tmdb_token", encrypt(settings.tmdbAccessToken))
            .putString("sources", legacySources.toString())
            .putString("source_configs", sourcesArr.toString())
            .putString("databases", databases.toString())
            .putString("streams", streamsJson(settings.streams))
            .apply()
    }

    private fun streamsJson(streams: List<StreamEntry>): String {
        return JSONArray().apply {
            streams.forEach {
                put(JSONObject().apply {
                    put("id", it.id)
                    put("title", it.title)
                    put("url", it.url)
                    put("mimeType", it.mimeType ?: "")
                    put("provider", it.provider)
                })
            }
        }.toString()
    }

    // Standard AES encryption with robust fallback
    private val salt = "SimpleStreamKey_16".toByteArray(StandardCharsets.UTF_8).copyOf(16)
    private val iv = "SimpleStreamIV_16".toByteArray(StandardCharsets.UTF_8).copyOf(16)

    private fun encrypt(value: String): String {
        if (value.isBlank()) return ""
        return runCatching {
            val keySpec = SecretKeySpec(salt, "AES")
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, IvParameterSpec(iv))
            val encrypted = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
            Base64.encodeToString(encrypted, Base64.NO_WRAP)
        }.getOrDefault(value)
    }

    private fun decrypt(value: String): String {
        if (value.isBlank()) return ""
        return runCatching {
            val keySpec = SecretKeySpec(salt, "AES")
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, keySpec, IvParameterSpec(iv))
            val decrypted = cipher.doFinal(Base64.decode(value, Base64.NO_WRAP))
            String(decrypted, StandardCharsets.UTF_8)
        }.getOrDefault(value)
    }
}
