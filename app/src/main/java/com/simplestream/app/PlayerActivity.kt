package com.simplestream.app

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class PlayerActivity : ComponentActivity() {
    private var player: ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra("url") ?: return finish()
        player = ExoPlayer.Builder(this).build().also {
            it.setMediaItem(ExoMediaItem.fromUri(Uri.parse(url)))
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
