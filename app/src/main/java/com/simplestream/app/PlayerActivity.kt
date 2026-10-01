package com.simplestream.app

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.util.UUID

class PlayerActivity : ComponentActivity() {
    private var player: ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val url = intent.getStringExtra("url") ?: return finish()
        val mimeType = intent.getStringExtra("mimeType")
        val drmScheme = intent.getStringExtra("drmScheme")
        val licenseUrl = intent.getStringExtra("drmLicenseUrl")
        val title = intent.getStringExtra("title").orEmpty()

        val mediaBuilder = ExoMediaItem.Builder()
            .setUri(Uri.parse(url))
            .setMediaId(title)

        if (!mimeType.isNullOrBlank()) mediaBuilder.setMimeType(mimeType)

        if (!drmScheme.isNullOrBlank() && !licenseUrl.isNullOrBlank()) {
            val uuid = when (drmScheme.lowercase()) {
                "widevine", "com.widevine.alpha" -> C.WIDEVINE_UUID
                "playready", "com.microsoft.playready" -> C.PLAYREADY_UUID
                "clearkey", "org.w3.clearkey" -> C.CLEARKEY_UUID
                else -> runCatching { UUID.fromString(drmScheme) }.getOrNull()
            }
            if (uuid != null) {
                mediaBuilder.setDrmConfiguration(
                    ExoMediaItem.DrmConfiguration.Builder(uuid)
                        .setLicenseUri(licenseUrl)
                        .build()
                )
            } else {
                Toast.makeText(this, "Unsupported DRM scheme: $drmScheme", Toast.LENGTH_LONG).show()
            }
        }

        player = ExoPlayer.Builder(this).build().also {
            it.setMediaItem(mediaBuilder.build())
            it.prepare()
            it.playWhenReady = true
        }

        setContent {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { PlayerView(it).apply { player = this@PlayerActivity.player } }
            )
        }
    }

    override fun onStop() {
        player?.release()
        player = null
        super.onStop()
    }
}
