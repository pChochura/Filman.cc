package com.pointlessapps.filman.config

object TiwiConfig {
    // Deep Links
    const val DEEP_LINK_SCHEME = "tiwi"
    const val LEGACY_DEEP_LINK_SCHEME = "filman"
    const val DEEP_LINK_HOST_DETAILS = "details"
    const val DEEP_LINK_BASE_URI = "$DEEP_LINK_SCHEME://$DEEP_LINK_HOST_DETAILS"
    const val DEEP_LINK_PARAM_URL = "url"
    const val DEEP_LINK_PARAM_EPISODE_URL = "episodeUrl"
    const val DEEP_LINK_PARAM_AUTOPLAY = "autoplay"
    const val DEEP_LINK_PARAM_FROM_RECOMMENDATION = "fromRecommendation"
    const val DEEP_LINK_PARAM_VALUE_TRUE = "true"
}

typealias FilmanConfig = TiwiConfig
