package com.example.filman.data.scraper

import com.example.filman.data.local.SessionManager
import com.example.filman.data.model.ActorInfo
import com.example.filman.data.model.ActorRole
import com.example.filman.data.model.CategoryInfo
import com.example.filman.data.model.DetailedMedia
import com.example.filman.data.model.EmbedLink
import com.example.filman.data.model.EpisodeLink
import com.example.filman.data.model.FilterData
import com.example.filman.data.model.FilterOption
import com.example.filman.data.model.MediaMetadata
import com.example.filman.data.model.MovieItem
import com.example.filman.data.model.Season
import com.example.filman.data.model.SimilarMovie
import com.example.filman.data.model.TagInfo
import com.example.filman.data.model.TvShow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

class AuthException(message: String) : Exception(message)

class FilmanScraper(private val sessionManager: SessionManager) {

    private val baseUrl = "https://filman.cc"

    private fun extractNumber(text: String): Int {
        val match = Regex("\\d+").find(text)
        return match?.value?.toIntOrNull() ?: 0
    }

    private fun parseTitleAndYear(rawTitle: String): Pair<String, String?> {
        val yearRegex = Regex("\\((\\d{4})\\)")
        val cleanedTitle = rawTitle.replace(yearRegex, "").trim()
        val parts = cleanedTitle.split(Regex("\\s*/\\s*"))
        val titlePl = parts[0].trim()
        val titleEn = parts.getOrNull(1)?.trim()
        return titlePl to titleEn
    }

    private fun extractEpisodeNumber(text: String): Int {
        val exMatch = Regex("(?i)e\\s*(\\d+)").find(text)
        if (exMatch != null) return exMatch.groupValues[1].toIntOrNull() ?: 0

        val odcMatch = Regex("(?i)odcinek\\s*(\\d+)").find(text)
        if (odcMatch != null) return odcMatch.groupValues[1].toIntOrNull() ?: 0

        val xMatch = Regex("(?i)\\d+x(\\d+)").find(text)
        if (xMatch != null) return xMatch.groupValues[1].toIntOrNull() ?: 0

        val numbers = Regex("\\d+").findAll(text).mapNotNull { it.value.toIntOrNull() }.toList()
        return numbers.lastOrNull { it < 1000 } ?: numbers.firstOrNull() ?: 0
    }

    private fun getDocument(path: String, passCookies: Boolean = false): Document {
        val cleanPath = path.trim().replace("\n", "").replace("\r", "")
        val url = if (cleanPath.startsWith("http")) {
            cleanPath
        } else {
            val separator = if (cleanPath.startsWith("/")) "" else "/"
            "$baseUrl$separator$cleanPath"
        }
        val cookie = sessionManager.getCookie()
        val userAgent = sessionManager.getUserAgent()

        var conn = Jsoup.connect(url)
            .userAgent(userAgent)
            .ignoreHttpErrors(true)
            .followRedirects(true)

        if (passCookies && !cookie.isNullOrBlank()) {
            conn.header("Cookie", cookie)
        }

        var doc = conn.get()
        var currentUrl = conn.response().url().toString()

        if (!passCookies && currentUrl.contains("/logowanie") && !cookie.isNullOrBlank()) {
            conn = Jsoup.connect(url)
                .userAgent(userAgent)
                .ignoreHttpErrors(true)
                .followRedirects(true)
                .header("Cookie", cookie)

            doc = conn.get()
            currentUrl = conn.response().url().toString()
        }

        if (currentUrl.contains("/logowanie")) {
            throw AuthException("Cookie expired or invalid. Redirected to /logowanie.")
        }

        if (currentUrl.endsWith("/404")) {
            throw Exception("Page not found (404)")
        }

        return doc
    }

    suspend fun getHomeMovies(): List<MovieItem> = withContext(Dispatchers.IO) {
        val movies = mutableListOf<MovieItem>()
        try {
            val doc = getDocument("/")
            val elements = doc.select(".movie-item")
            for (element in elements) {
                val aTag = element.selectFirst("a") ?: continue
                val url = aTag.attr("href")

                // Try webp source first, then img
                val webpSource = element.selectFirst("source[type=image/webp]")
                val imgTag = element.selectFirst("img")

                val posterUrl = webpSource?.attr("data-src") ?: imgTag?.attr("data-src") ?: ""
                val rawTitle = imgTag?.attr("alt") ?: aTag.attr("data-title")
                val (titlePl, titleEn, year) = parseTitleAndYear(rawTitle)
                val rating =
                    element.selectFirst(".rate")?.text()?.replace(",", ".")?.toFloatOrNull()

                movies.add(
                    MovieItem(
                        url = url,
                        titlePl = titlePl,
                        titleEn = titleEn,

                        filmanRating = rating,
                        posterUrl = posterUrl,
                    ),
                )
            }
        } catch (e: Exception) {
            if (e is AuthException) throw e
            e.printStackTrace()
        }
        return@withContext movies
    }

