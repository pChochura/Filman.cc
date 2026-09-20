package com.pointlessapps.filman.utils

import com.pointlessapps.filman.data.model.MovieItem
import kotlin.math.abs

internal fun Iterable<MovieItem>.findBestMatch(queryTitle: String, targetYear: Int?): MovieItem? {
    return maxByOrNull { item ->
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

        score
    }
}
