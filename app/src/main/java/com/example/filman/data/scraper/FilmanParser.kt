package com.example.filman.data.scraper

import com.example.filman.data.model.*
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

object FilmanParser {

    fun extractNumber(text: String): Int {
        val match = Regex("\\d+").find(text)
        return match?.value?.toIntOrNull() ?: 0
    }

    fun parseTitleAndYear(rawTitle: String): Triple<String, String?, Int?> {
        val yearRegex = Regex("\\((\\d{4})\\)")
        val yearMatch = yearRegex.find(rawTitle)
        val year = yearMatch?.groupValues?.get(1)?.toIntOrNull()
        val cleanedTitle = rawTitle.replace(yearRegex, "").trim()
        val parts = cleanedTitle.split(Regex("\\s*/\\s*"))
        val titlePl = parts[0].trim()
        val titleEn = parts.getOrNull(1)?.trim()
        return Triple(titlePl, titleEn, year)
    }

    fun extractEpisodeNumber(text: String): Int {
        val exMatch = Regex("(?i)e\\s*(\\d+)").find(text)
        if (exMatch != null) return exMatch.groupValues[1].toIntOrNull() ?: 0

        val odcMatch = Regex("(?i)odcinek\\s*(\\d+)").find(text)
        if (odcMatch != null) return odcMatch.groupValues[1].toIntOrNull() ?: 0

        val xMatch = Regex("(?i)\\d+x(\\d+)").find(text)
        if (xMatch != null) return xMatch.groupValues[1].toIntOrNull() ?: 0

        val numbers = Regex("\\d+").findAll(text).mapNotNull { it.value.toIntOrNull() }.toList()
        return numbers.lastOrNull { it < 1000 } ?: numbers.firstOrNull() ?: 0
    }

    fun parseHomeMovies(doc: Document): List<MovieItem> {
        val movies = mutableListOf<MovieItem>()
        val elements = doc.select(".movie-item")
        for (element in elements) {
            val aTag = element.selectFirst("a") ?: continue
            val url = aTag.attr("href")

            val webpSource = element.selectFirst("source[type=image/webp]")
            val imgTag = element.selectFirst("img")

            val posterUrl = webpSource?.attr("data-src") ?: imgTag?.attr("data-src") ?: ""
            val rawTitle = imgTag?.attr("alt") ?: aTag.attr("data-title")
            val (titlePl, titleEn, _) = parseTitleAndYear(rawTitle)
            val rating = element.selectFirst(".rate")?.text()?.replace(",", ".")?.toFloatOrNull()

            movies.add(
                MovieItem(
                    url = url,
                    titlePl = titlePl,
                    titleEn = titleEn,
                    filmanRating = rating,
                    posterUrl = posterUrl,
                )
            )
        }
        return movies
    }

    fun parseFilters(doc: Document): FilterData {
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
                return listOf(FilterOption("error", "Empty items. li count: ${listElement.select("li").size}"))
            }

