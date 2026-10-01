package com.simplestream.app

import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import java.util.UUID

class PlayerActivity : ComponentActivity() {
    private var player: ExoPlayer? = null
    private lateinit var playbackRepo: PlaybackRepository

    private var movieId: String = ""
    private var movieTitle: String = ""
    private var moviePoster: String? = null
    private var streamUrl: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemUi()

        playbackRepo = PlaybackRepository(this)

        movieId = intent.getStringExtra("movieId") ?: intent.getStringExtra("id").orEmpty()
        movieTitle = intent.getStringExtra("title").orEmpty()
        moviePoster = intent.getStringExtra("poster")
        streamUrl = intent.getStringExtra("url") ?: return finish()
        val mimeType = intent.getStringExtra("mimeType")
        val startPositionMs = intent.getLongExtra("positionMs", 0L)
        val subtitleUrl = intent.getStringExtra("subtitleUrl")
        val subtitleLanguage = intent.getStringExtra("subtitleLanguage") ?: "en"

        // Setup ExoPlayer with HttpDataSource supporting custom headers
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)

        val mediaSourceFactory = DefaultMediaSourceFactory(this)
            .setDataSourceFactory(httpDataSourceFactory)

        val mediaBuilder = ExoMediaItem.Builder()
            .setUri(Uri.parse(streamUrl))
            .setMediaId(movieId.ifBlank { movieTitle })

        if (!mimeType.isNullOrBlank()) {
            mediaBuilder.setMimeType(mimeType)
        }

        // Add subtitle configuration if available
        if (!subtitleUrl.isNullOrBlank()) {
            val subtitleConfig = ExoMediaItem.SubtitleConfiguration.Builder(Uri.parse(subtitleUrl))
                .setMimeType("text/vtt")
                .setLanguage(subtitleLanguage)
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()
            mediaBuilder.setSubtitleConfigurations(listOf(subtitleConfig))
        }

        // Setup DRM if provided
        val drmScheme = intent.getStringExtra("drmScheme")
        val drmLicenseUrl = intent.getStringExtra("drmLicenseUrl")
        if (!drmScheme.isNullOrBlank() && !drmLicenseUrl.isNullOrBlank()) {
            val uuid = when (drmScheme.lowercase()) {
                "widevine", "com.widevine.alpha" -> C.WIDEVINE_UUID
                "playready", "com.microsoft.playready" -> C.PLAYREADY_UUID
                "clearkey", "org.w3.clearkey" -> C.CLEARKEY_UUID
                else -> runCatching { UUID.fromString(drmScheme) }.getOrNull()
            }
            if (uuid != null) {
                mediaBuilder.setDrmConfiguration(
                    ExoMediaItem.DrmConfiguration.Builder(uuid)
                        .setLicenseUri(drmLicenseUrl)
                        .build()
                )
            }
        }

        val exo = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .apply {
                setMediaItem(mediaBuilder.build())
                if (startPositionMs > 0) {
                    seekTo(startPositionMs)
                }
                prepare()
                playWhenReady = true
            }

        this.player = exo

        setContent {
            PlayerScreen(
                player = exo,
                title = movieTitle,
                onBack = { finish() },
                onToggleOrientation = { toggleOrientation() },
                onEnterPiP = { enterPiP() },
                onRetry = {
                    exo.seekTo(exo.currentPosition)
                    exo.prepare()
                    exo.playWhenReady = true
                }
            )
        }
    }

    private fun toggleOrientation() {
        requestedOrientation = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    private fun enterPiP() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            enterPictureInPictureMode(params)
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (player?.isPlaying == true) {
            enterPiP()
        }
    }

    private fun saveCurrentProgress() {
        val p = player ?: return
        if (movieId.isNotBlank() && p.duration > 0) {
            val currentPos = p.currentPosition
            val totalDur = p.duration
            val isCompleted = (totalDur - currentPos) < 15000 // within last 15s
            playbackRepo.saveProgress(
                WatchProgress(
                    movieId = movieId,
                    title = movieTitle,
                    poster = moviePoster,
                    streamUrl = streamUrl,
                    positionMs = currentPos,
                    durationMs = totalDur,
                    lastWatched = System.currentTimeMillis(),
                    completed = isCompleted
                )
            )
        }
    }

    private fun hideSystemUi() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
        )
    }

    override fun onPause() {
        super.onPause()
        saveCurrentProgress()
    }

    override fun onStop() {
        saveCurrentProgress()
        super.onStop()
    }

    override fun onDestroy() {
        saveCurrentProgress()
        player?.release()
        player = null
        super.onDestroy()
    }
}

