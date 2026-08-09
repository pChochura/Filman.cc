package com.pointlessapps.filman.ui.player.model

import androidx.compose.runtime.Immutable

@Immutable
internal data class NextEpisodeButtonModel(
    val initialAppearanceModel: AppearanceModel? = null,
    val appearanceModel: AppearanceModel? = null,
) {
    @Immutable
    sealed interface AppearanceModel {
        val percentageOffset: Float
        val maxTimeOffset: Long

        @Immutable
        data class Show(
            override val percentageOffset: Float,
            override val maxTimeOffset: Long,
        ) : AppearanceModel

        @Immutable
        data class ShowInOverlay(
            override val percentageOffset: Float,
            override val maxTimeOffset: Long,
        ) : AppearanceModel

        @Immutable
        data class ShowWithTimer(
            override val percentageOffset: Float,
            override val maxTimeOffset: Long,
            val timerDuration: Long,
        ) : AppearanceModel
    }
}