    suspend fun getFilters(path: String): FilterData = withContext(Dispatchers.IO) {
        try {
            val doc = getDocument(path)

            fun parseList(id: String): List<FilterOption> {
                val listElement = doc.selectFirst("#$id") ?: return emptyList()
                val items = mutableListOf<FilterOption>()
                for (li in listElement.select("li")) {
                    val dataId = li.attr("data-id").takeIf { it.isNotEmpty() }
                        ?: li.attr("data-sort").takeIf { it.isNotEmpty() }
                        ?: li.selectFirst("a")?.attr("data-id").takeIf { !it.isNullOrEmpty() }
                        ?: li.selectFirst("a")?.attr("data-sort").takeIf { !it.isNullOrEmpty() }
                        ?: li.selectFirst("span")?.attr("data-id").takeIf { !it.isNullOrEmpty() }
                        ?: li.selectFirst("span")?.attr("data-sort").takeIf { !it.isNullOrEmpty() }

                    val label = li.text().trim()

                    if (!dataId.isNullOrEmpty() && label.isNotEmpty()) {
                        items.add(FilterOption(id = dataId, label = label))
                    }
                }

                if (items.isEmpty()) {
                    return listOf(
                        FilterOption(
                            "error",
                            "Empty items. li count: ${listElement.select("li").size}",
                        ),
                    )
                }

                return items
            }

            FilterData(
                sortingOptions = parseList("filter-sort"),
                qualityOptions = parseList("filter-quality"),
                versionOptions = parseList("filter-version"),
                categoryOptions = parseList("filter-category"),
                yearOptions = parseList("filter-year"),
            )
        } catch (e: Exception) {
            if (e is AuthException) throw e
            e.printStackTrace()
            FilterData(
                sortingOptions = listOf(FilterOption("error", e.message ?: "Exception occurred")),
                qualityOptions = listOf(FilterOption("error", e.message ?: "Exception occurred")),
                versionOptions = listOf(FilterOption("error", e.message ?: "Exception occurred")),
                categoryOptions = listOf(FilterOption("error", e.message ?: "Exception occurred")),
                yearOptions = listOf(FilterOption("error", e.message ?: "Exception occurred")),
            )
        }
    }

    suspend fun getFeaturedItems(path: String = "/"): List<MovieItem> =
        withContext(Dispatchers.IO) {
            val items = mutableListOf<MovieItem>()
            try {
                val doc = getDocument(path)
                val sliderItems = doc.select("#slider .slide")
                val posterItems = doc.select("#slider #posters > a")
                (sliderItems.zip(posterItems)).forEach { (slider, poster) ->
                    val aTag = slider.parent()
                    if (aTag?.tagName() != "a") return@forEach
                    val url = aTag.attr("href")

                    val titleElement = slider.selectFirst(".title")
                    val rawTitle = titleElement?.ownText()?.trim().orEmpty()
                    val (titlePl, titleEn, yearFromTitle) = parseTitleAndYear(rawTitle)

                    val descElement = slider.selectFirst(".description")
                    val descHtml = descElement?.html().orEmpty()
                    val descParts = descHtml.split("<br>").map { it.trim() }
                    val description =
                        descParts.lastOrNull { it.isNotEmpty() }?.replace(Regex("<.*?>"), "")
                            ?.trim().orEmpty()

                    // Try finding rating in description or .rate element
                    val ratingRegex = Regex("([0-9].[0-9]{2})")
                    val ratingMatch = ratingRegex.find(descHtml.replace(Regex("<.*?>"), ""))
                    val ratingFromDesc =
                        ratingMatch?.groupValues?.get(1)?.replace(",", ".")?.toFloatOrNull()
                    val ratingFromRateClass =
                        slider.selectFirst(".rate")?.text()?.replace(",", ".")?.toFloatOrNull()
                    val rating = ratingFromDesc ?: ratingFromRateClass

                    val webpSource = slider.selectFirst("source[type=image/webp]")
                    val imgTag = slider.selectFirst("img")
                    val imageUrl = webpSource?.attr("data-src")?.takeIf { it.isNotEmpty() }
                        ?: webpSource?.attr("srcset")?.takeIf { it.isNotEmpty() }
                        ?: imgTag?.attr("data-src")?.takeIf { it.isNotEmpty() }
                        ?: imgTag?.attr("src").orEmpty()

                    val posterImage = poster.selectFirst("img")?.attr("src").orEmpty()

                    if (url.isNotEmpty() && rawTitle.isNotEmpty()) {
                        items.add(
                            MovieItem(
                                url = url,
                                titlePl = titlePl,
                                titleEn = titleEn,

                                filmanRating = rating,
                                description = description,
                                posterUrl = posterImage,
                                backgroundUrl = imageUrl,
                            ),
                        )
                    }
                }
            } catch (e: Exception) {
                if (e is AuthException) throw e
                e.printStackTrace()
            }
            return@withContext items
        }