@Composable
fun PlayerScreen(
    player: ExoPlayer,
    title: String,
    onBack: () -> Unit,
    onToggleOrientation: () -> Unit,
    onEnterPiP: () -> Unit,
    onRetry: () -> Unit
) {
    var isPlaying by remember { mutableStateOf(player.isPlaying) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var isBuffering by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var controlsVisible by remember { mutableStateOf(true) }
    var playbackSpeed by remember { mutableFloatStateOf(1f) }

    // Auto-hide controls timer
    LaunchedEffect(controlsVisible, isPlaying) {
        if (controlsVisible && isPlaying) {
            delay(4000)
            controlsVisible = false
        }
    }

    // Progress updates
    LaunchedEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = state == Player.STATE_BUFFERING
                duration = player.duration.coerceAtLeast(0L)
                if (state == Player.STATE_READY) {
                    errorMessage = null
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                errorMessage = "Playback error: ${error.localizedMessage ?: "Source unavailable"}"
            }
        }
        player.addListener(listener)
        while (true) {
            currentPosition = player.currentPosition.coerceAtLeast(0L)
            duration = player.duration.coerceAtLeast(0L)
            delay(500)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                controlsVisible = !controlsVisible
            }
    ) {
        // Video View
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    this.player = player
                    useController = false // Use custom Compose controls
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Buffering Indicator
        if (isBuffering && errorMessage == null) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(56.dp)
                    .align(Alignment.Center)
            )
        }

        // Error Message Overlay
        if (errorMessage != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.85f), MaterialTheme.shapes.medium)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = errorMessage ?: "Playback failed",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = onRetry) {
                    Icon(Icons.Default.Refresh, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Retry")
                }
            }
        }

        // Controls Overlay
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
            ) {
                // Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 16.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                        maxLines = 1
                    )
                    IconButton(onClick = onEnterPiP) {
                        Icon(Icons.Default.PictureInPictureAlt, contentDescription = "PiP", tint = Color.White)
                    }
                    IconButton(onClick = onToggleOrientation) {
                        Icon(Icons.Default.ScreenRotation, contentDescription = "Rotate", tint = Color.White)
                    }
                }

                // Center Controls (10s back, Play/Pause, 10s forward)
                Row(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { player.seekTo((player.currentPosition - 10000).coerceAtLeast(0L)) },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.Replay10, contentDescription = "Rewind 10s", tint = Color.White, modifier = Modifier.size(36.dp))
                    }

                    Spacer(Modifier.width(32.dp))

                    FilledIconButton(
                        onClick = {
                            if (player.isPlaying) player.pause() else player.play()
                        },
                        modifier = Modifier.size(64.dp),
                        shape = CircleShape
                    ) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Spacer(Modifier.width(32.dp))

                    IconButton(
                        onClick = { player.seekTo((player.currentPosition + 10000).coerceAtMost(player.duration)) },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.Forward10, contentDescription = "Forward 10s", tint = Color.White, modifier = Modifier.size(36.dp))
                    }
                }

                // Bottom Bar (Seekbar, Current Time, Duration, Speed)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 16.dp, vertical = 16.dp)
                ) {
                    // Slider / Seekbar
                    if (duration > 0) {
                        Slider(
                            value = currentPosition.toFloat(),
                            onValueChange = { player.seekTo(it.toLong()) },
                            valueRange = 0f..duration.toFloat(),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                            )
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${formatTime(currentPosition)} / ${formatTime(duration)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Playback Speed button
                            TextButton(
                                onClick = {
                                    val newSpeed = when (playbackSpeed) {
                                        1f -> 1.25f
                                        1.25f -> 1.5f
                                        1.5f -> 2.0f
                                        2.0f -> 0.75f
                                        else -> 1f
                                    }
                                    playbackSpeed = newSpeed
                                    player.setPlaybackSpeed(newSpeed)
                                }
                            ) {
                                Text("${playbackSpeed}x", color = Color.White, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
