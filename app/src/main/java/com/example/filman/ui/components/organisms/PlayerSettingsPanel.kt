package com.example.filman.ui.components.organisms

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.example.filman.R
import com.example.filman.data.model.EmbedLink
import com.example.filman.ui.core.suppressKeyRepeat
import com.example.filman.ui.theme.spacing

@Composable
fun PlayerSettingsPanel(
    servers: List<EmbedLink>,
    selectedServer: EmbedLink?,
    onServerSelected: (EmbedLink) -> Unit,
    modifier: Modifier = Modifier,
) {
    val firstServerFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        firstServerFocusRequester.requestFocus()
    }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(350.dp)
            .background(Color.Black.copy(alpha = 0.9f))
            .padding(MaterialTheme.spacing.extraLarge),
    ) {
        Column {
            Text(
                text = stringResource(R.string.player_select_server),
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
            )
            Spacer(modifier = Modifier.height(MaterialTheme.spacing.medium))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
                itemsIndexed(servers) { index, server ->
                    Surface(
                        onClick = { onServerSelected(server) },
                        modifier = if (index == 0) {
                            Modifier
                                .suppressKeyRepeat()
                                .fillMaxWidth()
                                .focusRequester(firstServerFocusRequester)
                        } else {
                            Modifier
                                .suppressKeyRepeat()
                                .fillMaxWidth()
                        },
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = if (selectedServer == server) {
                                Color.DarkGray
                            } else {
                                Color.Transparent
                            },
                        ),
                    ) {
                        Text(
                            text = server.serverName,
                            color = Color.White,
                            modifier = Modifier.padding(MaterialTheme.spacing.medium),
                        )
                    }
                }
            }
        }
    }
}