            return items
        }

        return FilterData(
            sortingOptions = parseList("filter-sort"),
            qualityOptions = parseList("filter-quality"),
            versionOptions = parseList("filter-version"),
            categoryOptions = parseList("filter-category"),
            yearOptions = parseList("filter-year"),
        )
    }

    fun parseFeaturedItems(doc: Document): List<MovieItem> {
        val items = mutableListOf<MovieItem>()
        val sliderItems = doc.select("#slider .slide")
        val posterItems = doc.select("#slider #posters > a")
        (sliderItems.zip(posterItems)).forEach { (slider, poster) ->
            val aTag = slider.parent()
            if (aTag?.tagName() != "a") return@forEach
            val url = aTag.attr("href")

            val titleElement = slider.selectFirst(".title")
            val rawTitle = titleElement?.ownText()?.trim().orEmpty()
            val (titlePl, titleEn, _) = parseTitleAndYear(rawTitle)

            val descElement = slider.selectFirst(".description")
            val descHtml = descElement?.html().orEmpty()
            val descParts = descHtml.split("<br>").map { it.trim() }
            val description = descParts.lastOrNull { it.isNotEmpty() }?.replace(Regex("<.*?>"), "")?.trim().orEmpty()

            val ratingRegex = Regex("([0-9].[0-9]{2})")
            val ratingMatch = ratingRegex.find(descHtml.replace(Regex("<.*?>"), ""))
            val ratingFromDesc = ratingMatch?.groupValues?.get(1)?.replace(",", ".")?.toFloatOrNull()
            val ratingFromRateClass = slider.selectFirst(".rate")?.text()?.replace(",", ".")?.toFloatOrNull()
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
                    )
                )
            }
        }
        return items
    }

    fun parseCategoryMovies(doc: Document, parsedUrls: MutableSet<String>): List<MovieItem> {
        val movies = mutableListOf<MovieItem>()
        
        // .movie-item
        val movieItems = doc.select(".movie-item")
        movieItems.forEach { element ->
            val aTag = element.selectFirst("a") ?: return@forEach
            val url = aTag.attr("href")
            if (!parsedUrls.add(url)) return@forEach

            val webpSource = element.selectFirst("source[type=image/webp]")
            val imgTag = element.selectFirst("img")

            val posterUrl = webpSource?.attr("data-src") ?: imgTag?.attr("data-src") ?: imgTag?.attr("src") ?: ""
            val rawTitle = imgTag?.attr("alt") ?: aTag.attr("data-title")
            val (titlePl, titleEn, _) = parseTitleAndYear(rawTitle)
            val rating = element.selectFirst(".rate")?.text()?.replace(",", ".")?.toFloatOrNull()

            movies.add(
                MovieItem(
                    url = url,
                    titlePl = titlePl,
                    titleEn = titleEn,
                    filmanRating = rating,
                    posterUrl = posterUrl,
                )
            )
        }

        // .poster
        val posterItems = doc.select(".poster")
        posterItems.forEach { element ->
            val aTag = element.selectFirst("a") ?: return@forEach
            val url = aTag.attr("href")
            if (!parsedUrls.add(url)) return@forEach

            val imgTag = aTag.selectFirst("img")
            val posterUrl = imgTag?.attr("data-src")?.takeIf { it.isNotEmpty() } ?: imgTag?.attr("src").orEmpty()

            val filmTitleDiv = element.parent()?.selectFirst(".film_title")
            val rawTitle = filmTitleDiv?.text() ?: imgTag?.attr("alt") ?: aTag.attr("data-title")
            val (titlePl, titleEn, _) = parseTitleAndYear(rawTitle)
            
            val rating = element.parent()?.selectFirst(".rate")?.text()?.replace(",", ".")?.toFloatOrNull()

            if (url.isNotEmpty() && rawTitle.isNotEmpty()) {
                movies.add(
                    MovieItem(
                        url = url,
                        titlePl = titlePl,
                        titleEn = titleEn,
                        filmanRating = rating,
                        posterUrl = posterUrl,
                    )
                )
            }
        }
        
        return movies
    }

    fun parseSearchMovies(doc: Document): List<MovieItem> {
        val movies = mutableListOf<MovieItem>()
        val elements = doc.select(".poster")
        for (element in elements) {
            val aTag = element.selectFirst("a") ?: continue
            val url = aTag.attr("href")

            val imgTag = aTag.selectFirst("img")
            val posterUrl = imgTag?.attr("src") ?: ""

            val filmTitleDiv = element.parent()?.selectFirst(".film_title")
            val rawTitle = filmTitleDiv?.text() ?: imgTag?.attr("alt") ?: aTag.attr("data-title")
            val (titlePl, titleEn, _) = parseTitleAndYear(rawTitle)
            val rating = element.parent()?.selectFirst(".rate")?.text()?.replace(",", ".")?.toFloatOrNull()

            if (url.isNotEmpty() && rawTitle.isNotEmpty()) {
                movies.add(
                    MovieItem(
                        url = url,
                        titlePl = titlePl,
                        titleEn = titleEn,
                        filmanRating = rating,
                        posterUrl = posterUrl,
                    )
                )
            }
        }
        return movies
    }

    fun parseMediaMetadata(doc: Document, fallbackYear: Int?): MediaMetadata {
        var metaYear = fallbackYear
        var metaViews: Int? = null
        var metaDuration: String? = null
        var metaCountry: String? = null

        val metaRow = doc.selectFirst(".flm-meta-row")
        if (metaRow != null) {
            val metaItems = metaRow.children()
            if (metaItems.size > 0) metaYear = metaItems[0].text().replace(Regex("[^0-9]"), "").toIntOrNull() ?: fallbackYear
            if (metaItems.size > 1) metaViews = metaItems[1].text().replace(Regex("[^0-9]"), "").toIntOrNull()
            if (metaItems.size > 2) metaDuration = metaItems[2].text().replace(Regex("[^a-zA-Z0-9 :ąćęłńóśźżĄĆĘŁŃÓŚŹŻ]"), "").trim()
            if (metaItems.size > 3) metaCountry = metaItems[3].text().replace(Regex("[^a-zA-Z0-9 ,.-ąćęłńóśźżĄĆĘŁŃÓŚŹŻ]"), "").trim()
        }
        
        return MediaMetadata(
            year = metaYear,
            views = metaViews,
            duration = metaDuration,
            country = metaCountry
        )
    }

    fun parseCategories(doc: Document): List<CategoryInfo> {
        return doc.select(".flm-genre-tag").mapNotNull {
            val catUrl = it.attr("href")
            val catName = it.text().trim()
            if (catName.isNotEmpty()) CategoryInfo(catName, catUrl) else null
        }
    }

    fun parseTags(doc: Document): List<TagInfo> {
        return doc.select(".flm-tag-list").firstOrNull()?.children()?.mapNotNull {
            val aTag = if (it.tagName() == "a") it else it.selectFirst("a")
            val tagName = aTag?.text()?.trim() ?: it.text().trim()
            val tagUrl = aTag?.attr("href") ?: ""
            if (tagName.isNotEmpty()) TagInfo(tagName, tagUrl) else null
        } ?: emptyList()
    }

    fun parseActors(doc: Document): List<ActorInfo> {
        val actors = mutableListOf<ActorInfo>()
        val crewGroups = doc.select(".flm-crew-group")
        for (group in crewGroups) {
            val roleText = group.selectFirst("h1, h2, h3, h4, h5, .role-title, .title, .flm-crew-label")?.text()?.lowercase() ?: ""
            val role = when {
                roleText.contains("reżys") || roleText.contains("director") -> ActorRole.DIRECTOR
                roleText.contains("scenar") || roleText.contains("writer") -> ActorRole.WRITER
                roleText.contains("obsada") || roleText.contains("aktor") || roleText.contains("actor") || roleText.contains("występ") -> ActorRole.ACTOR
                else -> ActorRole.UNKNOWN
            }
            val items = group.select("li, .crew-item, .person-item, a, .flm-person-card").filter { it.select("img").isNotEmpty() || it.text().isNotBlank() }
            for (item in items) {
                if (item.tagName() == "a" && item.parent()?.tagName() == "li") continue
                
                val aTag = if (item.tagName() == "a") item else item.selectFirst("a")
                val personUrl = aTag?.attr("href")
                val img = item.selectFirst("img")
                val avatarUrl = img?.attr("data-src")?.takeIf { it.isNotBlank() } ?: img?.attr("src")
                val personName = img?.attr("alt")?.takeIf { it.isNotBlank() } ?: aTag?.text()?.trim()?.takeIf { it.isNotBlank() } ?: item.text().trim()
                
                if (personName.isNotBlank() && !personName.lowercase().contains(roleText) && !roleText.contains(personName.lowercase())) {
                    actors.add(ActorInfo(role, personName, avatarUrl, personUrl))
                }
            }
        }
        return actors
    }

    fun parseSimilarMovies(doc: Document): List<SimilarMovie> {
        val similarMovies = mutableListOf<SimilarMovie>()
        val similarList = doc.selectFirst("#item-list")
        if (similarList != null) {
            val items = similarList.select("a").filter { it.select("img").isNotEmpty() || it.attr("data-title").isNotBlank() }
            for (item in items) {
                val simUrl = item.attr("href")
                if (simUrl.isBlank()) continue
                val img = item.selectFirst("img")
                val simPoster = img?.attr("data-src")?.takeIf { it.isNotBlank() } ?: img?.attr("src") ?: ""
                val simName = item.attr("data-title").takeIf { it.isNotBlank() } ?: img?.attr("alt")?.takeIf { it.isNotBlank() } ?: item.text().trim()
                if (simName.isNotBlank()) {
                    similarMovies.add(SimilarMovie(simUrl, simName, simPoster))
                }
            }
        }
        return similarMovies
    }

    fun parseTvShowSeasons(doc: Document): List<Season> {
        val seasons = mutableListOf<Season>()
        val episodeList = doc.selectFirst("#episode-list, .episode-list")
        if (episodeList != null) {
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
        }
        return seasons
    }

    fun parseEmbedLinks(doc: Document): Pair<String, List<EmbedLink>> {
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
            var serverName = element.selectFirst("img")?.attr("alt") ?: element.text().trim()
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
                if (serverName.isEmpty() || serverName.equals("Oglądaj", ignoreCase = true)) {
                    val img = tr.selectFirst("img")
                    serverName = img?.attr("alt") ?: serverName
                }
            }

            if (serverName.isEmpty()) serverName = "Unknown"

            links.add(EmbedLink(id, serverName, version, quality))
        }
        return routeToken to links
    }
}