    suspend fun getCategoryMovies(
        path: String,
        page: Int = 1,
//        filterState: FilterState? = null,
    ): List<MovieItem> =
        withContext(Dispatchers.IO) {
            val movies = mutableListOf<MovieItem>()
            try {
                var fullPath = path.trimEnd('/')
                var hasFilters = false

//                if (filterState != null) {
//                    if (filterState.versions.isNotEmpty()) {
//                        fullPath += "/version:${filterState.versions.joinToString(",")}"
//                        hasFilters = true
//                    }
//                    if (filterState.categories.isNotEmpty()) {
//                        fullPath += "/category:${filterState.categories.joinToString(",")}"
//                        hasFilters = true
//                    }
//                    if (filterState.qualities.isNotEmpty()) {
//                        fullPath += "/quality:${filterState.qualities.joinToString(",")}"
//                        hasFilters = true
//                    }
//                    if (filterState.sort != null) {
//                        fullPath += "/${filterState.sort}"
//                        hasFilters = true
//                    }
//                    if (filterState.years.isNotEmpty()) {
//                        fullPath += "/year:${filterState.years.joinToString(",")}"
//                        hasFilters = true
//                    }
//                }

                val urlPath = if (hasFilters) "$fullPath?page=$page" else "$fullPath/?page=$page"
                val doc = getDocument(urlPath)
                val parsedUrls = mutableSetOf<String>()

                // First try .movie-item
                val movieItems = doc.select(".movie-item")
                movieItems.forEach { element ->
                    val aTag = element.selectFirst("a") ?: return@forEach
                    val url = aTag.attr("href")
                    if (!parsedUrls.add(url)) return@forEach

                    val webpSource = element.selectFirst("source[type=image/webp]")
                    val imgTag = element.selectFirst("img")

                    val posterUrl = webpSource?.attr("data-src") ?: imgTag?.attr("data-src")
                    ?: imgTag?.attr("src") ?: ""
                    val rawTitle = imgTag?.attr("alt") ?: aTag.attr("data-title")
                    val (titlePl, titleEn, year) = parseTitleAndYear(rawTitle)
                    val rating =
                        element.selectFirst(".rate")?.text()?.replace(",", ".")?.toFloatOrNull()

                    movies.add(
                        MovieItem(
                            url = url,
                            titlePl = titlePl,
                            titleEn = titleEn,

                            filmanRating = rating,
                            posterUrl = posterUrl,
                        ),
                    )
                }

                // Then try .poster
                val posterItems = doc.select(".poster")
                posterItems.forEach { element ->
                    val aTag = element.selectFirst("a") ?: return@forEach
                    val url = aTag.attr("href")
                    if (!parsedUrls.add(url)) return@forEach

                    val imgTag = aTag.selectFirst("img")
                    val posterUrl = imgTag?.attr("data-src")?.takeIf {
                        it.isNotEmpty()
                    } ?: imgTag?.attr("src").orEmpty()

                    val filmTitleDiv = element.parent()?.selectFirst(".film_title")
                    val rawTitle =
                        filmTitleDiv?.text() ?: imgTag?.attr("alt") ?: aTag.attr("data-title")
                    val (titlePl, titleEn) = parseTitleAndYear(rawTitle)
                    val rating = element.parent()?.selectFirst(".rate")?.text()?.replace(",", ".")
                        ?.toFloatOrNull()

                    if (url.isNotEmpty() && rawTitle.isNotEmpty()) {
                        movies.add(
                            MovieItem(
                                url = url,
                                titlePl = titlePl,
                                titleEn = titleEn,

                                filmanRating = rating,
                                posterUrl = posterUrl,
                            ),
                        )
                    }
                }
            } catch (e: Exception) {
                if (e is AuthException) throw e
                e.printStackTrace()
            }
            return@withContext movies
        }

