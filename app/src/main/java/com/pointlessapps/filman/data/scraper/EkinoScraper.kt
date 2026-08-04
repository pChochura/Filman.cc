package com.pointlessapps.filman.data.scraper

import com.pointlessapps.filman.config.EkinoConfig
import com.pointlessapps.filman.data.model.ActorDetails
import com.pointlessapps.filman.data.model.ActorInfo
import com.pointlessapps.filman.data.model.ActorRole
import com.pointlessapps.filman.data.model.CategoryInfo
import com.pointlessapps.filman.data.model.DetailedMedia
import com.pointlessapps.filman.data.model.EmbedLink
import com.pointlessapps.filman.data.model.MediaMetadata
import com.pointlessapps.filman.data.model.MovieItem
import com.pointlessapps.filman.data.model.Rating
import com.pointlessapps.filman.data.model.SearchResults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

internal class EkinoScraper {

    suspend fun getEmbeds(
        title: String,
        year: String?,
        season: Int? = null,
        episode: Int? = null,
    ): List<EmbedLink> = withContext(Dispatchers.IO) {
        val embeds = mutableListOf<EmbedLink>()
        try {
            var selectedUrl: String? = null

            if (season != null && episode != null) {
                val searchResults = searchMovies(title)

                var tvShowUrl = searchResults.tvShows.firstOrNull { item ->
                    val itemTitle = item.titlePl
                    itemTitle.contains(title, ignoreCase = true) || title.contains(
                        itemTitle,
                        ignoreCase = true,
                    )
                }?.url ?: searchResults.tvShows.firstOrNull()?.url

                if (tvShowUrl != null) {
                    if (!tvShowUrl.startsWith("http")) {
                        tvShowUrl = "${EkinoConfig.BASE_URL}$tvShowUrl"
                    }

                    val baseUrlWithoutSlash = tvShowUrl.removeSuffix("/")
                    selectedUrl = baseUrlWithoutSlash.replace(
                        "/show/",
                        "/watch/",
                    ) + "+season[$season]+episode[$episode]+"
                }
            }

            if (selectedUrl == null) {
                val searchUrl =
                    "${EkinoConfig.BASE_URL}${EkinoConfig.PATH_SEARCH}${title.replace(" ", "+")}"
                val searchDoc = Jsoup.connect(searchUrl)
                    .userAgent("Mozilla/5.0")
                    .get()

                val movieLinks = searchDoc.select(".movies-list-item a[href*='/movie/show/']")

                for (link in movieLinks) {
                    val href = link.attr("href")
                    val itemTitle = link.text()
                    val itemText = link.closest(".movies-list-item")?.text() ?: ""

                    if (itemTitle.contains(title, ignoreCase = true) || title.contains(
                            itemTitle,
                            ignoreCase = true,
                        )
                    ) {
                        if (year != null && itemText.contains(year)) {
                            selectedUrl = href
                            break
                        } else if (selectedUrl == null) {
                            selectedUrl = href
                        }
                    }
                }

                if (selectedUrl == null) return@withContext emptyList()
                if (!selectedUrl.startsWith("http")) {
                    selectedUrl = "${EkinoConfig.BASE_URL}$selectedUrl"
                }
            }

            val movieDoc = Jsoup.connect(selectedUrl)
                .userAgent("Mozilla/5.0")
                .get()

            val playerLinks = movieDoc.select("a[onClick*='ShowPlayer']")
            for (player in playerLinks) {
                val onClick = player.attr("onClick")
                val regex = "ShowPlayer\\('([^']+)',\\s*'([^']+)'\\)".toRegex()
                val match = regex.find(onClick)
                if (match != null) {
                    val host = match.groupValues[1]
                    val id = match.groupValues[2]

                    val watchUrl =
                        "${EkinoConfig.BASE_URL}${EkinoConfig.PATH_WATCH}$host/$id"

                    embeds.add(
                        EmbedLink(
                            url = watchUrl,
                            serverName = EkinoConfig.DOMAIN,
                            version = host,
                            quality = "Ekino",
                        ),
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        embeds
    }

    suspend fun searchMovies(query: String): SearchResults = withContext(Dispatchers.IO) {
        try {
            val searchUrl =
                "${EkinoConfig.BASE_URL}${EkinoConfig.PATH_SEARCH}${query.replace(" ", "+")}"
            val searchDoc = Jsoup.connect(searchUrl).userAgent("Mozilla/5.0").get()

            val movieWrap = searchDoc.selectFirst(".movie-wrap")
            val divs = movieWrap?.select("> div")

            val movies = mutableListOf<MovieItem>()
            val tvShows = mutableListOf<MovieItem>()

            if (divs != null && divs.size >= 2) {
                movies.addAll(parseMoviesList(divs[0]))
                tvShows.addAll(parseMoviesList(divs[1]))
            } else if (divs != null && divs.size == 1) {
                val prev = divs[0].previousElementSibling()
                if (prev != null && prev.text().contains("Serial", ignoreCase = true)) {
                    tvShows.addAll(parseMoviesList(divs[0]))
                } else {
                    movies.addAll(parseMoviesList(divs[0]))
                }
            } else {
                movies.addAll(parseMoviesList(searchDoc))
            }
            SearchResults(movies = movies, tvShows = tvShows)
        } catch (e: Exception) {
            e.printStackTrace()
            SearchResults(errorMessage = e.message)
        }
    }

    private fun parseMoviesList(element: org.jsoup.nodes.Element): List<MovieItem> {
        val movies = mutableListOf<MovieItem>()
        element.select(".movies-list-item").forEach { item ->
            val href = item.selectFirst(".title > a")?.attr("href") ?: return@forEach
            val url = if (href.startsWith("http")) href else "${EkinoConfig.BASE_URL}$href"
            val titlePl = item.selectFirst(".title > a")?.text()?.trim() ?: "Unknown"
            val titleEn = item.selectFirst(".title .blue a")?.text()?.trim()
            val posterSrc = item.selectFirst(".cover-list img")?.attr("src") ?: ""
            val posterUrl = if (posterSrc.startsWith("http") || posterSrc.isEmpty()) {
                posterSrc
            } else {
                "${EkinoConfig.BASE_URL}$posterSrc"
            }
            val description = item.selectFirst(".movieDesc")?.text()?.trim() ?: ""

            val ratingValue =
                item.selectFirst(".sum-vote div")?.text()?.replace(",", ".")?.toFloatOrNull()
            val rating = ratingValue?.let { Rating(it, 10f) }

            movies.add(
                MovieItem(
                    url = url,
                    titlePl = titlePl,
                    titleEn = titleEn,
                    filmanRating = rating,
                    posterUrl = posterUrl,
                    backgroundUrl = posterUrl,
                    description = description,
                ),
            )
        }
        return movies
    }

    suspend fun getActorDetails(url: String): ActorDetails? =
        withContext(Dispatchers.IO) {
            try {
                val doc = Jsoup.connect(url).userAgent("Mozilla/5.0").get()
                val name = url.substringAfter("aktor[").substringBefore("]").replace("%20", " ")
                    .replace("+", " ")
                val movies = parseMoviesList(doc)

                ActorDetails(
                    name = name,
                    avatarUrl = "",
                    birthDate = null,
                    birthPlace = null,
                    height = null,
                    description = null,
                    filmwebRating = null,
                    moviesDirector = emptyList(),
                    moviesWriter = emptyList(),
                    moviesCast = movies,
                )
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

    suspend fun getMediaDetails(url: String): DetailedMedia? = withContext(Dispatchers.IO) {
        try {
            val doc = Jsoup.connect(url).userAgent("Mozilla/5.0").get()
            val titleText = doc.selectFirst("h1.title")?.text() ?: "Unknown"
            val titleMeta = doc.selectFirst("title")?.text()?.substringBefore(" (") ?: titleText
            val descMeta = doc.selectFirst(".descriptionMovie")?.text()
                ?: doc.selectFirst("meta[name=\"description\"]")?.attr("content") ?: ""
            val posterUrl = doc.selectFirst("img.moviePoster")?.attr("src")
                ?.let { if (it.startsWith("http")) it else "${EkinoConfig.BASE_URL}$it" } ?: ""

            val ratingValue = doc.selectFirst(".score #scoreSum span[itemprop=ratingValue]")?.text()
                ?.replace(",", ".")?.toFloatOrNull()
            val rating = ratingValue?.let { Rating(it, DEFAULT_MAX_EKINO_RATING) }

            val actors = mutableListOf<ActorInfo>()
            val movieActorsDiv = doc.selectFirst("div.movieActors")
            movieActorsDiv?.select("ul.actors li")?.forEach { li ->
                val aTag = li.selectFirst("a")
                if (aTag != null) {
                    val name = aTag.text().trim()
                    val href = aTag.attr("href")
                    val actorUrl = if (href.startsWith("http")) {
                        href
                    } else {
                        "${EkinoConfig.BASE_URL}$href"
                    }
                    actors.add(
                        ActorInfo(
                            role = ActorRole.ACTOR,
                            name = name,
                            avatarUrl = null,
                            url = actorUrl,
                        ),
                    )
                }
            }

            val categories = mutableListOf<CategoryInfo>()
            var year: Int? = null
            doc.select(".catBox .cat a").forEach { aTag ->
                val text = aTag.text().trim()
                val href = aTag.attr("href")
                if (href.isEmpty() || text.matches(Regex("\\d{4}"))) {
                    year = text.toIntOrNull()
                } else if (href.contains("kategoria")) {
                    val catId = href.substringAfter("kategoria[")
                        .substringBefore("]").toIntOrNull() ?: 0
                    categories.add(
                        CategoryInfo(
                            name = text,
                            url = "${EkinoConfig.BASE_URL}$href",
                            id = catId,
                        ),
                    )
                }
            }

            val similarMovies = mutableListOf<MovieItem>()
            doc.select(".relatedmovie > a").forEach { aTag ->
                val href = aTag.attr("href")
                val url = when {
                    href.isEmpty() -> ""
                    href.startsWith("http") -> href
                    href.startsWith("//") -> "https:$href"
                    else -> "${EkinoConfig.BASE_URL}$href"
                }
                val imgTag = aTag.selectFirst("img.related")
                val posterSrc = imgTag?.attr("src") ?: ""
                val relatedPosterUrl = when {
                    posterSrc.isEmpty() -> ""
                    posterSrc.startsWith("http") -> posterSrc
                    posterSrc.startsWith("//") -> "https:$posterSrc"
                    else -> "${EkinoConfig.BASE_URL}$posterSrc"
                }
                val rawTitle = aTag.selectFirst(".title_related")?.text()?.trim() ?: "Unknown"
                val titleParts = rawTitle.split(" / ")
                val titlePl = titleParts.getOrNull(0)?.trim() ?: "Unknown"
                val titleEn = titleParts.getOrNull(1)?.trim()

                similarMovies.add(
                    MovieItem(
                        url = url,
                        titlePl = titlePl,
                        titleEn = titleEn,
                        posterUrl = relatedPosterUrl,
                        backgroundUrl = relatedPosterUrl,
                    ),
                )
            }

            val embeds = mutableListOf<EmbedLink>()
            val playerLinks = doc.select("a[onClick*='ShowPlayer']")
            for (player in playerLinks) {
                val onClick = player.attr("onClick")
                val regex = "ShowPlayer\\('([^']+)',\\s*'([^']+)'\\)".toRegex()
                val match = regex.find(onClick)
                if (match != null) {
                    val host = match.groupValues[1]
                    val id = match.groupValues[2]
                    val watchUrl = "${EkinoConfig.BASE_URL}${EkinoConfig.PATH_WATCH}$host/$id"

                    embeds.add(
                        EmbedLink(
                            url = watchUrl,
                            serverName = EkinoConfig.DOMAIN,
                            version = host,
                            quality = "Ekino",
                        ),
                    )
                }
            }

            DetailedMedia(
                baseItem = MovieItem(
                    url = url,
                    titlePl = titleText,
                    filmanRating = rating,
                    posterUrl = posterUrl,
                    backgroundUrl = posterUrl,
                    description = descMeta,
                ),
                embeds = embeds,
                actors = actors,
                categories = categories,
                metaInfo = MediaMetadata(
                    year = year,
                    views = null,
                    duration = null,
                    countries = emptyList(),
                ),
                similarMovies = similarMovies,
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
