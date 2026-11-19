package com.pointlessapps.filman.ui.favorites

import androidx.compose.runtime.Immutable
import com.pointlessapps.filman.data.model.DetailsRequest
import com.pointlessapps.filman.ui.base.FilmanEvent
import com.pointlessapps.filman.ui.base.SharedState
import com.pointlessapps.filman.ui.base.StateWithShared

sealed interface FavoritesEvent : FilmanEvent

@Immutable
data class FavoritesState(
    override val shared: SharedState = SharedState(),
) : StateWithShared<FavoritesState> {
    override fun copyWithShared(shared: SharedState) = copy(shared = shared)
}

sealed interface FavoritesEffect {
    data object NavigateToAuth : FavoritesEffect

    data class NavigateToDetails(
        val request: DetailsRequest,
    ) : FavoritesEffect
}
