package com.simplestream.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
    var showAddDatabase by remember { mutableStateOf(false) }
    var showAddStream by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val scheme = if (android.os.Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (dark) darkColorScheme() else lightColorScheme()

    MaterialTheme(colorScheme = scheme) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("SimpleStream") },
                    actions = { if (state.loading) CircularProgressIndicator(Modifier.size(24.dp)) }
                )
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(tab == 0, { tab = 0 }, icon = { Icon(Icons.Default.Home, null) }, label = { Text("Home") })
                    NavigationBarItem(tab == 1, { tab = 1 }, icon = { Icon(Icons.Default.LibraryAdd, null) }, label = { Text("Sources") })
                    NavigationBarItem(tab == 2, { tab = 2 }, icon = { Icon(Icons.Default.Settings, null) }, label = { Text("Settings") })
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (tab) {
                    0 -> HomeScreen(state, vm)
                    1 -> SourcesScreen(state, vm, { showAddSource = true }, { showAddStream = true })
                    2 -> SettingsScreen(state, vm) { showAddDatabase = true }
                }
            }
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
    if (showAddDatabase) AddDatabaseDialog(
        onDismiss = { showAddDatabase = false },
        onSave = { db ->
            vm.saveDatabases(state.settings.databases + db)
            showAddDatabase = false
        }
    )
    if (showAddStream) AddStreamDialog(
        onDismiss = { showAddStream = false },
        onSave = { entry ->
            vm.addStream(entry)
            showAddStream = false
        }
    )
}

