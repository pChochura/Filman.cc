package com.pointlessapps.filman.data.api

import android.util.Log
import com.pointlessapps.filman.data.model.DetailedMedia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import kotlin.math.abs

class YoutubeSearchService {

    companion object {
        private const val TAG = "YoutubeSearchService"
        private const val MIN_MOVIE_DURATION_SEC = 15 * 60L // 15 minutes
        private const val MAX_DURATION_DEVIATION_RATIO = 0.30 // 30% deviation from expected runtime
        private val EXCLUDED_TITLE_KEYWORDS = listOf(
            "trailer", "teaser", "review", "reaction", "video essay", "behind the scenes"
        )
    }

    suspend fun searchFullMovie(detailedMedia: DetailedMedia): String? = withContext(Dispatchers.IO) {
        val baseItem = detailedMedia.baseItem
        val titleEn = baseItem.titleEn?.trim()?.ifBlank { null }
        val titlePl = baseItem.titlePl.trim().ifBlank { null }
        val primaryTitle = titleEn ?: titlePl ?: return@withContext null

        val year = detailedMedia.metaInfo?.year ?: baseItem.year
        val expectedDurationSec = detailedMedia.metaInfo?.duration?.inWholeSeconds

        val queries = buildList {
            val yearSuffix = if (year != null) " $year" else ""
            if (titleEn != null) {
                add("$titleEn$yearSuffix full movie")
            }
            if (titlePl != null && titlePl != titleEn) {
                add("$titlePl$yearSuffix cały film")
            }
            if (titleEn != null) {
                add("$titleEn$yearSuffix")
            }
        }.distinct()

        for (query in queries) {
            try {
                Log.d(TAG, "Searching YouTube with query: '$query'")
                val matchedUrl = executeSearch(
                    query = query,
                    titleEn = titleEn,
                    titlePl = titlePl,
                    year = year,
                    expectedDurationSec = expectedDurationSec,
                )
                if (matchedUrl != null) {
                    Log.d(TAG, "Found matching YouTube movie: $matchedUrl for query '$query'")
                    return@withContext matchedUrl
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to search YouTube for query: '$query'", e)
            }
        }

        null
    }

    private fun executeSearch(
        query: String,
        titleEn: String?,
        titlePl: String?,
        year: Int?,
        expectedDurationSec: Long?,
    ): String? {
        val service = ServiceList.YouTube
        val handler = service.searchQHFactory.fromQuery(
            query,
            listOf(YoutubeSearchQueryHandlerFactory.VIDEOS),
            "",
        )
        val searchInfo = SearchInfo.getInfo(service, handler)
        val streamItems = searchInfo.relatedItems.filterIsInstance<StreamInfoItem>()

        for (item in streamItems) {
            if (item.isShortFormContent) continue
            val durationSec = item.duration
            if (durationSec <= 0) continue

            // Duration check: if expected duration is known and > 30 minutes, reject trailers/clips
            if (expectedDurationSec != null && expectedDurationSec > 30 * 60) {
                if (durationSec < MIN_MOVIE_DURATION_SEC) continue
                val diff = abs(durationSec - expectedDurationSec)
                if (diff.toDouble() / expectedDurationSec > MAX_DURATION_DEVIATION_RATIO) continue
            } else if (durationSec < MIN_MOVIE_DURATION_SEC) {
                // If expected duration is unknown, at least reject anything under 15 minutes for full movie
                continue
            }

            val isMatch = isCandidateMatch(item.name, titleEn, year) ||
                isCandidateMatch(item.name, titlePl, year)

            if (isMatch) {
                return item.url
            }
        }

        return null
    }

    private fun isCandidateMatch(
        candidateTitle: String,
        expectedTitle: String?,
        expectedYear: Int?,
    ): Boolean {
        if (expectedTitle.isNullOrBlank()) return false

        if (EXCLUDED_TITLE_KEYWORDS.any { candidateTitle.contains(it, ignoreCase = true) }) {
            return false
        }

        // If both years are present, verify they don't diverge by more than 1 year
        val candYear = VideoSignatureExtractor.extractYear(candidateTitle)
        if (expectedYear != null && candYear != null && abs(expectedYear - candYear) > 1) {
            return false
        }

        val candTokens = VideoSignatureExtractor.tokenize(candidateTitle)
        val targetTokens = VideoSignatureExtractor.tokenize(expectedTitle)
        if (targetTokens.isNotEmpty() && targetTokens.all { it in candTokens }) {
            return true
        }

        val confidence = VideoSignatureExtractor.computeConfidence(candidateTitle, expectedTitle, expectedYear)
        if (confidence >= 10) {
            return true
        }

        val similarity = VideoSignatureExtractor.jaccardSimilarity(candTokens, targetTokens)
        return similarity >= 0.30
    }
}
