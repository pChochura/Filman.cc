package com.pointlessapps.filman.ui.home

import com.pointlessapps.filman.ui.core.SectionFocusRestorationId

internal fun HomeState.focusFallbackAfterRemovingFavorite(removedUrl: String): String? {
    val isLast = favorites.size == 1 && favorites.first().url == removedUrl
    if (!isLast) return null
    return progressItems.lastOrNull()?.url?.let {
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