    suspend fun searchMovies(query: String): List<MovieItem> = withContext(Dispatchers.IO) {
        val movies = mutableListOf<MovieItem>()
        try {
            val doc = getDocument("/search?phrase=${query.replace(" ", "+")}", true)
            val elements = doc.select(".poster")
            for (element in elements) {
                val aTag = element.selectFirst("a") ?: continue
                val url = aTag.attr("href")

                val imgTag = aTag.selectFirst("img")
                val posterUrl = imgTag?.attr("src") ?: ""

                val filmTitleDiv = element.parent()?.selectFirst(".film_title")
                val rawTitle =
                    filmTitleDiv?.text() ?: imgTag?.attr("alt") ?: aTag.attr("data-title")
                val (titlePl, titleEn, year) = parseTitleAndYear(rawTitle)
                val rating = element.parent()?.selectFirst(".rate")?.text()?.replace(",", ".")
                    ?.toFloatOrNull()

                if (url.isNotEmpty() && rawTitle.isNotEmpty()) {
                    movies.add(
                        MovieItem(
                            url = url,
                            titlePl = titlePl,
                            titleEn = titleEn,

                            filmanRating = rating,
                            posterUrl = posterUrl,
                        ),
                    )
                }
            }
        } catch (e: Exception) {
            if (e is AuthException) throw e
            e.printStackTrace()
        }
        movies
    }

