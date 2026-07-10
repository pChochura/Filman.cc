package com.example.filman.ui.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.example.filman.R
import com.example.filman.data.model.Movie
import com.example.filman.ui.components.atoms.ButtonStyle
import com.example.filman.ui.components.atoms.FilmanButton
import com.example.filman.ui.home.ContextMenuData
import com.example.filman.ui.home.HomeEvent
import com.example.filman.ui.theme.spacing

@Composable
fun HomeContextMenu(
    data: ContextMenuData,
    favorites: List<Movie>,
    onEvent: (HomeEvent) -> Unit,
    onDismiss: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    var targetUrl = data.seriesUrl ?: if (data.isProgress) {
        data.url.replace(Regex("(?i)/s\\d+(?:e\\d+)?/?$"), "")
    } else {
        data.url
    }
    targetUrl = targetUrl.replace(Regex("^https?://[^/]+"), "")

    val targetTitle =
        if (data.isProgress && data.title.contains(" - ")) {
            data.title.substringBefore(" - ").trim()
        } else {
            data.title
        }

    val isFavorite = favorites.any { it.url == targetUrl }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
    ) {
        Text(
            text = data.title,
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
            modifier = Modifier.padding(bottom = MaterialTheme.spacing.large),
        )
        if (data.isProgress) {
            FilmanButton(
                onClick = {
                    onEvent(HomeEvent.RemoveFromProgress(data.url))
                    onDismiss()
                },
                modifier = Modifier
                    .focusRequester(focusRequester)
                    .fillMaxWidth(),
                style = ButtonStyle.Primary,
            ) {
                Text(stringResource(R.string.remove_from_continue_watching))
            }
        }
        FilmanButton(
            onClick = {
                if (isFavorite) {
                    onEvent(HomeEvent.RemoveFromFavorites(targetUrl))
                } else {
                    onEvent(
                        HomeEvent.AddToFavorites(
                            Movie(
                                url = targetUrl,
                                title = targetTitle,
                                posterUrl = data.posterUrl,
                            ),
                        ),
                    )
                }
                onDismiss()
            },
            modifier = Modifier
                .then(
                    if (!data.isProgress) {
                        Modifier.focusRequester(focusRequester)
                    } else {
                        Modifier
                    },
                )
                .fillMaxWidth(),
            style = ButtonStyle.Secondary,
        ) {
            Text(
                if (isFavorite) {
                    stringResource(R.string.remove_from_favorites)
                } else {
                    stringResource(R.string.add_to_favorites)
                },
            )
        }
    }
}
