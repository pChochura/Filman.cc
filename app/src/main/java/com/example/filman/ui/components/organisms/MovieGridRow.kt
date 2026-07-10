package com.example.filman.ui.components.organisms

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.tv.material3.MaterialTheme
import com.example.filman.data.model.Movie
import com.example.filman.ui.components.molecules.MovieCard
import com.example.filman.ui.theme.spacing

@Composable
fun MovieGridRow(
    movies: List<Movie>,
    onMovieClick: (Movie) -> Unit,
    onContextMenu: (Movie) -> Unit,
    modifier: Modifier = Modifier,
    firstItemModifier: Modifier = Modifier,
) {
    val firstItemRequester = remember { FocusRequester() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .focusGroup()
            .focusRestorer(firstItemRequester),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
    ) {
        movies.forEachIndexed { index, movie ->
            MovieCard(
                movie = movie,
                onClick = onMovieClick,
                onLongClick = onContextMenu,
                modifier = Modifier
                    .then(
                        if (index == 0) {
                            Modifier
                                .focusRequester(firstItemRequester)
                                .then(firstItemModifier)
                        } else {
                            Modifier
                        },
                    )
                    .weight(1f)
                    .aspectRatio(150f / 220f),
            )
        }
        // Fill remaining space if last row is incomplete
        repeat(5 - movies.size) {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}
