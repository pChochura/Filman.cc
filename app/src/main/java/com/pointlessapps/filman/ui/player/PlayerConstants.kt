package com.pointlessapps.filman.ui.player

object PlayerConstants {
    object PlaybackSpeed {
        const val X0_5 = 0.5f
        const val X0_75 = 0.75f
        const val X1_0 = 1.0f
        const val X1_25 = 1.25f
        const val X1_5 = 1.5f
        const val X2_0 = 2.0f
        val ALL = listOf(X0_5, X0_75, X1_0, X1_25, X1_5, X2_0)
    }

    object AspectRatio {
        const val FIT = 0
        const val CROP = 1
        const val STRETCH = 2
    }

    const val MENU_SOURCES_ID = "sources_menu"
}
