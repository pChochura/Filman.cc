package com.example.filman.ui.details.sections

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import com.example.filman.data.model.DetailedMedia

internal fun LazyListScope.posterSection(
    media: DetailedMedia,
) {
    item(key = "poster_section") {
        PosterSection()
    }
}

@Composable
private fun PosterSection() {

}
