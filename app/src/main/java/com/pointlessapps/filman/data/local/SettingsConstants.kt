package com.pointlessapps.filman.data.local

object SettingsConstants {
    object Quality {
        const val AUTO = "auto"
        const val P1080 = "1080p"
        const val P720 = "720p"
        const val P480 = "480p"
        val ALL = listOf(AUTO, P1080, P720, P480)
    }
}
