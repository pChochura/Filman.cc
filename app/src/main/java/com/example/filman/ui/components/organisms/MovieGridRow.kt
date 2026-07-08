package com.example.filman.ui.components.organisms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.tv.material3.MaterialTheme
import com.example.filman.data.model.Movie
import com.example.filman.ui.components.molecules.MovieCard
import com.example.filman.ui.home.ContextMenuData
import com.example.filman.ui.theme.spacing

@Composable
fun MovieGridRow(
    movies: List<Movie>,
    onMovieClick: (String) -> Unit,
    onContextMenu: (ContextMenuData) -> Unit,
    modifier: Modifier = Modifier,
    firstItemModifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
    ) {
        movies.forEachIndexed { index, movie ->
            MovieCard(
                movie = movie,
                onClick = { onMovieClick(movie.url) },
                onLongClick = {
                    onContextMenu(
                        ContextMenuData(
                            url = movie.url,
                            title = movie.title,
                            posterUrl = movie.posterUrl,
                            isProgress = false,
                        ),
                    )
                },
                modifier = Modifier
                    .then(if (index == 0) firstItemModifier else Modifier)
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
