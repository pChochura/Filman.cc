package com.pointlessapps.filman.data.scraper

import android.util.Base64
import com.pointlessapps.filman.config.ZaluknijConfig
import com.pointlessapps.filman.data.model.CategoryInfo
import com.pointlessapps.filman.data.model.DetailedMedia
import com.pointlessapps.filman.data.model.EmbedLink
import com.pointlessapps.filman.data.model.MediaSource
import com.pointlessapps.filman.data.model.MovieItem
import com.pointlessapps.filman.data.model.Rating
import com.pointlessapps.filman.data.model.SearchResults
import com.pointlessapps.filman.data.scraper.FilmanParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

internal class ZaluknijScraper(
    private val okHttpClient: OkHttpClient,
    private val sessionManager: com.pointlessapps.filman.data.local.ZaluknijSessionManager,
    private val appSessionManager: com.pointlessapps.filman.data.local.SessionManager,
) {

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Search zaluknij.cc for the given query.
     * Returns movies (href=/film/) and TV shows (href=/serial-online/) found.
     */
    suspend fun searchMovies(query: String): SearchResults = withContext(Dispatchers.IO) {
        try {
            val body = FormBody.Builder()
                .add("phrase", query)
                .build()

            val cookie = sessionManager.cookieFlow.firstOrNull()
            if (cookie.isNullOrBlank()) {
                sessionManager.requestChallenge()
                return@withContext SearchResults(errorMessage = "Wymagana autoryzacja Cloudflare (Zaluknij)")
            }

            val request = Request.Builder()
                .url("${ZaluknijConfig.BASE_URL}${ZaluknijConfig.PATH_SEARCH}")
                .post(body)
                .header("Accept", "*/*")
                .header("Accept-Language", "pl-PL,pl;q=0.9,en-US;q=0.8,en;q=0.7")
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .header("Origin", ZaluknijConfig.BASE_URL)
                .header("Referer", "${ZaluknijConfig.BASE_URL}/")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Cookie", cookie)
                .header("User-Agent", appSessionManager.getUserAgent())
                .build()

            val response = okHttpClient.newCall(request).execute()
            val html = response.use { it.body.string() ?: "" }

            if (response.code == 403 || html.contains("cf-browser-verification") || html.contains("Just a moment...")) {
                sessionManager.requestChallenge()
                return@withContext SearchResults(errorMessage = "Trwa autoryzacja Cloudflare (Zaluknij)...")
            }

            val doc = Jsoup.parse(html)

            val movies = mutableListOf<MovieItem>()
            val tvShows = mutableListOf<MovieItem>()

            doc.select("div.col-xs-3, div.col-xs-3.col-lg-2").forEach { col ->
                val a = col.selectFirst("a[href]") ?: return@forEach
                val href = a.attr("href").trim()
                val url = if (href.startsWith("http")) {
                    href
                } else {
                    "${ZaluknijConfig.BASE_URL}$href"
                }

                val titleText = col.selectFirst(".title")?.text()?.trim() ?: return@forEach
                val yearText = col.selectFirst(".year")?.text()?.trim()
                val year = yearText?.toIntOrNull()

                val imgTag = col.selectFirst("img.img-responsive")
                val posterSrc = imgTag?.attr("src")?.trim() ?: ""
                val posterUrl = when {
                    posterSrc.isEmpty() -> ""
                    posterSrc.startsWith("http") -> posterSrc
                    else -> "${ZaluknijConfig.BASE_URL}$posterSrc"
                }

                // Parse title — zaluknij uses " / " separator for Polish / English titles
                val titleParts = titleText.split(Regex("\\s*/\\s*"))
                val titlePl = titleParts[0].trim()
                val titleEn = titleParts.getOrNull(1)?.trim()

                val item = MovieItem(
                    url = url,
                    titlePl = titlePl,
                    titleEn = titleEn,
                    posterUrl = posterUrl,
                    backgroundUrl = posterUrl,
                    source = MediaSource.ZALUKNIJ,
                    year = year,
                )

                if (url.contains("/serial-online/")) {
                    tvShows.add(item)
                } else {
                    movies.add(item)
                }
            }

            SearchResults(movies = movies, tvShows = tvShows)
        } catch (e: Exception) {
            e.printStackTrace()
            SearchResults(errorMessage = e.message)
        }
    }

    suspend fun getMediaDetails(url: String): DetailedMedia? = withContext(Dispatchers.IO) {
        try {
            val doc = fetchDoc(url) ?: return@withContext null

            val titleText = doc.selectFirst("h1")?.text()
                ?: doc.selectFirst("meta[property=\"og:title\"]")?.attr("content")
                ?: doc.selectFirst("title")?.text()
                ?: "Unknown"

            // Zaluknij often has titles like "Polski tytuł / English title"
            val titleParts = titleText.split(Regex("\\s*/\\s*"))
            val titlePl = titleParts[0].trim()
            val titleEn = titleParts.getOrNull(1)?.trim()

            val descMeta = doc.selectFirst("meta[name=\"description\"]")?.attr("content")
                ?: doc.selectFirst("meta[property=\"og:description\"]")?.attr("content")
                ?: doc.selectFirst(".description, .desc")?.text()
                ?: ""

            var posterUrl = doc.selectFirst("#single-poster > img")?.attr("src")
                ?: doc.selectFirst("meta[property=\"og:image\"]")?.attr("content")
                ?: doc.selectFirst("img.img-responsive")?.attr("src") ?: ""
            if (posterUrl.isNotEmpty() && !posterUrl.startsWith("http")) {
                posterUrl = "${ZaluknijConfig.BASE_URL}$posterUrl"
            }

            val embeds = parseEmbeds(doc)

            // Rating
            val ratingValueText =
                doc.selectFirst("[itemprop=\"ratingValue\"]")?.text()?.replace(",", ".")
            val rating = ratingValueText?.toFloatOrNull()?.let { Rating(it, 5f) }

            // Categories
            val categories = mutableListOf<CategoryInfo>()
            doc.select("[itemprop=\"genre\"]").forEach { el ->
                val name = el.text().trim()
                if (name.isNotEmpty()) {
                    categories.add(CategoryInfo(name, "", 0))
                }
            }

            // Similar Movies
            val similarMovies = mutableListOf<MovieItem>()
            doc.select("#item-list > div")
                .forEach { col ->
                    val a = col.selectFirst("a[href]") ?: return@forEach
                    val href = a.attr("href").trim()
                    val simUrl =
                        if (href.startsWith("http")) href else "${ZaluknijConfig.BASE_URL}$href"

                    val simTitleText = col.selectFirst(".title")?.text()?.trim() ?: return@forEach
                    val simYearText = col.selectFirst(".year")?.text()?.trim()
                    val simYear = simYearText?.toIntOrNull()

                    val simImgTag = col.selectFirst("img.img-responsive")
                    val simPosterSrc = simImgTag?.attr("src")?.trim() ?: ""
                    val simPosterUrl = when {
                        simPosterSrc.isEmpty() -> ""
                        simPosterSrc.startsWith("http") -> simPosterSrc
                        else -> "${ZaluknijConfig.BASE_URL}$simPosterSrc"
                    }

                    val simTitleParts = simTitleText.split(Regex("\\s*/\\s*"))
                    val simTitlePl = simTitleParts[0].trim()
                    val simTitleEn = simTitleParts.getOrNull(1)?.trim()

                    similarMovies.add(
                        MovieItem(
                            url = simUrl,
                            titlePl = simTitlePl,
                            titleEn = simTitleEn,
                            posterUrl = simPosterUrl,
                            backgroundUrl = simPosterUrl,
                            source = MediaSource.ZALUKNIJ,
                            year = simYear,
                        ),
                    )
                }

            val seasons = FilmanParser.parseTvShowSeasons(doc).map { season ->
                season.copy(
                    episodes = season.episodes.map { ep ->
                        ep.copy(url = if (ep.url.startsWith("http")) ep.url else "${ZaluknijConfig.BASE_URL}${ep.url}")
                    }
                )
            }

            val baseItem = MovieItem(
                url = url,
                titlePl = titlePl,
                titleEn = titleEn,
                filmanRating = rating,
                posterUrl = posterUrl,
                backgroundUrl = posterUrl,
                source = MediaSource.ZALUKNIJ,
                description = descMeta,
                seasons = seasons.ifEmpty { null },
            )

            DetailedMedia(
                baseItem = baseItem,
                embeds = embeds,
                categories = categories,
                similarMovies = similarMovies,
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Fetches embed links for a given media title. For TV shows, pass [season] and [episode]
     * to select the correct episode page before extracting embeds.
     */
    suspend fun getEmbeds(
        title: String,
        year: String?,
        season: Int? = null,
        episode: Int? = null,
    ): List<EmbedLink> = withContext(Dispatchers.IO) {
        val embeds = mutableListOf<EmbedLink>()
        try {
            // 1. Search
            val searchResults = searchMovies(title)
            val allResults = searchResults.movies + searchResults.tvShows

            if (allResults.isEmpty()) return@withContext embeds

            // 2. Find the best matching result
            var selectedUrl: String? = null

            if (season != null && episode != null) {
                // Prefer TV shows for episode-based lookup
                val tvShow = searchResults.tvShows.firstOrNull { item ->
                    titleMatches(
                        item.titlePl,
                        title,
                    ) || (item.titleEn != null && titleMatches(item.titleEn, title))
                } ?: searchResults.tvShows.firstOrNull()

                if (tvShow != null) {
                    // Navigate to the TV show page and find the episode link
                    val showDoc = fetchDoc(tvShow.url) ?: return@withContext embeds
                    selectedUrl = findEpisodeUrl(showDoc, season, episode)
                }
            }

            if (selectedUrl == null) {
                // Movie lookup — prefer year match
                val movie = allResults.firstOrNull { item ->
                    (titleMatches(item.titlePl, title) ||
                            (item.titleEn != null && titleMatches(item.titleEn, title))) &&
                            (year == null || item.year?.toString() == year)
                } ?: allResults.firstOrNull { item ->
                    titleMatches(item.titlePl, title) ||
                            (item.titleEn != null && titleMatches(item.titleEn, title))
                } ?: allResults.firstOrNull()

                selectedUrl = movie?.url
            }

            if (selectedUrl == null) return@withContext embeds

            // 3. Fetch the target page and extract embeds
            val targetDoc = fetchDoc(selectedUrl) ?: return@withContext embeds
            embeds.addAll(parseEmbeds(targetDoc))
        } catch (e: Exception) {
            e.printStackTrace()
        }
        embeds
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private fun titleMatches(a: String, b: String): Boolean =
        a.contains(b, ignoreCase = true) || b.contains(a, ignoreCase = true)

    private suspend fun fetchDoc(url: String): Document? {
        return try {
            val cookie = sessionManager.cookieFlow.firstOrNull() ?: ""

            val request = Request.Builder()
                .url(url)
                .get()
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "pl-PL,pl;q=0.9,en-US;q=0.8,en;q=0.7")
                .header("Referer", ZaluknijConfig.BASE_URL)
                .header("Cookie", cookie)
                .header("User-Agent", appSessionManager.getUserAgent())
                .build()

            val response = okHttpClient.newCall(request).execute()
            val html = response.use { it.body.string() ?: "" }

            if (response.code == 403 || html.contains("cf-browser-verification") || html.contains("Just a moment...")) {
                sessionManager.requestChallenge()
                return null
            }

            Jsoup.parse(html)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Finds the URL for a specific season/episode inside a TV show page.
     * The HTML has structure:
     * <ul id="episode-list">
     *   <li>
     *     <span>Sezon 1</span>
     *     <ul>
     *       <li><a href="...">[s01e01] Odcinek 1</a></li>
     *     </ul>
     *   </li>
     * </ul>
     */
    private fun findEpisodeUrl(doc: Document, season: Int, episode: Int): String? {
        val seasons = FilmanParser.parseTvShowSeasons(doc)
        val targetSeason = seasons.find { FilmanParser.extractNumber(it.name) == season } ?: return null
        val targetEpisode = targetSeason.episodes.find { FilmanParser.extractEpisodeNumber(it.title) == episode } ?: return null
        val href = targetEpisode.url
        return if (href.startsWith("http")) href else "${ZaluknijConfig.BASE_URL}$href"
    }

    /**
     * Parses `data-iframe` base64-encoded JSON attributes from `.link-to-video` rows.
     * Each row also carries version (Lektor/Napisy/Dubbing) and quality (Wysoka/HD/…).
     */
    private fun parseEmbeds(doc: Document): List<EmbedLink> {
        val embeds = mutableListOf<EmbedLink>()

        doc.select("tr").forEach { row ->
            val linkTd = row.selectFirst("td.link-to-video") ?: return@forEach
            val anchor = linkTd.selectFirst("a[data-iframe]") ?: return@forEach

            val dataIframe = anchor.attr("data-iframe")
            if (dataIframe.isBlank()) return@forEach

            val embedUrl = try {
                val jsonStr = String(Base64.decode(dataIframe, Base64.DEFAULT))
                val json = JSONObject(jsonStr)
                json.optString("src").takeIf { it.isNotBlank() }
            } catch (e: Exception) {
                null
            } ?: return@forEach

            // Extract version (3rd td) and quality (4th td)
            val tds = row.select("td")
            val version = tds.getOrNull(2)?.text()?.trim() ?: ""
            val quality = tds.getOrNull(3)?.text()?.trim() ?: ""

            // Server name from alt attribute of the img, or from the anchor text
            val serverName = linkTd.selectFirst("img[alt]")?.attr("alt")?.trim()
                ?: anchor.text().trim()
                    .ifEmpty { runCatching { java.net.URL(embedUrl).host }.getOrDefault("") }

            embeds.add(
                EmbedLink(
                    url = embedUrl,
                    serverName = serverName,
                    version = version,
                    quality = quality,
                ),
            )
        }
        return embeds
    }
}
