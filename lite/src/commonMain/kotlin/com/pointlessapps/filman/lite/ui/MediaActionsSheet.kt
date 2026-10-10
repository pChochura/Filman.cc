package com.pointlessapps.filman.lite.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pointlessapps.filman.lite.data.Episode
import com.pointlessapps.filman.lite.data.StreamSource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaActionsSheet(
    state: SheetState,
    onDismiss: () -> Unit,
    onSeasonSelected: (Int) -> Unit,
    onEpisodeSelected: (Episode) -> Unit,
    onAction: (MediaAction) -> Unit,
    onSourceChosen: (StreamSource) -> Unit,
    onBackToActions: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(state.item.title, style = MaterialTheme.typography.titleLarge)
            if (state.sources.isNotEmpty()) {
                SourceList(state, onSourceChosen, onBackToActions)
                return@Column
            }
            if (state.isLoadingSeasons) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
            if (state.seasons.isNotEmpty()) EpisodePicker(state, onSeasonSelected, onEpisodeSelected)
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (state.isResolving) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator()
                    Text("Looking for sources…")
                }
            } else {
                val canAct = !state.item.isTvShow || state.episode != null
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { onAction(MediaAction.WATCH) }, enabled = canAct) { Text("Watch") }
                    OutlinedButton(onClick = { onAction(MediaAction.DOWNLOAD) }, enabled = canAct) { Text("Download") }
                }
            }
        }
    }
}

@Composable
private fun EpisodePicker(
    state: SheetState,
    onSeasonSelected: (Int) -> Unit,
    onEpisodeSelected: (Episode) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(state.seasons, key = { it.number }) { season ->
            FilterChip(
                selected = season.number == state.selectedSeason,
                onClick = { onSeasonSelected(season.number) },
                label = { Text(season.name) },
            )
        }
    }
    val episodes = state.seasons.firstOrNull { it.number == state.selectedSeason }?.episodes.orEmpty()
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 240.dp).padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(episodes, key = { "${it.season}_${it.episode}" }) { episode ->
            FilterChip(
                selected = episode == state.episode,
                onClick = { onEpisodeSelected(episode) },
                label = { Text(episode.label) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SourceList(state: SheetState, onSourceChosen: (StreamSource) -> Unit, onBack: () -> Unit) {
    Text(
        if (state.pendingAction == MediaAction.DOWNLOAD) "Choose a source to download" else "Choose a source to watch",
        style = MaterialTheme.typography.titleSmall,
    )
    LazyColumn(Modifier.heightIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(state.sources) { source ->
            OutlinedButton(onClick = { onSourceChosen(source) }, modifier = Modifier.fillMaxWidth()) {
                Text(source.label)
            }
        }
    }
    TextButton(onClick = onBack) { Text("Back") }
}
