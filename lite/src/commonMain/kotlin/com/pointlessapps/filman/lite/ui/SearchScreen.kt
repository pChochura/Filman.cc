package com.pointlessapps.filman.lite.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.pointlessapps.filman.lite.data.MediaItem
import com.pointlessapps.filman.lite.download.DownloadItem
import com.pointlessapps.filman.lite.download.DownloadState
import com.pointlessapps.filman.lite.player.BackHandler
import com.pointlessapps.filman.lite.player.PlaybackSource
import com.pointlessapps.filman.lite.player.VideoPlayer
import org.koin.compose.koinInject

@Composable
fun SearchScreen() {
    val repository = koinInject<com.pointlessapps.filman.lite.data.LiteRepository>()
    val downloads = koinInject<com.pointlessapps.filman.lite.download.DownloadManager>()
    val settings = koinInject<com.pointlessapps.filman.lite.ServerSettings>()
    val filmanSession = koinInject<com.pointlessapps.filman.lite.FilmanSession>()
    val viewModel = viewModel { LiteViewModel(repository, downloads, settings, filmanSession) }

    val search by viewModel.search.collectAsState()
    val sheet by viewModel.sheet.collectAsState()
    val player by viewModel.player.collectAsState()
    val downloadItems by viewModel.downloadItems.collectAsState()
    val serverUrl by viewModel.serverUrl.collectAsState()
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    val showLogin by viewModel.showLogin.collectAsState()
    var showServerDialog by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            SearchContent(
                search = search,
                downloads = downloadItems,
                onQueryChange = viewModel::onQueryChange,
                onRetry = viewModel::retrySearch,
                onItemClick = viewModel::openItem,
                onPlayDownload = viewModel::playDownload,
                onDeleteDownload = viewModel::deleteDownload,
                onOpenServerSettings = { showServerDialog = true },
                isLoggedIn = isLoggedIn,
                onAccountClick = { if (isLoggedIn) viewModel.logout() else viewModel.openLogin() },
            )
            if (showServerDialog) {
                ServerDialog(
                    currentUrl = serverUrl,
                    onSave = { viewModel.updateServerUrl(it); showServerDialog = false },
                    onReset = { viewModel.resetServerUrl(); showServerDialog = false },
                    onDismiss = { showServerDialog = false },
                )
            }
            sheet?.let { state ->
                MediaActionsSheet(
                    state = state,
                    onDismiss = viewModel::closeSheet,
                    onSeasonSelected = viewModel::selectSeason,
                    onEpisodeSelected = viewModel::selectEpisode,
                    onAction = viewModel::requestAction,
                    onSourceChosen = viewModel::chooseSource,
                    onBackToActions = viewModel::clearSources,
                )
            }
            player?.let { PlayerOverlay(it, onClose = viewModel::closePlayer) }
            if (showLogin) {
                FilmanLoginScreen(onLoggedIn = viewModel::onLoggedIn, onClose = viewModel::closeLogin)
            }
        }
    }
}

@Composable
private fun SearchContent(
    search: SearchState,
    downloads: List<DownloadItem>,
    onQueryChange: (String) -> Unit,
    onRetry: () -> Unit,
    onItemClick: (MediaItem) -> Unit,
    onPlayDownload: (DownloadItem) -> Unit,
    onDeleteDownload: (DownloadItem) -> Unit,
    onOpenServerSettings: () -> Unit,
    isLoggedIn: Boolean,
    onAccountClick: () -> Unit,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = search.query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("Search movies and TV shows") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            )
            TextButton(onClick = onAccountClick) { Text(if (isLoggedIn) "Log out" else "Log in") }
            TextButton(onClick = onOpenServerSettings) { Text("Server") }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (downloads.isNotEmpty()) {
                item(key = "downloads_header") { SectionTitle("Downloads") }
                items(downloads, key = { "dl_${it.id}" }) {
                    DownloadRow(it, onPlay = { onPlayDownload(it) }, onDelete = { onDeleteDownload(it) })
                }
            }
            when {
                search.isLoading -> item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                search.error != null -> item(key = "error") {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(search.error, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = onRetry) { Text("Retry") }
                    }
                }
                search.hasSearched && search.results.isEmpty() -> item(key = "empty") {
                    Text("Nothing found", Modifier.fillMaxWidth().padding(24.dp))
                }
                else -> {
                    if (search.results.isNotEmpty()) item(key = "results_header") { SectionTitle("Results") }
                    items(search.results, key = { "r_${it.isTvShow}_${it.id}" }) {
                        ResultRow(it, onClick = { onItemClick(it) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ServerDialog(
    currentUrl: String,
    onSave: (String) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(currentUrl) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Server address") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Address of the Filman backend, e.g. http://100.64.0.1:8080 or https://my-server.example.com")
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("Save") } },
        dismissButton = {
            Row {
                TextButton(onClick = onReset) { Text("Reset") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun SectionTitle(text: String) =
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))

@Composable
private fun Poster(url: String?, modifier: Modifier = Modifier) {
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.width(64.dp).height(96.dp).clip(RoundedCornerShape(8.dp))
            .then(Modifier),
    )
}

@Composable
private fun ResultRow(item: MediaItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Poster(item.posterUrl)
        Column(Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                if (item.isTvShow) "TV show" else "Movie",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                item.overview,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DownloadRow(item: DownloadItem, onPlay: () -> Unit, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Poster(item.posterUrl)
        Column(Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            when (item.state) {
                DownloadState.DOWNLOADING -> {
                    val progress = item.progress
                    if (progress != null) {
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    }
                    Text(
                        progress?.let { "${(it * 100).toInt()}%" } ?: "${item.downloadedBytes / 1_000_000} MB",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                DownloadState.COMPLETED -> Text("Saved offline", style = MaterialTheme.typography.labelMedium)
                DownloadState.FAILED -> Text(
                    item.error ?: "Failed",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        Column {
            if (item.state == DownloadState.COMPLETED) TextButton(onClick = onPlay) { Text("Play") }
            TextButton(onClick = onDelete) {
                Text(if (item.state == DownloadState.DOWNLOADING) "Cancel" else "Delete")
            }
        }
    }
}

@Composable
private fun PlayerOverlay(source: PlaybackSource, onClose: () -> Unit) {
    BackHandler(enabled = true, onBack = onClose)
    Surface(Modifier.fillMaxSize(), color = androidx.compose.ui.graphics.Color.Black) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(source.title, Modifier.weight(1f).padding(start = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Button(onClick = onClose) { Text("Close") }
            }
            VideoPlayer(source, Modifier.fillMaxWidth().weight(1f))
        }
    }
}
