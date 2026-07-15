package com.example.filman.ui.details.sections

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import com.example.filman.data.model.Movie

internal fun LazyListScope.posterSection(
    movie: Movie
) {
    item(key = "poster_section") {
        PosterSection()
    }
}

@Composable
private fun PosterSection() {

}
