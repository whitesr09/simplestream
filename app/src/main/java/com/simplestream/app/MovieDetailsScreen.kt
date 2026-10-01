package com.simplestream.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovieDetailsScreen(
    item: MediaItem,
    progress: WatchProgress?,
    isWatchlist: Boolean,
    isFavorite: Boolean,
    onBack: () -> Unit,
    onPlay: (MediaItem, StreamVariant?, Long) -> Unit,
    onToggleWatchlist: () -> Unit,
    onToggleFavorite: () -> Unit,
    onClearProgress: () -> Unit,
    onAddStreamToItem: ((MediaItem, String) -> Unit)? = null
) {
    var selectedStream by remember(item) { mutableStateOf(item.allStreams.firstOrNull()) }
    var selectedSubtitle by remember(item) { mutableStateOf(item.subtitles.firstOrNull()) }
    var showAddStreamDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(item.title, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = onToggleWatchlist) {
                        Icon(
                            if (isWatchlist) Icons.Default.BookmarkAdded else Icons.Default.BookmarkBorder,
                            contentDescription = "Watchlist",
                            tint = if (isWatchlist) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // Backdrop & Poster Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
            ) {
                val backdropUrl = item.backdrop ?: item.poster
                if (!backdropUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = backdropUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                }

                // Gradient overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, MaterialTheme.colorScheme.background.copy(alpha = 0.95f)),
                                startY = 100f
                            )
                        )
                )

                // Poster & Title Info Overlay
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomStart)
                        .padding(16.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    if (!item.poster.isNullOrBlank()) {
                        AsyncImage(
                            model = item.poster,
                            contentDescription = null,
                            modifier = Modifier
                                .width(90.dp)
                                .height(130.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.width(16.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        val metaList = listOfNotNull(item.type, item.year, item.runtime)
                        if (metaList.isNotEmpty()) {
                            Text(
                                text = metaList.joinToString(" • "),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (!item.genre.isNullOrBlank()) {
                            Text(
                                text = item.genre,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Watch Progress Bar if exists
            if (progress != null && progress.positionMs > 5000 && !progress.completed) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Watched ${progress.formattedResume}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = progress.formattedRemaining,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = progress.progressFraction,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                    )
                }
            }

            // Action Buttons
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (item.isPlayable) {
                    // Resume button if watched
                    if (progress != null && progress.positionMs > 10000 && !progress.completed) {
                        Button(
                            onClick = { onPlay(item, selectedStream, progress.positionMs) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.PlayArrow, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Resume from ${progress.formattedResume}")
                        }

                        OutlinedButton(
                            onClick = { onPlay(item, selectedStream, 0L) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Replay, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Play from Beginning")
                        }
                    } else {
                        Button(
                            onClick = { onPlay(item, selectedStream, 0L) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.PlayArrow, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Play Movie")
                        }
                    }
                } else {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "No stream URL available yet",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                "This entry contains metadata. Add a personal stream URL from your home server or NAS to make it playable.",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(Modifier.height(8.dp))
                            FilledTonalButton(onClick = { showAddStreamDialog = true }) {
                                Icon(Icons.Default.AddLink, null)
                                Spacer(Modifier.width(6.dp))
                                Text("Attach Stream URL")
                            }
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onToggleWatchlist,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            if (isWatchlist) Icons.Default.BookmarkAdded else Icons.Default.BookmarkBorder,
                            null
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (isWatchlist) "In Watchlist" else "+ Watchlist")
                    }

                    if (progress != null) {
                        OutlinedButton(
                            onClick = onClearProgress,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.DeleteOutline, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Clear History")
                        }
                    }
                }
            }

            // Quality Selection (if multiple streams available)
            if (item.allStreams.size > 1) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("Stream Quality", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item.allStreams.forEach { variant ->
                            FilterChip(
                                selected = selectedStream?.url == variant.url,
                                onClick = { selectedStream = variant },
                                label = { Text(variant.quality) }
                            )
                        }
                    }
                }
            }

            // Subtitle Selection (if subtitles available)
            if (item.subtitles.isNotEmpty()) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("Subtitles", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item.subtitles.forEach { sub ->
                            FilterChip(
                                selected = selectedSubtitle?.url == sub.url,
                                onClick = { selectedSubtitle = if (selectedSubtitle?.url == sub.url) null else sub },
                                label = { Text(sub.label) }
                            )
                        }
                    }
                }
            }

            // Synopsis / Description
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Synopsis", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = item.description ?: "No description available for this title.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (!item.provider.isNullOrBlank()) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "Source: ${item.provider}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }

    if (showAddStreamDialog) {
        var inputUrl by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddStreamDialog = false },
            title = { Text("Attach personal stream") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter direct MP4, HLS (.m3u8), or DASH (.mpd) URL:")
                    OutlinedTextField(
                        value = inputUrl,
                        onValueChange = { inputUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Video URL") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (inputUrl.startsWith("http", ignoreCase = true)) {
                            onAddStreamToItem?.invoke(item, inputUrl.trim())
                            showAddStreamDialog = false
                        }
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showAddStreamDialog = false }) { Text("Cancel") }
            }
        )
    }
}
