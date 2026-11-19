package com.pointlessapps.filman.ui.home

import com.pointlessapps.filman.ui.core.SectionFocusRestorationId

internal fun HomeState.focusFallbackAfterRemovingJumpBackIn(removedUrl: String): String? {
    val isLast = jumpBackInItems.size == 1 && jumpBackInItems.first().url == removedUrl
    if (!isLast) return null
    return watchlist.lastOrNull()?.url?.let {
        SectionFocusRestorationId.moviesRowPrefix(it)
    } ?: progressItems.lastOrNull()?.url?.let {
        "${SectionFocusRestorationId.CONTINUE_WATCHING.prefix}$it"
    } ?: featuredItems.lastOrNull()?.url?.let {
        "${SectionFocusRestorationId.FEATURED.prefix}$it"
    }
}

internal fun HomeState.focusFallbackAfterRemovingProgress(removedUrl: String): String? {
    val isLast = progressItems.size == 1 && progressItems.first().url == removedUrl
    if (!isLast) return null
    return featuredItems.lastOrNull()?.url?.let {
        "${SectionFocusRestorationId.FEATURED.prefix}$it"
    }
}

internal fun HomeState.focusFallbackAfterRemovingWatchlist(removedUrl: String): String? {
    val isLast = watchlist.size == 1 && watchlist.first().url == removedUrl
    if (!isLast) return null
    return progressItems.lastOrNull()?.url?.let {
        "${SectionFocusRestorationId.CONTINUE_WATCHING.prefix}$it"
    } ?: featuredItems.lastOrNull()?.url?.let {
        "${SectionFocusRestorationId.FEATURED.prefix}$it"
    }
}
