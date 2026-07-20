package com.example.filman.ui.components.sections

import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.runtime.Composable
import com.example.filman.data.model.ActorDetails

internal fun LazyGridScope.actorInfoSection(
    actorDetails: ActorDetails?,
) {
    if (actorDetails == null) return

    item(
        key = "actor_info_section",
        span = { GridItemSpan(maxLineSpan) },
        contentType = "ActorInfoContent",
    ) {
        ActorInfoContent(
            actorDetails = actorDetails,
        )
    }
}

@Composable
private fun ActorInfoContent(
    actorDetails: ActorDetails,
) {

}
