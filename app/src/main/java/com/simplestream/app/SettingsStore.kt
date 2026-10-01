package com.simplestream.app

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("simplestream", Context.MODE_PRIVATE)
    private val alias = "simplestream_credentials"

    fun load(): AppSettings {
        val sources = JSONArray(prefs.getString("sources", "[]") ?: "[]").let { arr ->
            buildList { for (i in 0 until arr.length()) add(arr.getString(i)) }
        }
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
        val configured = if (databases.isEmpty()) listOf(
            DatabaseConfig("tvmaze", "TVmaze", "https://api.tvmaze.com/search/shows", "q"),
            DatabaseConfig("jikan", "Jikan Anime", "https://api.jikan.moe/v4/anime?q={query}", "q"),
            DatabaseConfig("archive", "Internet Archive", "https://archive.org/advancedsearch.php?q={query}&fl[]=identifier&fl[]=title&fl[]=description&rows=20&page=1&output=json", "q")
        ) else databases
        return AppSettings(
            decrypt(prefs.getString("tmdb_key", "") ?: ""),
            decrypt(prefs.getString("tmdb_token", "") ?: ""),
            sources,
            databases
        )
    }

    fun save(settings: AppSettings) {
        val sources = JSONArray().apply { settings.sourceUrls.distinct().filter(String::isNotBlank).forEach(::put) }
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
            .putString("sources", sources.toString())
            .putString("databases", databases.toString())
            .apply()
    }

    private fun key(): SecretKey {
        val ks = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = ks.getKey(alias, null)
        if (existing is SecretKey) return existing
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(
            alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(false)
            .build())
        return generator.generateKey()
    }

    private fun encrypt(value: String): String {
        if (value.isBlank()) return ""
        return runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val combined = cipher.iv + cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
            Base64.encodeToString(combined, Base64.NO_WRAP)
        }.getOrDefault("")
    }

    private fun decrypt(value: String): String {
        if (value.isBlank()) return ""
        return runCatching {
            val raw = Base64.decode(value, Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw.copyOfRange(0, 12)))
            String(cipher.doFinal(raw.copyOfRange(12, raw.size)), StandardCharsets.UTF_8)
        }.getOrDefault("")
    }
}
