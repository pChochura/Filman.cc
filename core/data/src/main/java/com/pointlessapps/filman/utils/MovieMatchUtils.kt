package com.pointlessapps.filman.utils

import com.pointlessapps.filman.data.model.MovieItem
import kotlin.math.abs

fun Iterable<MovieItem>.findBestMatch(
    queryTitle: String,
    targetYear: Int?,
    minScore: Int = 10,
): MovieItem? {
    val scored = map { it to scoreMovieMatch(it, queryTitle, targetYear) }
    val best = scored.maxByOrNull { it.second } ?: return null
    return if (best.second >= minScore) best.first else null
}

fun Iterable<MovieItem>.findBestMatchWithFallback(
    title: String,
    originalTitle: String? = null,
    targetYear: Int? = null,
    minScore: Int = 10,
): MovieItem? {
    val matchTitle = findBestMatch(title, targetYear, minScore)
    if (matchTitle != null) return matchTitle

    if (!originalTitle.isNullOrBlank() && !originalTitle.equals(title, ignoreCase = true)) {
        return findBestMatch(originalTitle, targetYear, minScore)
    }

    return null
}

fun scoreMovieMatch(item: MovieItem, queryTitle: String, targetYear: Int?): Int {
    var score = 0

    val cleanQuery = queryTitle.lowercase().replace(Regex("[^a-z0-9\\s]"), "")
    val cleanTitlePl = item.titlePl.lowercase().replace(Regex("[^a-z0-9\\s]"), "")
    val cleanTitleEn = item.titleEn?.lowercase()?.replace(Regex("[^a-z0-9\\s]"), "") ?: ""

    val queryWords = cleanQuery.split("\\s+".toRegex()).filter { it.isNotBlank() }.toSet()
    val titlePlWords = cleanTitlePl.split("\\s+".toRegex()).filter { it.isNotBlank() }.toSet()
    val titleEnWords = cleanTitleEn.split("\\s+".toRegex()).filter { it.isNotBlank() }.toSet()

    if (cleanTitlePl == cleanQuery || cleanTitleEn == cleanQuery) {
        score += 100
    } else if (cleanTitlePl.contains(cleanQuery) || cleanTitleEn.contains(cleanQuery) || cleanQuery.contains(
            cleanTitlePl,
        ) || (cleanTitleEn.isNotBlank() && cleanQuery.contains(cleanTitleEn))
    ) {
        score += 50
    }

    val plIntersect = queryWords.intersect(titlePlWords).size
    val enIntersect = queryWords.intersect(titleEnWords).size
    score += maxOf(plIntersect, enIntersect) * 10

    if (targetYear != null) {
        if (item.year == targetYear) {
            score += 40
        } else if (item.year != null && abs(item.year - targetYear) <= 1) {
            score += 10
        }
    }

    return score
}

