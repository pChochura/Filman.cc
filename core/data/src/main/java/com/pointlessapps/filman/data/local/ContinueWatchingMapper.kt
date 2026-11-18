package com.pointlessapps.filman.data.local

import com.pointlessapps.filman.data.model.ProgressItem

fun List<ProgressItem>.distinctBySeries(): List<ProgressItem> =
    distinctBy { it.parentUrl ?: it.url }

fun List<ProgressItem>.toContinueWatchingItems(): List<ProgressItem> =
    mapNotNull { item ->
        if (item is ProgressItem.Watched) {
            if (item.parentUrl != null && item.parentUrl != item.url && item.hasNextEpisode) {
                ProgressItem.NextEpisode(
                    url = item.url,
                    parentUrl = item.parentUrl,
                    posterUrl = item.posterUrl,
                    titlePl = item.seriesTitle ?: item.titlePl,
                    seriesTitle = item.seriesTitle,
                )
            } else {
                null
            }
        } else {
            item
        }
    }

fun List<ProgressItem>.extractFullyWatchedUrls(): Set<String> =
    mapNotNull { item ->
        if (item is ProgressItem.Watched) {
            when {
                item.parentUrl == null || item.parentUrl == item.url -> item.url
                !item.hasNextEpisode -> item.parentUrl
                else -> null
            }
        } else {
            null
        }
    }.toSet()
