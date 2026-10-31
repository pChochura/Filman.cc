package com.pointlessapps.filman.data.scraper

import com.pointlessapps.filman.data.model.EpisodeLink
import com.pointlessapps.filman.data.model.Season

object EpisodeUtils {

    // Matches standard SxxEyy formats: [s01e02], (s1e2), s01e02, S01E02, s01.e02, s01_e02, s01-e02
    private val seasonEpisodeRegex =
        Regex("""(?i)(?:\[|\(|\b)s\d+\s*[-._/]?\s*e(\d+)(?:\]|\)|\b)""")

    // Matches NxNN formats: 1x02, 01x02, [1x02]
    private val xEpisodeRegex =
        Regex("""(?i)(?:\[|\(|\b)\d+x(\d+)(?:\]|\)|\b)""")

    // Matches keywords like: Odcinek 2, odc. 2, episode 2, ep. 2, odc 02, episode[2], odcinek-2, odc-2, /odcinek/2
    private val keywordEpisodeRegex =
        Regex("""(?i)(?:\[|\(|\b)(?:odcinek|odc|episode|ep)\s*[.:\-–—/_]?\s*\[?(\d+)\]?(?:\]|\)|\b)""")

    // Matches lone "E" notation: E02, E2, [E02]
    private val loneERegex =
        Regex("""(?i)(?:\[|\(|\b)e\s*(\d+)(?:\]|\)|\b)""")

    // Matches leading episode number: "2. Odcinek", "02 - Title", "2) Title"
    private val leadingNumberRegex =
        Regex("""^\s*(\d+)\s*(?:[.:)\-–—]|\s+)""")

    // Matches season number: Sezon 1, Season 02, s1, [s01]
    private val seasonNumberRegex =
        Regex("""(?i)(?:sezon|season|\bs)\s*[.:\-–—/_]?\s*\[?(\d+)\]?""")

    // General number regex
    private val numberRegex = Regex("""\d+""")

    // Regex to remove bracketed/prefixed season & episode markers from title:
    // e.g., "[s01e02]", "[s1e2] - ", "(s01e02) : ", "s01e02 - ", "[1x02]", "1x02 - "
    private val prefixTagRegex =
        Regex("""(?i)^\s*(?:\[|\()?s\d+\s*[-._/]?\s*e\d+(?:\]|\))?\s*[-:–—.]?\s*""")
    private val prefixXTagRegex =
        Regex("""(?i)^\s*(?:\[|\()?\d+x\d+(?:\]|\))?\s*[-:–—.]?\s*""")

    // Regex to remove trailing season & episode markers from title:
    // e.g. "Title [s01e02]", "Title (s01e02)"
    private val suffixTagRegex =
        Regex("""(?i)\s*(?:\[|\()\s*s\d+\s*[-._/]?\s*e\d+\s*(?:\]|\))\s*$""")
    private val suffixXTagRegex =
        Regex("""(?i)\s*(?:\[|\()\s*\d+x\d+\s*(?:\]|\))\s*$""")

    /**
     * Extracts the episode number from title and/or URL.
     * Returns the integer episode number if found (> 0), or 0 if unresolvable.
     */
    fun extractEpisodeNumber(title: String, url: String = ""): Int {
        // 1. Try patterns on title first
        findEpisodeNumberInString(title)?.let { return it }

        // 2. Try patterns on URL if title didn't yield a valid number
        if (url.isNotEmpty()) {
            findEpisodeNumberInString(url)?.let { return it }

            // URL path segments like /odcinki/2.html or /episode-2/ or /2/ (prefer last segment)
            val urlSegments = Regex("""(?i)[/-](\d+)(?=\.html|/|$)""").findAll(url).toList()
            val urlNum = urlSegments.lastOrNull()?.groupValues?.get(1)?.toIntOrNull()
            if (urlNum != null && urlNum in 1..999) return urlNum
        }

        // 3. Fallback: single number in title < 1000
        val numbers = numberRegex.findAll(title).mapNotNull { it.value.toIntOrNull() }.toList()
        return numbers.lastOrNull { it in 1..999 } ?: numbers.firstOrNull { it in 1..999 } ?: 0
    }

