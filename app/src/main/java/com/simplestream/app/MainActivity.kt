package com.simplestream.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SimpleStreamApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimpleStreamApp(vm: SimpleStreamViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    var showAddSource by remember { mutableStateOf(false) }
    var showAddStream by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val scheme = if (android.os.Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (dark) darkColorScheme() else lightColorScheme()

    // Handle back press when movie details is open
    if (state.selectedMovie != null) {
        BackHandler { vm.selectMovie(null) }
    }

    LaunchedEffect(Unit) {
        vm.refreshUserData()
    }

    MaterialTheme(colorScheme = scheme) {
        if (state.selectedMovie != null) {
            val movie = state.selectedMovie!!
            val progress = vm.getProgress(movie.id)
            val isWatchlist = vm.isWatchlist(movie.id)
            val isFavorite = vm.isFavorite(movie.id)

            MovieDetailsScreen(
                item = movie,
                progress = progress,
                isWatchlist = isWatchlist,
                isFavorite = isFavorite,
                onBack = { vm.selectMovie(null) },
                onPlay = { item, variant, pos -> vm.playMovie(item, variant, pos) },
                onToggleWatchlist = { vm.toggleWatchlist(movie) },
                onToggleFavorite = { vm.toggleFavorite(movie) },
                onClearProgress = { vm.clearProgress(movie.id) },
                onAddStreamToItem = { item, url -> vm.attachStreamToMovie(item, url) }
            )
        } else {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Movie, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Text("SimpleStream", fontWeight = FontWeight.Bold)
                            }
                        },
                        actions = {
                            if (state.loading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    strokeWidth = 2.5.dp
                                )
                                Spacer(Modifier.width(12.dp))
                            }
                            IconButton(onClick = { vm.refreshAll() }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                            }
                        }
                    )
                },
                bottomBar = {
                    NavigationBar {
                        NavigationBarItem(
                            selected = tab == 0,
                            onClick = { tab = 0 },
                            icon = { Icon(Icons.Default.Home, null) },
                            label = { Text("Home") }
                        )
                        NavigationBarItem(
                            selected = tab == 1,
                            onClick = { tab = 1 },
                            icon = { Icon(Icons.Default.LocalMovies, null) },
                            label = { Text("Movies") }
                        )
                        NavigationBarItem(
                            selected = tab == 2,
                            onClick = { tab = 2 },
                            icon = { Icon(Icons.Default.History, null) },
                            label = { Text("Continue") }
                        )
                        NavigationBarItem(
                            selected = tab == 3,
                            onClick = { tab = 3 },
                            icon = { Icon(Icons.Default.Bookmark, null) },
                            label = { Text("Saved") }
                        )
                        NavigationBarItem(
                            selected = tab == 4,
                            onClick = { tab = 4 },
                            icon = { Icon(Icons.Default.Settings, null) },
                            label = { Text("Settings") }
                        )
                    }
                }
            ) { padding ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    when (tab) {
                        0 -> HomeScreen(state, vm)
                        1 -> PersonalMoviesScreen(state, vm)
                        2 -> ContinueWatchingScreen(state, vm)
                        3 -> WatchlistScreen(state, vm)
                        4 -> SettingsScreen(
                            state = state,
                            vm = vm,
                            onAddSource = { showAddSource = true },
                            onAddStream = { showAddStream = true }
                        )
                    }
                }
            }
        }

        if (showAddSource) {
            AddSourceDialog(
                onDismiss = { showAddSource = false },
                onSave = { name, url, hName, hVal ->
                    vm.addPersonalSource(name, url, hName, hVal)
                    showAddSource = false
                }
            )
        }

        if (showAddStream) {
            AddStreamDialog(
                onDismiss = { showAddStream = false },
                onSave = { entry ->
                    vm.addStream(entry)
                    showAddStream = false
                }
            )
        }
    }
}

