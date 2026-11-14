package com.pointlessapps.filman.data.scraper

import com.pointlessapps.filman.config.CdaHdConfig
import android.util.Log
import com.pointlessapps.filman.data.api.CloudflareChallengeDetected
import com.pointlessapps.filman.data.api.extractors.getExtractorForUrl
import com.pointlessapps.filman.data.api.isCloudflareChallengePage
import com.pointlessapps.filman.data.cache.CachePolicy
import com.pointlessapps.filman.data.cache.ModelCache
import com.pointlessapps.filman.data.local.CdaHdSessionManager
import com.pointlessapps.filman.data.local.SessionManager
import com.pointlessapps.filman.data.model.ActorInfo
import com.pointlessapps.filman.data.model.ActorRole
import com.pointlessapps.filman.data.model.CategoryInfo
import com.pointlessapps.filman.data.model.DetailedMedia
import com.pointlessapps.filman.data.model.EmbedLink
import com.pointlessapps.filman.data.model.EpisodeLink
import com.pointlessapps.filman.data.model.MediaMetadata
import com.pointlessapps.filman.data.model.MediaSource
import com.pointlessapps.filman.data.model.MovieItem
import com.pointlessapps.filman.data.model.Rating
import com.pointlessapps.filman.data.model.SearchResults
import com.pointlessapps.filman.data.model.Season
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.IOException
import java.net.URL
import java.text.Normalizer