    private fun findEpisodeNumberInString(text: String): Int? {
        if (text.isBlank()) return null

        seasonEpisodeRegex.find(text)?.let {
            val num = it.groupValues[1].toIntOrNull()
            if (num != null && num > 0) return num
        }

        xEpisodeRegex.find(text)?.let {
            val num = it.groupValues[1].toIntOrNull()
            if (num != null && num > 0) return num
        }

        keywordEpisodeRegex.find(text)?.let {
            val num = it.groupValues[1].toIntOrNull()
            if (num != null && num > 0) return num
        }

        loneERegex.find(text)?.let {
            val num = it.groupValues[1].toIntOrNull()
            if (num != null && num > 0) return num
        }

        leadingNumberRegex.find(text)?.let {
            val num = it.groupValues[1].toIntOrNull()
            if (num != null && num > 0) return num
        }

        return null
    }

    /**
     * Extracts the season number from season name and/or URL.
     */
    fun extractSeasonNumber(seasonName: String, url: String = ""): Int {
        if (seasonName.contains(Regex("""(?i)specjal|special"""))) return 0

        seasonNumberRegex.find(seasonName)?.let {
            val num = it.groupValues[1].toIntOrNull()
            if (num != null && num >= 0) return num
        }

        if (url.isNotEmpty()) {
            seasonNumberRegex.find(url)?.let {
                val num = it.groupValues[1].toIntOrNull()
                if (num != null && num >= 0) return num
            }
        }

        return numberRegex.find(seasonName)?.value?.toIntOrNull() ?: 0
    }

    /**
     * Cleans an episode title by stripping redundant tags like `[s01e02]`, `(s01e02)`, `1x02`,
     * normalizing whitespace and formatting.
     * E.g.:
     *   "[s01e02] Odcinek 2" -> "Odcinek 2"
     *   "[s01e02] Nadchodzi zima" -> "Nadchodzi zima"
     *   "[s01e02]" -> "Odcinek 2" (if episodeNumber == 2)
     */
    fun cleanEpisodeTitle(title: String, episodeNumber: Int = 0): String {
        var cleaned = title.trim()

        // Strip prefix tags like [s01e02] or 1x02 -
        cleaned = cleaned.replace(prefixTagRegex, "").trim()
        cleaned = cleaned.replace(prefixXTagRegex, "").trim()

        // Strip suffix tags like [s01e02]
        cleaned = cleaned.replace(suffixTagRegex, "").trim()
        cleaned = cleaned.replace(suffixXTagRegex, "").trim()

        // Strip leading dashes or colons left over
        cleaned = cleaned.removePrefix("-").removePrefix(":").trim()

        // If title became empty or was just an episode tag
        if (cleaned.isEmpty()) {
            return if (episodeNumber > 0) "Odcinek $episodeNumber" else title.trim()
        }

        // Normalize "Odcinek 02" -> "Odcinek 2"
        val genericOdcMatch = Regex("""(?i)^\s*odcinek\s*0+(\d+)\s*$""").find(cleaned)
        if (genericOdcMatch != null) {
            val num = genericOdcMatch.groupValues[1]
            return "Odcinek $num"
        }

        // Normalize "odc. 2" or "odc 2" -> "Odcinek 2"
        val odcShortMatch = Regex("""(?i)^\s*odc\.?\s*(\d+)\s*$""").find(cleaned)
        if (odcShortMatch != null) {
            val num = odcShortMatch.groupValues[1].toIntOrNull() ?: odcShortMatch.groupValues[1]
            return "Odcinek $num"
        }

        return cleaned
    }

    /**
     * Scores title quality to decide which title to keep when merging duplicates.
     * Higher score = better title.
     * Descriptive named titles score higher than generic "Odcinek X".
     */
    private fun getTitleScore(title: String): Int {
        if (title.isBlank()) return 0
        var score = 0
        if (title.contains(Regex("""(?i)\[s\d+e\d+\]"""))) score -= 10
        val isGeneric =
            title.matches(Regex("""(?i)^\s*(?:odcinek|odc|episode|ep)\s*\.?\s*\d+\s*$"""))
        if (!isGeneric) {
            score += 50
        }
        score += title.length.coerceAtMost(50)
        return score
    }