@Composable
private fun HomeScreen(state: UiState, vm: SimpleStreamViewModel) {
    var directUrl by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp)
    ) {
        // Search Bar
        item {
            OutlinedTextField(
                value = state.query,
                onValueChange = vm::setQuery,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search your movies and library") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (state.query.isNotBlank()) {
                        IconButton(onClick = vm::search) {
                            Icon(Icons.Default.ArrowForward, contentDescription = "Search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
        }

        // Quick Direct Play URL Input
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = directUrl,
                        onValueChange = { directUrl = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Direct MP4 / M3U8 / MPD URL") },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    FilledIconButton(
                        onClick = {
                            if (directUrl.startsWith("http", ignoreCase = true)) {
                                vm.playStreamEntry(
                                    StreamEntry(
                                        id = "direct_" + System.currentTimeMillis(),
                                        title = "Direct Stream",
                                        url = directUrl.trim()
                                    )
                                )
                            }
                        }
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Play Direct")
                    }
                }
            }
        }

        // Status or error message
        if (!state.message.isNullOrBlank()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        // Search Results Section
        if (state.results.isNotEmpty()) {
            item {
                Text(
                    text = "Search Results (${state.results.size})",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            items(state.results, key = { "search_" + it.id }) { item ->
                MovieCardRow(item = item, onClick = { vm.selectMovie(item) })
            }
        }

        // Continue Watching Section (Priority 4)
        if (state.results.isEmpty() && state.continueWatching.isNotEmpty()) {
            item {
                Text(
                    text = "Continue Watching",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(end = 16.dp)
                ) {
                    items(state.continueWatching, key = { "cw_" + it.movieId }) { progress ->
                        ContinueWatchingCard(progress = progress, onClick = {
                            val matched = state.personalMovies.firstOrNull { it.id == progress.movieId }
                            if (matched != null) {
                                vm.selectMovie(matched)
                            } else {
                                vm.playMovie(
                                    MediaItem(id = progress.movieId, title = progress.title, streamUrl = progress.streamUrl),
                                    positionMs = progress.positionMs
                                )
                            }
                        })
                    }
                }
            }
        }

        // Home Sections (Featured, Genres)
        if (state.results.isEmpty() && state.homeSections.isNotEmpty()) {
            state.homeSections.forEach { section ->
                item {
                    Text(
                        text = section.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(end = 16.dp)
                    ) {
                        items(section.items, key = { "section_${section.title}_${it.id}" }) { item ->
                            MoviePosterCard(item = item, onClick = { vm.selectMovie(item) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PersonalMoviesScreen(state: UiState, vm: SimpleStreamViewModel) {
    var filterText by remember { mutableStateOf("") }
    val filteredMovies = remember(state.personalMovies, filterText) {
        if (filterText.isBlank()) state.personalMovies
        else state.personalMovies.filter {
            it.title.contains(filterText, ignoreCase = true) ||
            (it.genre?.contains(filterText, ignoreCase = true) == true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Personal Movie Library",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "${filteredMovies.size} movies ready to stream",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = filterText,
            onValueChange = { filterText = it },
            placeholder = { Text("Filter library...") },
            leadingIcon = { Icon(Icons.Default.FilterList, null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp)
        )

        Spacer(Modifier.height(16.dp))

        if (filteredMovies.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No movies found", color = MaterialTheme.colorScheme.outline)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 140.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredMovies, key = { "grid_" + it.id }) { item ->
                    MoviePosterCard(item = item, onClick = { vm.selectMovie(item) })
                }
            }
        }
    }
}

@Composable
private fun ContinueWatchingScreen(state: UiState, vm: SimpleStreamViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Continue Watching",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Resume your movies where you left off",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
        Spacer(Modifier.height(16.dp))

        if (state.continueWatching.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.History, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.height(8.dp))
                    Text("No movies currently in progress", color = MaterialTheme.colorScheme.outline)
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(state.continueWatching, key = { "cw_list_" + it.movieId }) { cw ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val matched = state.personalMovies.firstOrNull { it.id == cw.movieId }
                                if (matched != null) vm.selectMovie(matched)
                                else vm.playMovie(
                                    MediaItem(id = cw.movieId, title = cw.title, streamUrl = cw.streamUrl),
                                    positionMs = cw.positionMs
                                )
                            },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!cw.poster.isNullOrBlank()) {
                                AsyncImage(
                                    model = cw.poster,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(60.dp, 85.dp)
                                        .clip(RoundedCornerShape(6.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(Modifier.width(12.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(cw.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Resume from ${cw.formattedResume} • ${cw.formattedRemaining}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = cw.progressFraction,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                )
                            }
                            IconButton(onClick = { vm.clearProgress(cw.movieId) }) {
                                Icon(Icons.Default.Close, contentDescription = "Remove")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WatchlistScreen(state: UiState, vm: SimpleStreamViewModel) {
    var selectedSection by remember { mutableIntStateOf(0) }
    val displayItems = if (selectedSection == 0) state.watchlist else state.favorites

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        TabRow(selectedTabIndex = selectedSection) {
            Tab(
                selected = selectedSection == 0,
                onClick = { selectedSection = 0 },
                text = { Text("Watchlist (${state.watchlist.size})") },
                icon = { Icon(Icons.Default.Bookmark, null) }
            )
            Tab(
                selected = selectedSection == 1,
                onClick = { selectedSection = 1 },
                text = { Text("Favorites (${state.favorites.size})") },
                icon = { Icon(Icons.Default.Favorite, null) }
            )
        }

        Spacer(Modifier.height(16.dp))

        if (displayItems.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (selectedSection == 0) "Your watchlist is empty" else "No favorite movies added yet",
                    color = MaterialTheme.colorScheme.outline
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 140.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(displayItems, key = { "saved_" + it.id }) { item ->
                    MoviePosterCard(item = item, onClick = { vm.selectMovie(item) })
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    state: UiState,
    vm: SimpleStreamViewModel,
    onAddSource: () -> Unit,
    onAddStream: () -> Unit
) {
    var tmdbKey by remember(state.settings.tmdbApiKey) { mutableStateOf(state.settings.tmdbApiKey) }
    var tmdbToken by remember(state.settings.tmdbAccessToken) { mutableStateOf(state.settings.tmdbAccessToken) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item {
            Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Manage your personal movie sources, servers, and streams", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }

        // Sources Section
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Personal Movie Sources", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        FilledTonalButton(onClick = onAddSource) {
                            Icon(Icons.Default.Add, null)
                            Spacer(Modifier.width(4.dp))
                            Text("Add Source")
                        }
                    }
                    Text("Add JSON manifests hosted on your own server, NAS, or network", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.height(8.dp))

                    if (state.settings.sources.isEmpty()) {
                        Text("Default movie library is currently loaded. You can attach your own server manifest above.", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        state.settings.sources.forEach { source ->
                            ListItem(
                                headlineContent = { Text(source.name) },
                                supportingContent = { Text(source.url, maxLines = 1) },
                                trailingContent = {
                                    IconButton(onClick = { vm.deleteSource(source.id) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        // Direct Streams Section
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Direct Streams", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        FilledTonalButton(onClick = onAddStream) {
                            Icon(Icons.Default.Add, null)
                            Spacer(Modifier.width(4.dp))
                            Text("Add Stream")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    state.settings.streams.forEach { stream ->
                        ListItem(
                            headlineContent = { Text(stream.title) },
                            supportingContent = { Text(listOfNotNull(stream.provider, stream.mimeType).joinToString(" • ")) },
                            trailingContent = {
                                Row {
                                    IconButton(onClick = { vm.playStreamEntry(stream) }) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = "Play")
                                    }
                                    IconButton(onClick = { vm.deleteStream(stream.id) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }

        // TMDB Optional Metadata
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("TMDB Metadata (Optional)", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("Optionally configure TMDB to search public movie posters and descriptions.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = tmdbKey,
                        onValueChange = { tmdbKey = it },
                        label = { Text("TMDB API Key") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = PasswordVisualTransformation()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = tmdbToken,
                        onValueChange = { tmdbToken = it },
                        label = { Text("TMDB Read Access Token") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = PasswordVisualTransformation()
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            vm.saveSettings(tmdbKey, tmdbToken, state.settings.sourceUrls.joinToString("\n"))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save Credentials")
                    }
                }
            }
        }
    }
}

@Composable
private fun MoviePosterCard(item: MediaItem, onClick: () -> Unit) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier
            .width(140.dp)
            .height(230.dp),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (!item.poster.isNullOrBlank()) {
                    AsyncImage(
                        model = item.poster,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Movie, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                    }
                }
                // Quality tag if available
                item.allStreams.firstOrNull()?.let { stream ->
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = stream.quality,
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
            Column(modifier = Modifier.padding(6.dp)) {
                Text(
                    text = item.title,
                    maxLines = 1,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = listOfNotNull(item.year, item.runtime).joinToString(" • "),
                    maxLines = 1,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
private fun ContinueWatchingCard(progress: WatchProgress, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .width(180.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (!progress.poster.isNullOrBlank()) {
                    AsyncImage(
                        model = progress.poster,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.PlayCircle,
                        contentDescription = "Resume",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
            LinearProgressIndicator(
                progress = progress.progressFraction,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
            )
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = progress.title,
                    maxLines = 1,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = progress.formattedResume,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun MovieCardRow(item: MediaItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!item.poster.isNullOrBlank()) {
                AsyncImage(
                    model = item.poster,
                    contentDescription = null,
                    modifier = Modifier
                        .size(60.dp, 88.dp)
                        .clip(RoundedCornerShape(6.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    listOfNotNull(item.type, item.year, item.runtime).joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
                if (!item.description.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        item.description,
                        maxLines = 2,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (item.isPlayable) {
                FilledIconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Play")
                }
            }
        }
    }
}

@Composable
private fun AddSourceDialog(
    onDismiss: () -> Unit,
    onSave: (name: String, url: String, headerName: String, headerValue: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var headerName by remember { mutableStateOf("") }
    var headerValue by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Personal Movie Source") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Source Label (e.g. Home NAS)") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("HTTPS/HTTP Manifest URL") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = headerName,
                    onValueChange = { headerName = it },
                    label = { Text("Auth Header (optional, e.g. Authorization)") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = headerValue,
                    onValueChange = { headerValue = it },
                    label = { Text("Auth Token / Value (optional)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (url.startsWith("http", ignoreCase = true)) {
                        onSave(name, url, headerName, headerValue)
                    }
                }
            ) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun AddStreamDialog(onDismiss: () -> Unit, onSave: (StreamEntry) -> Unit) {
    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var mime by remember { mutableStateOf("") }
    var provider by remember { mutableStateOf("Manual") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Direct Media Stream") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Title") }, singleLine = true)
                OutlinedTextField(url, { url = it }, label = { Text("Media URL (MP4 / HLS / DASH)") }, singleLine = true)
                OutlinedTextField(mime, { mime = it }, label = { Text("MIME type (optional)") }, singleLine = true)
                OutlinedTextField(provider, { provider = it }, label = { Text("Provider / Server") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (title.isNotBlank() && url.startsWith("http", ignoreCase = true)) {
                        onSave(
                            StreamEntry(
                                id = title.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_'),
                                title = title.trim(),
                                url = url.trim(),
                                mimeType = mime.trim().ifBlank { null },
                                provider = provider.trim().ifBlank { "Manual" }
                            )
                        )
                    }
                }
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
