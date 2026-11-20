package com.pointlessapps.filman

import android.content.Intent
import com.pointlessapps.filman.config.TiwiConfig
import com.pointlessapps.filman.config.TiwiConfig.DEEP_LINK_PARAM_AUTOPLAY
import com.pointlessapps.filman.config.TiwiConfig.DEEP_LINK_PARAM_FROM_RECOMMENDATION
import com.pointlessapps.filman.config.TiwiConfig.DEEP_LINK_PARAM_VALUE_TRUE
import com.pointlessapps.filman.data.model.DetailsRequest

internal data class DeepLinkNavigation(
    val route: Route.Details,
    val shouldResetToHome: Boolean,
)

internal fun parseDeepLinkIntent(intent: Intent?): DeepLinkNavigation? {
    val data = intent?.data ?: return null
    if ((data.scheme != TiwiConfig.DEEP_LINK_SCHEME && data.scheme != TiwiConfig.LEGACY_DEEP_LINK_SCHEME) ||
        data.host != TiwiConfig.DEEP_LINK_HOST_DETAILS
    ) {
        return null
    }

    val url = data.getQueryParameter(TiwiConfig.DEEP_LINK_PARAM_URL) ?: return null
    val episodeUrl = data.getQueryParameter(TiwiConfig.DEEP_LINK_PARAM_EPISODE_URL)
    val autoPlay = data.getQueryParameter(DEEP_LINK_PARAM_AUTOPLAY) == DEEP_LINK_PARAM_VALUE_TRUE ||
        data.getQueryParameter("autoPlay") == DEEP_LINK_PARAM_VALUE_TRUE
    val fromRecommendation =
        data.getQueryParameter(DEEP_LINK_PARAM_FROM_RECOMMENDATION) == DEEP_LINK_PARAM_VALUE_TRUE

    return DeepLinkNavigation(
        route = Route.Details(
            request = DetailsRequest.Url(url),
            autoPlay = autoPlay,
            episodeUrl = episodeUrl,
        ),
        shouldResetToHome = fromRecommendation || autoPlay,
    )
}