class CdaHdScraper(
    private val okHttpClient: OkHttpClient,
    private val sessionManager: CdaHdSessionManager,
    private val appSessionManager: SessionManager,
    private val modelCache: ModelCache,
) {
    private companion object {
        const val CACHE_TTL_MEDIA_DETAILS = 60L * 60 * 1000
        val yearRegex = Regex("""\((\d{4})\)""")
        val titleSplitRegex = Regex("""\s*/\s*""")
    }

    private fun parseTitleAndYear(rawTitle: String): Triple<String, String?, Int?> {
        val yearMatch = yearRegex.find(rawTitle)
        val year = yearMatch?.groupValues?.get(1)?.toIntOrNull()
        val cleanedTitle = rawTitle.replace(yearRegex, "").trim()
        val parts = cleanedTitle.split(titleSplitRegex)
        val titlePl = parts[0].trim()
        val titleEn = parts.getOrNull(1)?.trim()
        return Triple(titlePl, titleEn, year)
    }

    /**
     * Search cda-hd.cc for the given query.
     */
    suspend fun searchMovies(query: String): SearchResults =
        withContext(Dispatchers.IO) {
            try {
                val searchUrl = CdaHdConfig.BASE_URL.toHttpUrl().newBuilder()
                    .addQueryParameter("s", query)
                    .build().toString()

                val doc = fetchDoc(searchUrl)
                if (doc == null) {
                    return@withContext SearchResults(errorMessage = "Nie udało się pobrać wyników wyszukiwania (CDA-HD)")
                }

                val movies = mutableListOf<MovieItem>()
                val tvShows = mutableListOf<MovieItem>()

                // DooPlay theme search result items: .result-item or article.item
                val items = doc.select(".result-item").ifEmpty {
                    doc.select("article.item, .search-page article, .items .item")
                }

                items.forEach { element ->
                    val titleAnchor = element.selectFirst(".boxinfo a:has(.tt), .title a, h3 a, .data .title a")
                        ?: element.selectFirst("a[href]") ?: return@forEach
                    val href = titleAnchor.attr("href").trim()
                    if (href.isBlank()) return@forEach

                    val url = if (href.startsWith("http")) href else "${CdaHdConfig.BASE_URL}$href"
                    val rawTitle = (titleAnchor.selectFirst(".tt")?.text() ?: titleAnchor.text()).trim().ifEmpty {
                        element.selectFirst("img[alt]")?.attr("alt")?.trim() ?: ""
                    }
                    if (rawTitle.isBlank()) return@forEach

                    val (titlePl, titleEn, parsedYear) = parseTitleAndYear(rawTitle)

                    val yearText = element.selectFirst(".extra .date, span.year, .year")?.text()?.trim()
                    val year = yearText?.toIntOrNull() ?: parsedYear

                    val posterImg = element.selectFirst("img")
                    val posterSrc = posterImg?.attr("data-src")?.takeIf { it.isNotBlank() }
                        ?: posterImg?.attr("src")?.takeIf { it.isNotBlank() }
                        ?: ""
                    val posterUrl = when {
                        posterSrc.isEmpty() -> ""
                        posterSrc.startsWith("http") -> posterSrc
                        posterSrc.startsWith("//") -> "https:$posterSrc"
                        else -> "${CdaHdConfig.BASE_URL}$posterSrc"
                    }

                    val isTvShow = url.contains("/serial") ||
                            url.contains("/tvshows") ||
                            element.text().contains("serial", ignoreCase = true) ||
                            element.selectFirst(".tvshows, .type-tvshows") != null

                    val movieItem = MovieItem(
                        url = url,
                        titlePl = titlePl,
                        titleEn = titleEn,
                        posterUrl = posterUrl,
                        backgroundUrl = posterUrl,
                        source = MediaSource.CDA_HD,
                        year = year,
                        isTvShow = isTvShow,
                    )

                    if (isTvShow) {
                        tvShows.add(movieItem)
                    } else {
                        movies.add(movieItem)
                    }
                }

                SearchResults(
                    movies = movies.distinctBy { it.url },
                    tvShows = tvShows.distinctBy { it.url },
                )
            } catch (e: CloudflareChallengeDetected) {
                SearchResults(
                    errorMessage = "Wymagana autoryzacja Cloudflare (CDA-HD)",
                    isAuthError = true,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("CdaHdScraper", "Search failed", e)
                SearchResults(errorMessage = e.message)
            }
        }

    /**
     * Fetch media details (movie or TV series) from cda-hd.cc
     */
    suspend fun getMediaDetails(mediaUrlRaw: String): DetailedMedia? =
        withContext(Dispatchers.IO) {
            val mediaUrl = mediaUrlRaw.substringBefore("?").substringBefore("#")

            try {
                modelCache.getOrFetch(
                    key = "media_$mediaUrl",
                    policy = CachePolicy.TTL(CACHE_TTL_MEDIA_DETAILS),
                ) {
                    val doc = fetchDoc(mediaUrl) ?: return@getOrFetch null

                    val rawTitle = doc.selectFirst("meta[property=\"og:title\"]")?.attr("content")
                        ?: doc.selectFirst("h1, .data h1")?.text()
                        ?: "Unknown Title"
                    val (titlePl, titleEn, parsedYear) = parseTitleAndYear(rawTitle)

                    val yearText = doc.selectFirst(".extra .date, .data .date, span.year")?.text()?.trim()
                    val year = yearText?.toIntOrNull() ?: parsedYear

                    val posterMeta = doc.selectFirst("meta[property=\"og:image\"]")?.attr("content")
                    val posterImg = doc.selectFirst(".poster img, .sheader .poster img")
                    val posterSrc = posterMeta?.takeIf { it.isNotBlank() }
                        ?: posterImg?.attr("data-src")?.takeIf { it.isNotBlank() }
                        ?: posterImg?.attr("src")?.takeIf { it.isNotBlank() }
                        ?: ""
                    val posterUrl = when {
                        posterSrc.isEmpty() -> ""
                        posterSrc.startsWith("http") -> posterSrc
                        posterSrc.startsWith("//") -> "https:$posterSrc"
                        else -> "${CdaHdConfig.BASE_URL}$posterSrc"
                    }

                    val description = doc.selectFirst(".wp-content p, #info .wp-content, .description")?.text()?.trim().orEmpty()

                    // Rating parsing
                    var filmanRating: Rating? = null
                    val ratingText = doc.selectFirst(".dt_rating_vgs, .star-rate span, .rating-score")?.text()?.trim()
                    val ratingValue = ratingText?.replace(",", ".")?.toFloatOrNull()
                    if (ratingValue != null) {
                        filmanRating = Rating(ratingValue, 10f)
                    }

                    // Categories
                    val categories = mutableListOf<CategoryInfo>()
                    doc.select(".sgeneres a, a[href*=\"/genre/\"], a[href*=\"/kategoria/\"]").forEach { a ->
                        val name = a.text().trim()
                        val href = a.attr("href").trim()
                        if (name.isNotBlank()) {
                            categories.add(
                                CategoryInfo(
                                    name = name,
                                    url = if (href.startsWith("http")) href else "${CdaHdConfig.BASE_URL}$href",
                                    id = href.hashCode(),
                                )
                            )
                        }
                    }

                    // Actors
                    val actors = mutableListOf<ActorInfo>()
                    doc.select(".persons .person, a[href*=\"/star/\"], a[href*=\"/director/\"]").forEach { personEl ->
                        val a = if (personEl.tagName() == "a") personEl else personEl.selectFirst("a")
                        val name = a?.text()?.trim() ?: personEl.selectFirst(".name")?.text()?.trim()
                        val href = a?.attr("href")?.trim().orEmpty()
                        val img = personEl.selectFirst("img")?.attr("src")
                        val isDirector = href.contains("/director/") || personEl.hasClass("director")

                        if (!name.isNullOrBlank()) {
                            actors.add(
                                ActorInfo(
                                    role = if (isDirector) ActorRole.DIRECTOR else ActorRole.ACTOR,
                                    name = name,
                                    avatarUrl = img?.takeIf { it.startsWith("http") },
                                    url = if (href.startsWith("http")) href else "${CdaHdConfig.BASE_URL}$href",
                                )
                            )
                        }
                    }

                    // Seasons and episodes (for TV shows)
                    val seasons = parseSeasons(doc)

                    // Embeds on the page
                    val embeds = parseEmbeds(doc, mediaUrl)

                    DetailedMedia(
                        baseItem = MovieItem(
                            url = mediaUrl,
                            titlePl = titlePl,
                            titleEn = titleEn,
                            filmanRating = filmanRating,
                            posterUrl = posterUrl,
                            backgroundUrl = posterUrl,
                            description = description,
                            source = MediaSource.CDA_HD,
                            seasons = seasons.ifEmpty { null },
                            year = year,
                        ),
                        embeds = embeds,
                        metaInfo = MediaMetadata(
                            year = year,
                            views = null,
                            duration = null,
                            countries = emptyList(),
                        ),
                        categories = categories,
                        actors = actors,
                        tags = emptyList(),
                        similarMovies = emptyList(),
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

    /**
     * Resolves embed links for a movie or TV show episode from cda-hd.cc
     */
    suspend fun getEmbeds(
        title: String,
        year: String?,
        season: Int? = null,
        episode: Int? = null,
        originalTitle: String? = null,
    ): List<EmbedLink> =
        withContext(Dispatchers.IO) {
            val embeds = mutableListOf<EmbedLink>()
            try {
                val titles = listOfNotNull(title, originalTitle?.takeIf { it.isNotBlank() })
                    .distinctBy { it.lowercase() }
                var match: MovieItem? = null
                for (query in titles) {
                    val results = searchMovies(query)
                    if (results.isAuthError) throw CloudflareChallengeDetected(CdaHdConfig.DOMAIN)
                    if (results.errorMessage != null) throw IOException("CDA-HD search failed: ${results.errorMessage}")
                    val candidates = if (season != null && episode != null) results.tvShows else results.movies
                    val matching = candidates.filter { item ->
                        titles.any { candidate ->
                            titleMatches(item.titlePl, candidate) ||
                                (item.titleEn != null && titleMatches(item.titleEn, candidate))
                        }
                    }
                    match = matching.firstOrNull { year != null && it.year?.toString() == year }
                        ?: matching.firstOrNull()
                    if (match != null) break
                }
                if (match == null) {
                    Log.d("CdaHdScraper", "No matching title found")
                    return@withContext embeds
                }
                val selectedUrl = if (season != null && episode != null) {
                    val showDoc = fetchDoc(match.url) ?: return@withContext embeds
                    findEpisodeUrl(showDoc, season, episode) ?: run {
                        Log.d("CdaHdScraper", "Requested episode not found")
                        return@withContext embeds
                    }
                } else {
                    match.url
                }

                val targetDoc = fetchDoc(selectedUrl) ?: return@withContext embeds
                embeds.addAll(parseEmbeds(targetDoc, selectedUrl))
                android.util.Log.d("CdaHdScraper", "Found ${embeds.size} embeds")
            } catch (e: CloudflareChallengeDetected) {
                throw e
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("CdaHdScraper", "Failed to get provider embeds", e)
            }
            embeds
        }

    // -------------------------------------------------------------------------
    // Private Helpers
    // -------------------------------------------------------------------------

    private fun parseSeasons(doc: Document): List<Season> {
        val seasons = mutableListOf<Season>()
        val seasonElements = doc.select("#seasons .se-c")

        seasonElements.forEach { seasonEl ->
            val seasonTitle = seasonEl.selectFirst(".se-t, .title")?.text()?.trim() ?: "Sezon"
            val episodesList = mutableListOf<EpisodeLink>()

            seasonEl.select(".episodios li, ul.episodes li").forEach { epLi ->
                val epLink = epLi.selectFirst("a[href]") ?: return@forEach
                val epHref = epLink.attr("href").trim()
                val epUrl = if (epHref.startsWith("http")) epHref else "${CdaHdConfig.BASE_URL}$epHref"
                val epNum = epLi.selectFirst(".numerando")?.text()?.trim().orEmpty()
                val epTitle = epLi.selectFirst(".episodiotitle a, .title")?.text()?.trim() ?: epNum

                episodesList.add(
                    EpisodeLink(
                        url = epUrl,
                        title = if (epNum.isNotBlank()) "[$epNum] $epTitle" else epTitle,
                    )
                )
            }

            if (episodesList.isNotEmpty()) {
                seasons.add(
                    Season(
                        name = seasonTitle,
                        episodes = episodesList,
                    )
                )
            }
        }
        return seasons
    }

    private fun findEpisodeUrl(
        doc: Document,
        season: Int,
        episode: Int,
    ): String? {
        val seasons = parseSeasons(doc)
        val targetSeason = seasons.find {
            EpisodeUtils.extractSeasonNumber(it.name) == season
        } ?: return null

        val targetEpisode = targetSeason.episodes.find {
            EpisodeUtils.extractEpisodeNumber(it.title) == episode
        } ?: return null

        return targetEpisode.url
    }

    private suspend fun parseEmbeds(doc: Document, pageUrl: String): List<EmbedLink> {
        val embeds = mutableListOf<EmbedLink>()

        doc.select("#playcontainer iframe, .source-box iframe, #dooplay_player_response iframe, .playex iframe, .movieplay iframe, #streaming .player2 iframe, #streaming .embed2 > [id^=player2] > a[href]").forEach { element ->
            val src = if (element.tagName() == "a") {
                element.attr("href").takeIf { it.isNotBlank() }
            } else {
                element.attr("src").takeIf { it.isNotBlank() }
                    ?: element.attr("data-src").takeIf { it.isNotBlank() }
            }
                ?: return@forEach
            val url = pageUrl.toHttpUrlOrNull()?.resolve(src)?.toString() ?: return@forEach
            val host = url.toHttpUrlOrNull()?.host ?: return@forEach
            val serverName = if (host == "chuckle-tube.com" || host.endsWith(".chuckle-tube.com")) "Voe" else host

            embeds.add(
                EmbedLink(
                    url = url,
                    serverName = serverName,
                    version = element.parent()?.selectFirst(".tit")?.text()?.trim().orEmpty(),
                    quality = "HD",
                    sourceWebsite = CdaHdConfig.DOMAIN,
                    isWebView = getExtractorForUrl(url, serverName) == null,
                )
            )
        }

        // 2. DooPlay player tabs: ul#playeroptionsul li
        val playerOptions = doc.select("ul#playeroptionsul li")
        playerOptions.forEach { li ->
            val postId = li.attr("data-post").trim()
            val nume = li.attr("data-nume").trim()
            val type = li.attr("data-type").trim()
            val serverTitle = li.selectFirst(".title")?.text()?.trim()
                ?: li.text().trim().ifEmpty { "Source $nume" }

            if (postId.isNotBlank() && nume.isNotBlank()) {
                val resolvedEmbedUrl = resolveDooPlayPlayerAjax(postId, nume, type, pageUrl)
                if (resolvedEmbedUrl != null) {
                    embeds.add(
                        EmbedLink(
                            url = resolvedEmbedUrl,
                            serverName = serverTitle,
                            version = "",
                            quality = "HD",
                            sourceWebsite = CdaHdConfig.DOMAIN,
                            isWebView = getExtractorForUrl(resolvedEmbedUrl, serverTitle) == null,
                        )
                    )
                }
            }
        }

        return embeds.distinctBy { it.url }
    }

    private suspend fun resolveDooPlayPlayerAjax(
        postId: String,
        nume: String,
        type: String,
        pageUrl: String,
    ): String? = withContext(Dispatchers.IO) {
        try {
            val body = FormBody.Builder()
                .add("action", "doo_player_ajax")
                .add("post", postId)
                .add("nume", nume)
                .add("type", type.ifBlank { "movie" })
                .build()

            val request = Request.Builder()
                .url("${CdaHdConfig.BASE_URL}${CdaHdConfig.PATH_AJAX}")
                .post(body)
                .header("Accept", "*/*")
                .header("Origin", CdaHdConfig.BASE_URL)
                .header("Referer", pageUrl)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("User-Agent", appSessionManager.getUserAgent())
                .build()

            val response = okHttpClient.newCall(request).execute()
            val (code, mitigated, htmlOrJson) = response.use {
                Triple(it.code, it.header("cf-mitigated").equals("challenge", ignoreCase = true), it.body.string())
            }
            if (mitigated || isCloudflareChallengePage(htmlOrJson)) {
                sessionManager.requestChallenge(request)
                throw CloudflareChallengeDetected(CdaHdConfig.DOMAIN, request.url.toString(), request = request)
            }
            if (code !in 200..299) {
                Log.w("CdaHdScraper", "Player request returned HTTP $code")
                return@withContext null
            }

            if (htmlOrJson.startsWith("{")) {
                val json = JSONObject(htmlOrJson)
                val embedUrl = json.optString("embed_url")
                if (embedUrl.isNotBlank()) return@withContext pageUrl.toHttpUrlOrNull()?.resolve(embedUrl)?.toString()
            }

            val iframeSrc = Jsoup.parse(htmlOrJson).selectFirst("iframe")?.attr("src")
            if (!iframeSrc.isNullOrBlank()) {
                return@withContext pageUrl.toHttpUrlOrNull()?.resolve(iframeSrc)?.toString()
            }
        } catch (e: CloudflareChallengeDetected) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("CdaHdScraper", "Player request failed", e)
        }
        null
    }

    private suspend fun fetchDoc(url: String): Document? {
        try {
            val request = Request.Builder()
                .url(url)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "pl-PL,pl;q=0.9,en-US;q=0.8,en;q=0.7")
                .header("User-Agent", appSessionManager.getUserAgent())
                .build()

            val response = okHttpClient.newCall(request).execute()
            val (code, mitigated, html) = response.use {
                Triple(it.code, it.header("cf-mitigated").equals("challenge", ignoreCase = true), it.body.string())
            }
            if (mitigated || isCloudflareChallengePage(html)) {
                sessionManager.requestChallenge(request)
                throw CloudflareChallengeDetected(CdaHdConfig.DOMAIN, url, request = request)
            }
            if (code !in 200..299) {
                android.util.Log.w("CdaHdScraper", "Provider returned HTTP $code")
                return null
            }

            return Jsoup.parse(html)
        } catch (e: CloudflareChallengeDetected) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("CdaHdScraper", "Provider page request failed", e)
            return null
        }
    }

    private fun titleMatches(
        source: String,
        target: String,
    ): Boolean {
        fun clean(s: String) =
            Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
                .replace(Regex("\\p{M}"), "")
                .replace('ł', 'l')
                .replace(Regex("[^a-z0-9 ]"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()

        val cSource = clean(source)
        val cTarget = clean(target)
        return cSource.isNotBlank() && cTarget.isNotBlank() &&
            (cSource.contains(cTarget) || cTarget.contains(cSource))
    }
}
