package com.pointlessapps.filman.data.scraper

import com.pointlessapps.filman.config.EkinoConfig
import com.pointlessapps.filman.data.model.DetailedMedia
import com.pointlessapps.filman.data.model.EmbedLink
import com.pointlessapps.filman.data.model.MovieItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

internal class EkinoScraper {

    suspend fun getEmbeds(title: String, year: String?): List<EmbedLink> =
        withContext(Dispatchers.IO) {
            val embeds = mutableListOf<EmbedLink>()
            try {
                val searchUrl = "${EkinoConfig.BASE_URL}${EkinoConfig.PATH_SEARCH}${title.replace(" ", "+")}"
                val searchDoc = Jsoup.connect(searchUrl)
                    .userAgent("Mozilla/5.0")
                    .get()

                val movieLinks = searchDoc.select(".movies-list-item a[href*='/movie/show/']")
                var selectedUrl: String? = null

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
                if (!selectedUrl.startsWith("http")) selectedUrl = "${EkinoConfig.BASE_URL}$selectedUrl"

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

                        try {
                            val watchUrl = "${EkinoConfig.BASE_URL}${EkinoConfig.PATH_WATCH}$host/$id"
                            val watchDoc = Jsoup.connect(watchUrl)
                                .userAgent("Mozilla/5.0")
                                .get()

                            val realEmbedLink = watchDoc.select("a.buttonprch").attr("href")
                            if (realEmbedLink.isNotEmpty()) {
                                embeds.add(
                                    EmbedLink(
                                        url = realEmbedLink,
                                        serverName = EkinoConfig.DOMAIN,
                                        version = host,
                                        quality = "Ekino",
                                    ),
                                )
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            embeds
        }

    suspend fun searchMovies(query: String): List<MovieItem> = withContext(Dispatchers.IO) {
        val movies = mutableListOf<MovieItem>()
        try {
            val searchUrl = "${EkinoConfig.BASE_URL}${EkinoConfig.PATH_SEARCH}${query.replace(" ", "+")}"
            val searchDoc = Jsoup.connect(searchUrl).userAgent("Mozilla/5.0").get()

            searchDoc.select(".movies-list-item").forEach { item ->
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

                movies.add(
                    MovieItem(
                        url = url,
                        titlePl = titlePl,
                        titleEn = titleEn,
                        posterUrl = posterUrl,
                        backgroundUrl = posterUrl,
                        description = description,
                    ),
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        movies
    }

    suspend fun getMediaDetails(url: String): DetailedMedia? = withContext(Dispatchers.IO) {
        try {
            val doc = Jsoup.connect(url).userAgent("Mozilla/5.0").get()
            val titleText = doc.selectFirst("h1.title")?.text() ?: "Unknown"
            val titleMeta = doc.selectFirst("title")?.text()?.substringBefore(" (") ?: titleText
            val descMeta = doc.selectFirst(".descriptionMovie")?.text() ?: doc.selectFirst("meta[name=\"description\"]")?.attr("content") ?: ""
            val posterUrl = doc.selectFirst("img.moviePoster")?.attr("src")?.let { if (it.startsWith("http")) it else "${EkinoConfig.BASE_URL}$it" } ?: ""

            val ratingValue = doc.selectFirst(".score #scoreSum span[itemprop=ratingValue]")?.text()?.replace(",", ".")?.toFloatOrNull()
            val rating = ratingValue?.let { com.pointlessapps.filman.data.model.Rating(it, 10f) }

            val actors = mutableListOf<com.pointlessapps.filman.data.model.ActorInfo>()
            val movieActorsDiv = doc.selectFirst("div.movieActors")
            if (movieActorsDiv != null) {
                movieActorsDiv.select("ul.actors li").forEach { li ->
                    val aTag = li.selectFirst("a")
                    if (aTag != null) {
                        val name = aTag.text().trim()
                        val href = aTag.attr("href")
                        val actorUrl = if (href.startsWith("http")) href else "${EkinoConfig.BASE_URL}$href"
                        actors.add(
                            com.pointlessapps.filman.data.model.ActorInfo(
                                role = com.pointlessapps.filman.data.model.ActorRole.ACTOR,
                                name = name,
                                avatarUrl = null,
                                url = actorUrl,
                            )
                        )
                    }
                }
            }

            val categories = mutableListOf<com.pointlessapps.filman.data.model.CategoryInfo>()
            var year: Int? = null
            doc.select(".catBox .cat a").forEach { aTag ->
                val text = aTag.text().trim()
                val href = aTag.attr("href")
                if (href.isEmpty() || text.matches(Regex("\\d{4}"))) {
                    year = text.toIntOrNull()
                } else if (href.contains("kategoria")) {
                    val catId = href.substringAfter("kategoria[").substringBefore("]").toIntOrNull() ?: 0
                    categories.add(
                        com.pointlessapps.filman.data.model.CategoryInfo(
                            name = text,
                            url = "${EkinoConfig.BASE_URL}$href",
                            id = catId
                        )
                    )
                }
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
                    try {
                        val watchUrl = "${EkinoConfig.BASE_URL}${EkinoConfig.PATH_WATCH}$host/$id"
                        val watchDoc = Jsoup.connect(watchUrl).userAgent("Mozilla/5.0").get()
                        val realEmbedLink = watchDoc.select("a.buttonprch").attr("href")
                        if (realEmbedLink.isNotEmpty()) {
                            embeds.add(
                                EmbedLink(
                                    url = realEmbedLink,
                                    serverName = EkinoConfig.DOMAIN,
                                    version = host,
                                    quality = "Ekino",
                                ),
                            )
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            DetailedMedia(
                baseItem = com.pointlessapps.filman.data.model.MovieItem(
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
                metaInfo = com.pointlessapps.filman.data.model.MediaMetadata(
                    year = year,
                    views = null,
                    duration = null,
                    countries = emptyList(),
                ),
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
