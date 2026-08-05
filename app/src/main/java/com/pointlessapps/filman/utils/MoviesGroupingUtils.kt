package com.pointlessapps.filman.utils

import com.pointlessapps.filman.data.model.MediaSource
import com.pointlessapps.filman.data.model.MovieItem
import com.pointlessapps.filman.ui.components.sections.MoviesGridItem

private val SUFFIX_REGEX = Regex(
    """\s*[-–]\s*(full\s*hd|hd|4k|uhd|cam|ts|dvdrip|bluray|blu-ray|webrip|web-dl|hdrip|hdtv|720p|1080p|2160p)\s*$""",
    RegexOption.IGNORE_CASE,
)
private val SPECIAL_CHARS_REGEX = Regex("[^a-z0-9 ]")
private val WHITESPACE_REGEX = Regex("\\s+")

internal fun List<MovieItem>.groupByTitle(): List<MoviesGridItem> {
    fun String.normalise(): String {
        return this
            .replace(SUFFIX_REGEX, "")
            .lowercase()
            .replace(SPECIAL_CHARS_REGEX, "")
            .replace(WHITESPACE_REGEX, " ")
            .trim()
    }

    val groups = LinkedHashMap<String, MutableList<MovieItem>>()
    for (item in this) {
        val key = item.titlePl.normalise().ifEmpty {
            item.titleEn?.normalise() ?: item.url
        }
        groups.getOrPut(key) { mutableListOf() }.add(item)
    }

    return groups.values.map { groupItems ->
        val representative = groupItems.firstOrNull {
            it.source == MediaSource.FILMAN
        } ?: groupItems.first()

        val alternativeSources = groupItems.filter { it.url != representative.url }

        if (alternativeSources.isEmpty()) {
            MoviesGridItem.Single(movieItem = representative)
        } else {
            MoviesGridItem.Group(
                movieItem = representative,
                alternativeSources = alternativeSources,
            )
        }
    }
}
