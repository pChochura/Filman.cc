package com.example.filman.ui.home.utils

internal enum class HomeSectionFocusRestorationId(val prefix: String) {
    FEATURED("featured_"),
    CONTINUE_WATCHING("continue_"),
    RECOMMENDED("recommended_");

    companion object {
        fun moviesRowPrefix(titleId: Int): String = "movies_row_${titleId}_"
    }
}
