package com.pointlessapps.filman.data.local

import com.pointlessapps.filman.data.model.MediaRef
import com.pointlessapps.filman.data.model.MovieItem
import com.pointlessapps.filman.data.model.ProgressItem

const val MIN_ENGAGEMENT_PROGRESS = 0.05f
const val MIN_ENGAGEMENT_MS = 120_000L // 2 minutes
const val ACTIVE_WATCH_WINDOW_MS = 30L * 24 * 60 * 60 * 1000L // 30 days
const val RECENTLY_FINISHED_WINDOW_MS = 180L * 24 * 60 * 60 * 1000L // 180 days

fun String.canonicalUrl(): String = MediaRef.parse(this)?.canonicalKey ?: this

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
                    season = item.season,
                    episode = item.episode,
                    episodeTitle = item.episodeTitle,
                    hasNextEpisode = item.hasNextEpisode,
                    timestamp = item.timestamp,
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
                item.parentUrl == null || item.parentUrl == item.url -> item.url.canonicalUrl()
                !item.hasNextEpisode -> (item.parentUrl ?: item.url).canonicalUrl()
                else -> null
            }
        } else {
            null
        }
    }.toSet()

fun List<MovieItem>.filterUnwatched(fullyWatchedUrls: Set<String>): List<MovieItem> =
    filterNot { it.url.canonicalUrl() in fullyWatchedUrls }

fun ProgressItem.isSampled(allProgressItems: List<ProgressItem> = emptyList()): Boolean {
    if (this !is ProgressItem.InProgress) return false
    val isMinimal = progressPercentage < MIN_ENGAGEMENT_PROGRESS && progressMs < MIN_ENGAGEMENT_MS
    if (!isMinimal) return false

    val parent = parentUrl
    if (parent != null && parent != url) {
        // If on a later episode, it's part of an ongoing series watch, not a pilot sample
        if ((season ?: 1) > 1 || (episode ?: 1) > 1) {
            return false
        }
        val otherItems = allProgressItems.filter {
            (it.parentUrl == parent || it.url == parent) && it.url != url
        }
        val hasPreviousOrEngaged = otherItems.any {
            it is ProgressItem.Watched ||
                (it is ProgressItem.InProgress && (it.progressPercentage >= MIN_ENGAGEMENT_PROGRESS || it.progressMs >= MIN_ENGAGEMENT_MS))
        }
        return !hasPreviousOrEngaged
    }
    return true
}

fun ProgressItem.isActive(
    now: Long = System.currentTimeMillis(),
    activeWindowMs: Long = ACTIVE_WATCH_WINDOW_MS,
): Boolean = now - timestamp <= activeWindowMs

fun ProgressItem.isDropped(
    now: Long = System.currentTimeMillis(),
    activeWindowMs: Long = ACTIVE_WATCH_WINDOW_MS,
): Boolean = now - timestamp > activeWindowMs

fun List<ProgressItem>.toActiveContinueWatchingItems(
    now: Long = System.currentTimeMillis(),
    allProgressItems: List<ProgressItem> = this,
): List<ProgressItem> =
    toContinueWatchingItems().filter { item ->
        !item.isSampled(allProgressItems) && item.isActive(now)
    }

fun List<ProgressItem>.toJumpBackInItems(
    now: Long = System.currentTimeMillis(),
    allProgressItems: List<ProgressItem> = this,
): List<ProgressItem> =
    toContinueWatchingItems().filter { item ->
        !item.isSampled(allProgressItems) && item.isDropped(now)
    }