    suspend fun getMediaDetails(mediaUrl: String): DetailedMedia? =
        withContext(Dispatchers.IO) {
            try {
                val doc = getDocument(mediaUrl)
                val titleMeta = doc.selectFirst("meta[property=\"og:title\"]")
                val rawTitle = titleMeta?.attr("content") ?: doc.selectFirst("title")?.text()
                    ?.substringBefore(" - ") ?: "Unknown Title"
                val (titlePl, titleEn, year) = parseTitleAndYear(rawTitle)

                val posterMeta = doc.selectFirst("meta[property=\"og:image\"]")
                val posterUrl = posterMeta?.attr("content") ?: ""

                val descMeta = doc.selectFirst("meta[property=\"og:description\"]")
                val description =
                    descMeta?.attr("content") ?: doc.selectFirst("meta[name=\"description\"]")
                        ?.attr("content") ?: "No description available."

                val scoreRows = doc.select(".vote-score-row")
                var filmanRating: Float? = null
                var imdbRating: Float? = null

                if (scoreRows.isNotEmpty()) {
                    filmanRating = scoreRows[0].selectFirst(".vote-num")?.text()?.replace(",", ".")
                        ?.toFloatOrNull()
                }
                if (scoreRows.size > 1) {
                    imdbRating = scoreRows[1].selectFirst(".vote-num")?.text()?.replace(",", ".")
                        ?.toFloatOrNull()
                }

                var metaYear = year
                var metaViews: Int? = null
                var metaDuration: String? = null
                var metaCountry: String? = null

                val metaRow = doc.selectFirst(".flm-meta-row")
                if (metaRow != null) {
                    val metaItems = metaRow.children()
                    if (metaItems.isNotEmpty()) {
                        metaYear =
                            metaItems[0].text().replace(Regex("[^0-9]"), "").toIntOrNull() ?: year
                    }
                    if (metaItems.size > 1) {
                        metaViews =
                            metaItems[1].text().replace(Regex("[^0-9]"), "").toIntOrNull()
                    }
                    if (metaItems.size > 2) {
                        metaDuration =
                            metaItems[2].text()
                                .replace(Regex("[^a-zA-Z0-9 :ąćęłńóśźżĄĆĘŁŃÓŚŹŻ]"), "")
                                .trim()
                    }
                    if (metaItems.size > 3) {
                        metaCountry =
                            metaItems[3].text()
                                .replace(Regex("[^a-zA-Z0-9 ,.-ąćęłńóśźżĄĆĘŁŃÓŚŹŻ]"), "")
                                .trim()
                    }
                }
                val mediaMetadata = MediaMetadata(
                    year = metaYear,
                    views = metaViews,
                    duration = metaDuration,
                    country = metaCountry,
                )

                val categories = doc.select(".flm-genre-tag").mapNotNull {
                    val catUrl = it.attr("href")
                    val catName = it.text().trim()
                    if (catName.isNotEmpty()) {
                        CategoryInfo(catName, catUrl)
                    } else {
                        null
                    }
                }

                val tags = doc.select(".flm-tag-list").firstOrNull()?.children()?.mapNotNull {
                    val aTag = if (it.tagName() == "a") it else it.selectFirst("a")
                    val tagName = aTag?.text()?.trim() ?: it.text().trim()
                    val tagUrl = aTag?.attr("href") ?: ""
                    if (tagName.isNotEmpty()) {
                        TagInfo(tagName, tagUrl)
                    } else {
                        null
                    }
                } ?: emptyList()

                val actors = mutableListOf<ActorInfo>()
                val crewGroups = doc.select(".flm-crew-group")
                for (group in crewGroups) {
                    val roleText =
                        group.selectFirst("h1, h2, h3, h4, h5, .role-title, .title, .flm-crew-label")
                            ?.text()?.lowercase() ?: ""
                    val role = when {
                        roleText.contains("reżys") || roleText.contains("director") -> ActorRole.DIRECTOR
                        roleText.contains("scenar") || roleText.contains("writer") -> ActorRole.WRITER
                        roleText.contains("obsada") || roleText.contains("aktor") || roleText.contains(
                            "actor",
                        ) || roleText.contains("występ") -> ActorRole.ACTOR

                        else -> ActorRole.UNKNOWN
                    }
                    val items = group.select("li, .crew-item, .person-item, a, .flm-person-card")
                        .filter { it.select("img").isNotEmpty() || it.text().isNotBlank() }
                    for (item in items) {
                        if (item.tagName() == "a" && item.parent()?.tagName() == "li") continue

                        val aTag = if (item.tagName() == "a") item else item.selectFirst("a")
                        val personUrl = aTag?.attr("href")
                        val img = item.selectFirst("img")
                        val avatarUrl =
                            img?.attr("data-src")?.takeIf { it.isNotBlank() } ?: img?.attr("src")
                        val personName =
                            img?.attr("alt")?.takeIf { it.isNotBlank() } ?: aTag?.text()?.trim()
                                ?.takeIf { it.isNotBlank() } ?: item.text().trim()

                        if (personName.isNotBlank() && !personName.lowercase()
                                .contains(roleText) && !roleText.contains(personName.lowercase())
                        ) {
                            actors.add(ActorInfo(role, personName, avatarUrl, personUrl))
                        }
                    }
                }

                val similarMovies = mutableListOf<SimilarMovie>()
                val similarList = doc.selectFirst("#item-list")
                if (similarList != null) {
                    val items = similarList.select("a").filter {
                        it.select("img").isNotEmpty() || it.attr("data-title").isNotBlank()
                    }
                    for (item in items) {
                        val simUrl = item.attr("href")
                        if (simUrl.isBlank()) continue
                        val img = item.selectFirst("img")
                        val simPoster =
                            img?.attr("data-src")?.takeIf { it.isNotBlank() } ?: img?.attr("src")
                            ?: ""
                        val simName =
                            item.attr("data-title").takeIf { it.isNotBlank() } ?: img?.attr("alt")
                                ?.takeIf { it.isNotBlank() } ?: item.text().trim()
                        if (simName.isNotBlank()) {
                            similarMovies.add(SimilarMovie(simUrl, simName, simPoster))
                        }
                    }
                }

                // Check if it's a series (has episode-list)
                val episodeList = doc.selectFirst("#episode-list, .episode-list")
                if (episodeList != null) {
                    val seasons = mutableListOf<Season>()
                    val seasonNodes = episodeList.children().filter { it.tagName() == "li" }

                    for (seasonNode in seasonNodes) {
                        val seasonName = seasonNode.selectFirst("span")?.text() ?: "Unknown Season"
                        val episodesList = seasonNode.select("ul li a")
                        val episodes = episodesList.map { aTag ->
                            EpisodeLink(
                                url = aTag.attr("href"),
                                title = aTag.text().trim(),
                            )
                        }.sortedBy { extractEpisodeNumber(it.title) }
                        if (episodes.isNotEmpty()) {
                            seasons.add(Season(seasonName, episodes))
                        }
                    }
                    seasons.sortBy { extractNumber(it.name) }
                    return@withContext DetailedMedia(
                        baseItem = TvShow(
                            url = mediaUrl,
                            titlePl = titlePl,
                            titleEn = titleEn,
                            filmanRating = filmanRating,
                            imdbRating = imdbRating,
                            posterUrl = posterUrl,
                            backgroundUrl = null,
                            description = description,
                            seasons = seasons,
                        ),
                        metaInfo = mediaMetadata,
                        categories = categories,
                        tags = tags,
                        actors = actors,
                        similarMovies = similarMovies,
                    )
                } else {
                    // It's a Movie or an Episode, parse embeds
                    val links = mutableListOf<EmbedLink>()
                    var routeToken = ""

                    val scripts = doc.select("script")
                    for (script in scripts) {
                        val scriptData = script.data()
                        if (scriptData.contains("var routeToken")) {
                            val match = Regex("var routeToken = '([a-zA-Z0-9]+)'").find(scriptData)
                            if (match != null) {
                                routeToken = match.groupValues[1]
                                break
                            }
                        }
                    }

                    val elements = doc.select("a[data-link-id]")
                    for (element in elements) {
                        val id = element.attr("data-link-id")
                        var serverName =
                            element.selectFirst("img")?.attr("alt") ?: element.text().trim()
                        var version = ""
                        var quality = ""

                        val tr = element.closest("tr")
                        if (tr != null) {
                            val tds = tr.select("td")
                            for (td in tds) {
                                val text = td.text().trim()
                                if (text.matches(Regex("(?i).*\\b\\d{3,4}p\\b.*"))) {
                                    quality = text
                                } else if (text.matches(Regex("(?i).*(lektor|napisy|dubbing|pl|eng).*"))) {
                                    version = text
                                }
                            }
                            if (serverName.isEmpty() || serverName.equals(
                                    "Oglądaj",
                                    ignoreCase = true,
                                )
                            ) {
                                val img = tr.selectFirst("img")
                                serverName = img?.attr("alt") ?: serverName
                            }
                        }

                        if (serverName.isEmpty()) serverName = "Unknown"

                        links.add(EmbedLink(id, serverName, version, quality))
                    }

                    var seriesUrl: String? = null
                    val breadcrumbLinks =
                        doc.select("ul.breadcrumb li a, ol.breadcrumb li a, .breadcrumbs a, .path a, .brd li a, ul.b-crumbs li a")
                    val seriesLink = breadcrumbLinks.find {
                        it.attr("href").contains("/serial-online/") && !it.attr("href")
                            .contains(mediaUrl)
                    }
                    if (seriesLink != null) {
                        seriesUrl = seriesLink.attr("href")
                    }
                    if (seriesUrl == null && mediaUrl.contains("/serial-online/")) {
                        val parts = mediaUrl.split("/")
                        val lastPart = parts.lastOrNull { it.isNotBlank() }
                        if (
                            lastPart != null &&
                            lastPart.matches(Regex("s\\d+e\\d+", RegexOption.IGNORE_CASE))
                        ) {
                            seriesUrl = parts.dropLast(1).joinToString("/")
                        }
                    }

                    var prevEpisodeUrl: String? = null
                    var nextEpisodeUrl: String? = null
                    val navLinks = doc.select("#single-info div a")
                    for (link in navLinks) {
                        val text = link.text().lowercase()
                        val href = link.attr("href")
                        if (text.contains("poprzedni") || link.hasClass("pull-left")) {
                            prevEpisodeUrl = href
                        } else if (text.contains("następny") || text.contains("nastepny") || link.hasClass(
                                "pull-right",
                            )
                        ) {
                            nextEpisodeUrl = href
                        }
                    }

                    return@withContext DetailedMedia(
                        baseItem = MovieItem(
                            url = mediaUrl,
                            titlePl = titlePl,
                            titleEn = titleEn,
                            filmanRating = filmanRating,
                            imdbRating = imdbRating,
                            posterUrl = posterUrl,
                            backgroundUrl = null,
                            description = description,
                            routeToken = routeToken,
                            embeds = links,
                            seriesUrl = seriesUrl,
                            prevEpisodeUrl = prevEpisodeUrl,
                            nextEpisodeUrl = nextEpisodeUrl,
                        ),
                        metaInfo = mediaMetadata,
                        categories = categories,
                        tags = tags,
                        actors = actors,
                        similarMovies = similarMovies,
                    )
                }
            } catch (e: Exception) {
                if (e is AuthException) throw e
            }

            null
        }
}