    /**
     * Deduplicates a list of episode links within a season.
     * - Identifies episodes by episode number (or fallback normalized key).
     * - Cleans titles to eliminate tags like `[s01e02]`.
     * - Retains the best title and the earliest non-blank URL.
     * - Sorts the result cleanly by episode number.
     */
    fun deduplicateEpisodes(episodes: List<EpisodeLink>): List<EpisodeLink> {
        if (episodes.isEmpty()) return emptyList()

        data class EpisodeCandidate(
            val original: EpisodeLink,
            val episodeNumber: Int,
            val cleanedTitle: String,
            val groupKey: String,
        )

        val candidates =
            episodes.map { ep ->
                val epNum = extractEpisodeNumber(ep.title, ep.url)
                val cleanedTitle = cleanEpisodeTitle(ep.title, epNum)
                val key =
                    if (epNum > 0) {
                        "num_$epNum"
                    } else {
                        "title_${cleanedTitle.lowercase().replace(Regex("""[^a-z0-9]"""), "")}"
                    }
                EpisodeCandidate(ep, epNum, cleanedTitle, key)
            }

        val grouped = candidates.groupBy { it.groupKey }

        val merged =
            grouped.values.map { group ->
                // Best title: highest score among cleaned titles
                val bestCandidate =
                    group.maxByOrNull { getTitleScore(it.cleanedTitle) } ?: group.first()
                val bestTitle = bestCandidate.cleanedTitle

                // Best URL: first non-blank URL in group
                val bestUrl =
                    group.firstOrNull { it.original.url.isNotBlank() }?.original?.url.orEmpty()

                val epNum = group.firstOrNull { it.episodeNumber > 0 }?.episodeNumber ?: 0

                epNum to EpisodeLink(
                    title = bestTitle,
                    url = bestUrl,
                )
            }

        return merged
            .sortedWith(
                compareBy<Pair<Int, EpisodeLink>> { (epNum, _) ->
                    if (epNum > 0) epNum else Int.MAX_VALUE
                }.thenBy { (_, link) -> link.title },
            ).map { it.second }
    }

    /**
     * Merges seasons from two sources (or a single source), deduplicating
     * seasons by season number and episodes within each season.
     */
    fun mergeSeasons(
        currentSeasons: List<Season>?,
        newSeasons: List<Season>?,
    ): List<Season>? {
        if (currentSeasons == null && newSeasons == null) return null

        val allSeasons = currentSeasons.orEmpty() + newSeasons.orEmpty()
        if (allSeasons.isEmpty()) return emptyList()

        val grouped = allSeasons.groupBy { extractSeasonNumber(it.name) }

        val mergedSeasons =
            grouped.map { (seasonNumber, seasonsList) ->
                val seasonName =
                    if (seasonNumber == 0) {
                        seasonsList.firstOrNull { it.name.isNotBlank() }?.name ?: "Odcinki specjalne"
                    } else {
                        seasonsList
                            .firstOrNull {
                                extractSeasonNumber(it.name) == seasonNumber &&
                                    !it.name.contains(Regex("""(?i)\[s\d+\]"""))
                            }?.name
                            ?: seasonsList.firstOrNull { it.name.contains(seasonNumber.toString()) }?.name
                            ?: "Sezon $seasonNumber"
                    }

                val allEpisodes = seasonsList.flatMap { it.episodes }
                val mergedEpisodes = deduplicateEpisodes(allEpisodes)

                seasonNumber to Season(seasonName, mergedEpisodes)
            }

        return mergedSeasons
            .sortedWith(
                compareBy<Pair<Int, Season>> { (seasonNum, _) ->
                    if (seasonNum == 0) Int.MAX_VALUE else seasonNum
                },
            ).map { it.second }
            .ifEmpty { null }
    }
}
