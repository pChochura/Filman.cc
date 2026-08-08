package com.pointlessapps.filman.data.local

object SettingsConstants {
    object Quality {
        const val AUTO = "auto"
        const val P1080 = "1080p"
        const val P720 = "720p"
        const val P480 = "480p"
        val ALL = listOf(AUTO, P1080, P720, P480)
    }

    object NextEpisodeInitialAppearance {
        const val SHOW = "show"
        const val SHOW_IN_OVERLAY = "show_in_overlay"
        const val DONT_SHOW = "dont_show"
        val ALL = listOf(SHOW, SHOW_IN_OVERLAY, DONT_SHOW)
    }

    object NextEpisodeSecondaryAppearance {
        const val SHOW_WITH_TIMER = "show_with_timer"
        const val JUST_SHOW = "just_show"
        const val SHOW_IN_OVERLAY = "show_in_overlay"
        const val DONT_SHOW = "dont_show"
        val ALL = listOf(SHOW_WITH_TIMER, JUST_SHOW, SHOW_IN_OVERLAY, DONT_SHOW)
    }
}
