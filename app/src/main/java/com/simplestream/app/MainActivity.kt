package com.simplestream.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage

@Composable
fun SimpleStreamApp(vm: SimpleStreamViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    var showAddSource by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("SimpleStream") }) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(tab == 0, { tab = 0 }, icon = { Icon(Icons.Default.Home, null) }, label = { Text("Home") })
                NavigationBarItem(tab == 1, { tab = 1 }, icon = { Icon(Icons.Default.Add, null) }, label = { Text("Sources") })
                NavigationBarItem(tab == 2, { tab = 2 }, icon = { Icon(Icons.Default.Settings, null) }, label = { Text("Settings") })
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                0 -> HomeScreen(state, vm)
                1 -> SourcesScreen(state, { showAddSource = true })
                2 -> SettingsScreen(state, vm)
            }
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }
    }
    if (showAddSource) AddSourceDialog(
        onDismiss = { showAddSource = false },
        onSave = { url ->
            val s = state.settings
            vm.saveSettings(s.tmdbApiKey, s.tmdbAccessToken, (s.sourceUrls + url).distinct().joinToString("\n"))
            showAddSource = false
        }
    )
}

@Composable
private fun HomeScreen(state: UiState, vm: SimpleStreamViewModel) {
    var directUrl by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = state.query, onValueChange = vm::setQuery, modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search movies, TV and sources") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = { IconButton(onClick = vm::search) { Icon(Icons.Default.Search, null) } },
            singleLine = true
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = directUrl, onValueChange = { directUrl = it }, modifier = Modifier.weight(1f),
                placeholder = { Text("Paste a direct media URL") }, singleLine = true
            )
            Spacer(Modifier.width(8.dp))
            FilledIconButton(onClick = {
                if (directUrl.startsWith("http")) vm.play(MediaItem("direct", "Direct stream", streamUrl = directUrl))
            }) { Icon(Icons.Default.PlayArrow, "Play") }
        }
        Spacer(Modifier.height(16.dp))
        if (state.results.isEmpty()) Text("Search TMDB or add source manifests in Settings.", style = MaterialTheme.typography.bodyLarge)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(state.results) { MediaCard(it, vm) }
        }
    }
}

@Composable
private fun MediaCard(item: MediaItem, vm: SimpleStreamViewModel) {
    ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(model = item.poster, contentDescription = null, modifier = Modifier.size(72.dp, 104.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium)
                Text(listOf(item.type, item.year).filterNotNull().joinToString(" • "), style = MaterialTheme.typography.bodyMedium)
                item.description?.let { Text(it, maxLines = 3, style = MaterialTheme.typography.bodySmall) }
            }
            if (item.streamUrl != null) IconButton(onClick = { vm.play(item) }) { Icon(Icons.Default.PlayArrow, "Play") }
        }
    }
}

@Composable
private fun SourcesScreen(state: UiState, onAdd: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Sources", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            FilledIconButton(onClick = onAdd) { Icon(Icons.Default.Add, "Add source") }
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.sources) { source ->
                ListItem(
                    headlineContent = { Text(source.name) },
                    supportingContent = { Text("${source.items.size} items • ${source.url}") }
                )
            }
        }
    }
}

@Composable
private fun SettingsScreen(state: UiState, vm: SimpleStreamViewModel) {
    var apiKey by remember(state.settings.tmdbApiKey) { mutableStateOf(state.settings.tmdbApiKey) }
    var token by remember(state.settings.tmdbAccessToken) { mutableStateOf(state.settings.tmdbAccessToken) }
    var sources by remember(state.settings.sourceUrls) { mutableStateOf(state.settings.sourceUrls.joinToString("\n")) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Connection settings", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(apiKey, { apiKey = it }, Modifier.fillMaxWidth(), label = { Text("TMDB API key") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(token, { token = it }, Modifier.fillMaxWidth(), label = { Text("TMDB access token") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(sources, { sources = it }, Modifier.fillMaxWidth().height(160.dp), label = { Text("Source manifest URLs, one per line") })
        Spacer(Modifier.height(12.dp))
        Button(onClick = { vm.saveSettings(apiKey, token, sources) }, modifier = Modifier.fillMaxWidth()) { Text("Save and load") }
        Spacer(Modifier.height(16.dp))
        Text("Manifest JSON: name + items[]. Items can contain title, type, year, poster, description and streamUrl.", style = MaterialTheme.typography.bodySmall)
        Text("Remote plugin code is not executed. Sources are data manifests, keeping the client smaller and safer.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun AddSourceDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add source") },
        text = { OutlinedTextField(url, { url = it }, label = { Text("Manifest URL") }, singleLine = true) },
        confirmButton = { TextButton(onClick = { if (url.startsWith("http")) onSave(url) }) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