@Composable
private fun HomeScreen(state: UiState, vm: SimpleStreamViewModel) {
    var directUrl by remember { mutableStateOf("") }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)
    ) {
        item {
            OutlinedTextField(
                value = state.query,
                onValueChange = vm::setQuery,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search movies, TV, anime and video") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = { IconButton(onClick = vm::search) { Icon(Icons.Default.Search, null) } },
                singleLine = true
            )
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = directUrl,
                    onValueChange = { directUrl = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Direct MP4 / M3U8 / MPD") },
                    singleLine = true
                )
                Spacer(Modifier.width(8.dp))
                FilledIconButton(onClick = {
                    if (directUrl.startsWith("http", true)) {
                        vm.play(MediaItem("direct", "Direct stream", streamUrl = directUrl))
                    }
                }) { Icon(Icons.Default.PlayArrow, "Play") }
            }
        }

        if (!state.message.isNullOrBlank()) {
            item { Text(state.message, style = MaterialTheme.typography.bodyMedium) }
        }

        if (state.results.isNotEmpty()) {
            item { Text("Search results", style = MaterialTheme.typography.headlineSmall) }
            items(state.results, key = { "search_" + it.id }) { MediaCard(it, vm) }
        }

        if (state.results.isEmpty() && state.homeSections.isNotEmpty()) {
            state.homeSections.forEach { section ->
                item {
                    Text(section.title, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(4.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(end = 16.dp)
                    ) {
                        items(section.items, key = { "home_" + section.title + "_" + it.id }) { item ->
                            PosterCard(item, vm)
                        }
                    }
                }
            }
        }

        if (state.results.isEmpty() && state.homeSections.isEmpty()) {
            item {
                Text(
                    "Configure TMDB in Settings to populate the home catalogue. TMDB supplies metadata; a playable stream must come from a source you are authorized to use.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        if (state.settings.streams.isNotEmpty()) {
            item { Text("Added streams", style = MaterialTheme.typography.headlineSmall) }
            items(state.settings.streams, key = { "stream_" + it.id }) { stream ->
                StreamCard(stream, vm)
            }
        }
    }
}

@Composable
private fun PosterCard(item: MediaItem, vm: SimpleStreamViewModel) {
    ElevatedCard(onClick = { if (item.streamUrl != null) vm.play(item) }, modifier = Modifier.width(150.dp)) {
        Column {
            AsyncImage(model = item.poster, contentDescription = null, modifier = Modifier.fillMaxWidth().height(210.dp))
            Column(Modifier.padding(10.dp)) {
                Text(item.title, maxLines = 2, style = MaterialTheme.typography.titleSmall)
                Text(listOfNotNull(item.type, item.year).joinToString(" • "), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun MediaCard(item: MediaItem, vm: SimpleStreamViewModel) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(model = item.poster, contentDescription = null, modifier = Modifier.size(72.dp, 104.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium)
                Text(listOfNotNull(item.type, item.year, item.provider).joinToString(" • "), style = MaterialTheme.typography.bodyMedium)
                item.description?.let { Text(it, maxLines = 3, style = MaterialTheme.typography.bodySmall) }
            }
            if (item.streamUrl != null) {
                IconButton(onClick = { vm.play(item) }) { Icon(Icons.Default.PlayArrow, "Play") }
            }
        }
    }
}

@Composable
private fun StreamCard(entry: StreamEntry, vm: SimpleStreamViewModel) {
    ListItem(
        headlineContent = { Text(entry.title) },
        supportingContent = { Text(listOfNotNull(entry.provider, entry.mimeType).joinToString(" • ")) },
        trailingContent = {
            IconButton(onClick = { vm.play(entry) }) {
                Icon(Icons.Default.PlayArrow, "Play")
            }
        }
    )
}

@Composable
private fun SourcesScreen(
    state: UiState,
    vm: SimpleStreamViewModel,
    onAddSource: () -> Unit,
    onAddStream: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Streaming sources", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            IconButton(onClick = onAddStream) { Icon(Icons.Default.VideoLibrary, "Add stream") }
            FilledIconButton(onClick = onAddSource) { Icon(Icons.Default.Add, "Add source") }
        }
        Text("Add manifest URLs or individual direct media URLs. Direct playback supports Media3-compatible media; protected services require their licensed DRM configuration.", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(12.dp))

        if (state.settings.streams.isNotEmpty()) {
            Text("Direct streams", style = MaterialTheme.typography.titleLarge)
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(state.settings.streams, key = { it.id }) { StreamCard(it, vm) }
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.sources) { source ->
                    ListItem(
                        headlineContent = { Text(source.name) },
                        supportingContent = { Text(source.error ?: (source.items.size.toString() + " items • " + source.url), maxLines = 2) },
                        leadingContent = { Icon(if (source.error == null) Icons.Default.CheckCircle else Icons.Default.Warning, null) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(state: UiState, vm: SimpleStreamViewModel, onAddDatabase: () -> Unit) {
    var apiKey by remember(state.settings.tmdbApiKey) { mutableStateOf(state.settings.tmdbApiKey) }
    var token by remember(state.settings.tmdbAccessToken) { mutableStateOf(state.settings.tmdbAccessToken) }
    var sources by remember(state.settings.sourceUrls) { mutableStateOf(state.settings.sourceUrls.joinToString("\n")) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("TMDB", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(apiKey, { apiKey = it }, Modifier.fillMaxWidth(), label = { Text("TMDB API key") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(token, { token = it }, Modifier.fillMaxWidth(), label = { Text("TMDB Read Access Token") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
        Spacer(Modifier.height(8.dp))
        Button(onClick = { vm.saveSettings(apiKey, token, sources) }, modifier = Modifier.fillMaxWidth()) { Text("Save credentials") }

        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Databases", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            FilledIconButton(onClick = onAddDatabase) { Icon(Icons.Default.Add, "Add database") }
        }
        Text("Add REST JSON databases without changing the APK. Use {query} in an endpoint when the API expects the search term in the URL.", style = MaterialTheme.typography.bodySmall)
        state.settings.databases.forEach { db ->
            ListItem(
                headlineContent = { Text(db.name) },
                supportingContent = { Text(db.endpoint, maxLines = 1) },
                trailingContent = { Text(if (db.enabled) "ON" else "OFF") }
            )
        }

        Spacer(Modifier.height(14.dp))
        Text("Streaming manifests", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            sources, { sources = it },
            Modifier.fillMaxWidth().height(130.dp),
            label = { Text("Manifest URLs, one per line") }
        )
        Spacer(Modifier.height(8.dp))
        Text("For a provider webpage or provider-specific service, add a dedicated adapter rather than trying to scrape or bypass access controls. For DRM, supply the provider's legitimate license configuration.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun AddSourceDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add streaming source") },
        text = { OutlinedTextField(url, { url = it }, label = { Text("HTTPS manifest URL") }, singleLine = true) },
        confirmButton = { TextButton(onClick = { if (url.startsWith("http", true)) onSave(url.trim()) }) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
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
        title = { Text("Add direct stream") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Title") }, singleLine = true)
                OutlinedTextField(url, { url = it }, label = { Text("Media URL") }, singleLine = true)
                OutlinedTextField(mime, { mime = it }, label = { Text("MIME type (optional)") }, singleLine = true, placeholder = { Text("video/mp4 or application/x-mpegURL") })
                OutlinedTextField(provider, { provider = it }, label = { Text("Provider label") }, singleLine = true)
                Text("Use this for media URLs you are authorized to access. HLS/DASH URLs can be entered even when their filename does not reveal the format, by supplying the MIME type.")
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (title.isNotBlank() && url.startsWith("http", true)) {
                    onSave(StreamEntry(
                        id = title.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_'),
                        title = title.trim(),
                        url = url.trim(),
                        mimeType = mime.trim().ifBlank { null },
                        provider = provider.trim().ifBlank { "Manual" }
                    ))
                }
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun AddDatabaseDialog(onDismiss: () -> Unit, onSave: (DatabaseConfig) -> Unit) {
    var name by remember { mutableStateOf("") }
    var endpoint by remember { mutableStateOf("") }
    var parameter by remember { mutableStateOf("query") }
    var credential by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(CredentialMode.NONE) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add API database") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(endpoint, { endpoint = it }, label = { Text("Search endpoint or URL with {query}") }, singleLine = true)
                OutlinedTextField(parameter, { parameter = it }, label = { Text("Search parameter") }, singleLine = true)
                OutlinedTextField(credential, { credential = it }, label = { Text("API key / token (optional)") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                Text("Authentication")
                CredentialMode.values().forEach { option ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(mode == option, { mode = option })
                        Text(option.name.replace('_', ' '))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank() && endpoint.startsWith("http", true)) {
                    onSave(DatabaseConfig(
                        id = name.lowercase().replace(" ", "_"),
                        name = name.trim(),
                        endpoint = endpoint.trim(),
                        searchParameter = parameter.trim().ifBlank { "query" },
                        credentialMode = mode,
                        credential = credential.trim()
                    ))
                }
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
