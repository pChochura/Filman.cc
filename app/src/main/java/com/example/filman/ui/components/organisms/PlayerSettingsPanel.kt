package com.example.filman.ui.components.organisms

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.RadioButton
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.example.filman.R
import com.example.filman.data.model.EmbedLink
import com.example.filman.ui.core.suppressKeyRepeat
import com.example.filman.ui.theme.spacing
import kotlinx.coroutines.delay

@Composable
fun PlayerSettingsPanel(
    servers: List<EmbedLink>,
    selectedServer: EmbedLink?,
    onServerSelected: (EmbedLink) -> Unit,
    playbackSpeed: Float,
    onPlaybackSpeedSelected: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var activeCategory by remember { mutableStateOf<String?>(null) }
    val mainFocusRequester = remember { FocusRequester() }
    val detailFocusRequester = remember { FocusRequester() }

    val speedOptions = listOf(0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

    LaunchedEffect(activeCategory) {
        delay(100)
        if (activeCategory == null) {
            mainFocusRequester.requestFocus()
        } else {
            detailFocusRequester.requestFocus()
        }
    }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(350.dp)
            .background(Color.Black.copy(alpha = 0.9f))
            .padding(MaterialTheme.spacing.extraLarge),
    ) {
        if (activeCategory == null) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
            ) {
                Text(
                    text = stringResource(R.string.player_settings).replace("⚙ ", ""),
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = MaterialTheme.spacing.medium),
                )

                ListItem(
                    selected = false,
                    onClick = { activeCategory = "servers" },
                    headlineContent = { Text(stringResource(R.string.player_select_server)) },
                    modifier = Modifier.focusRequester(mainFocusRequester),
                )

                ListItem(
                    selected = false,
                    onClick = { activeCategory = "speed" },
                    headlineContent = { Text("Playback Speed") },
                    trailingContent = {
                        Text(
                            text = "${
                                if (playbackSpeed == playbackSpeed.toInt().toFloat()) {
                                    playbackSpeed.toInt().toString()
                                } else {
                                    playbackSpeed
                                }
                            }x",
                            color = Color.Gray,
                        )
                    },
                )
            }
        } else {
            BackHandler(true) {
                activeCategory = null
            }
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = MaterialTheme.spacing.medium),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = { activeCategory = null },
                        modifier = Modifier
                            .suppressKeyRepeat()
                            .focusRequester(detailFocusRequester),
                    ) {
                        Text(stringResource(R.string.filters_back))
                    }
                    Text(
                        text = if (activeCategory == "servers") {
                            stringResource(R.string.player_select_server)
                        } else {
                            "Playback Speed"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        modifier = Modifier.padding(start = MaterialTheme.spacing.medium),
                    )
                }

                if (activeCategory == "servers") {
                    val groupedServers = servers.groupBy { it.serverName }

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        groupedServers.forEach { (serverName, serverList) ->
                            item {
                                Text(
                                    text = serverName,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color.Gray,
                                    modifier = Modifier.padding(
                                        top = MaterialTheme.spacing.small,
                                        bottom = 4.dp,
                                    ),
                                )
                            }

                            itemsIndexed(serverList) { _, server ->
                                Surface(
                                    onClick = { onServerSelected(server) },
                                    modifier = Modifier
                                        .suppressKeyRepeat()
                                        .fillMaxWidth(),
                                    colors = ClickableSurfaceDefaults.colors(
                                        containerColor = if (selectedServer == server) {
                                            Color.DarkGray
                                        } else {
                                            Color.Transparent
                                        },
                                    ),
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(MaterialTheme.spacing.medium),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = server.version.ifEmpty { "Default" },
                                        )
                                        if (server.quality.isNotEmpty()) {
                                            Box(
                                                modifier = Modifier
                                                    .background(
                                                        Color.DarkGray,
                                                        shape = MaterialTheme.shapes.small,
                                                    )
                                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                            ) {
                                                Text(
                                                    text = server.quality,
                                                    color = Color.LightGray,
                                                    fontSize = 12.sp,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else if (activeCategory == "speed") {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(speedOptions) { speed ->
                            val label = "${
                                if (speed == speed.toInt().toFloat()) {
                                    speed.toInt().toString()
                                } else {
                                    speed
                                }
                            }x"
                            ListItem(
                                selected = playbackSpeed == speed,
                                onClick = { onPlaybackSpeedSelected(speed) },
                                headlineContent = { Text(label) },
                                trailingContent = {
                                    RadioButton(
                                        selected = playbackSpeed == speed,
                                        onClick = null,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
