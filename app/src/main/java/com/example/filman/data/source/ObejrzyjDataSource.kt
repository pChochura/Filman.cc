package com.example.filman.data.source

import com.example.filman.data.model.ActorDetails
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
import com.example.filman.data.model.PageResult
import com.example.filman.data.model.Rating
import com.example.filman.data.model.SearchResults
import com.example.filman.data.model.Season
import com.example.filman.data.model.TagInfo
import com.example.filman.data.source.obejrzyj.models.BootstrapData
import com.example.filman.data.source.obejrzyj.models.ObejrzyjDetailsResponse
import com.example.filman.data.source.obejrzyj.models.ObejrzyjEpisodesContainer
import com.example.filman.data.source.obejrzyj.models.ObejrzyjMovie
import com.example.filman.data.source.obejrzyj.models.ObejrzyjRelatedResponse
import com.example.filman.data.source.obejrzyj.models.ObejrzyjSeasonEpisodesResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import org.jsoup.Jsoup
import kotlin.time.Duration.Companion.minutes

internal class ObejrzyjDataSource(
    private val httpClient: HttpClient,
) : ContentSource {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun getFilters(path: String): FilterData {
        TODO("Implement when API is provided")
    }

    override suspend fun getCategoryPage(path: String, page: Int): PageResult {
        val url =
            if (path.isEmpty() || path == "/") "https://obejrzyj.to/" else "https://obejrzyj.to$path"
        val html = httpClient.get(url).bodyAsText()

        val doc = Jsoup.parse(html)
        val scriptContent =
            doc.select("script").find { it.data().contains("window.bootstrapData") }?.data()

        if (scriptContent != null) {
            val jsonString =
                scriptContent.substringAfter("window.bootstrapData = ").substringBeforeLast(";")
            val bootstrapData = json.decodeFromString<BootstrapData>(jsonString)

            val categories =
                bootstrapData.loaders?.channelPage?.channel?.content?.data ?: emptyList()

            val featuredItems = mutableListOf<MovieItem>()
            val movies = mutableListOf<MovieItem>()

            for (category in categories) {
                val items = category.content?.data?.mapNotNull { it.toMovieItem() } ?: emptyList()
                if (items.isEmpty()) continue

                if (category.slug == "slider") {
                    featuredItems.addAll(items)
                } else {
                    movies.addAll(items)
                }
            }

            return PageResult(
                featuredItems = featuredItems,
                movies = movies.distinctBy { it.url },
                path = url,
            )
        }

        return PageResult(featuredItems = emptyList(), movies = emptyList(), path = url)
    }

    private fun ObejrzyjMovie.toMovieItem(): MovieItem? {
        val movieId = id?.toString() ?: return null
        return MovieItem(
            url = "https://obejrzyj.to/title/$movieId",
            titlePl = name ?: "Unknown",
            titleEn = null,
            filmanRating = rating?.let { Rating(it.toFloat(), 10f) },
            imdbRating = null,
            posterUrl = poster ?: "",
            backgroundUrl = backdrop,
            description = description ?: "",
            routeToken = movieId,
            seriesUrl = if (is_series == true) "https://obejrzyj.to/title/$movieId" else null,
            seasons = null,
        )
    }

    override suspend fun searchMovies(query: String): SearchResults {
        TODO("Implement when API is provided")
    }

    override suspend fun getActorDetails(actorUrl: String): ActorDetails? {
        TODO("Implement when API is provided")
    }

    override suspend fun getMediaDetails(mediaUrl: String): DetailedMedia? = coroutineScope {
        if (mediaUrl.contains("/watch/")) {
            val idString = mediaUrl.substringAfterLast("/").substringBefore("?")
            val videoId =
                idString.takeWhile { it.isDigit() }.toIntOrNull() ?: return@coroutineScope null

            val resp = httpClient.get("https://obejrzyj.to/api/v1/watch/$videoId") {
                header("referer", "https://obejrzyj.to/")
            }.bodyAsText()

            val watchData = try {
                json.decodeFromString<com.example.filman.data.source.obejrzyj.models.ObejrzyjWatchResponse>(
                    resp,
                )
            } catch (e: Exception) {
                null
            }

            val videoList =
                mutableListOf<com.example.filman.data.source.obejrzyj.models.ObejrzyjVideo>()
            watchData?.video?.let { videoList.add(it) }
            watchData?.alternative_videos?.let { videoList.addAll(it) }

            val embeds = videoList.mapNotNull { v ->
                if (v.src == null || v.name == null) return@mapNotNull null
                EmbedLink(
                    url = v.src,
                    serverName = v.name,
                    quality = v.quality ?: "",
                    version = v.language_type ?: "",
                )
            }

            val baseItem = MovieItem(
                url = mediaUrl,
                titlePl = "Odcinek",
                posterUrl = "",
                routeToken = videoId.toString(),
            )

            return@coroutineScope DetailedMedia(
                baseItem = baseItem,
                embeds = embeds,
            )
        }

        val idString = mediaUrl.substringAfterLast("/")
        val id = idString.takeWhile { it.isDigit() }.toIntOrNull() ?: return@coroutineScope null

        val detailsDef = async {
            val response = httpClient.get("https://obejrzyj.to/api/v1/titles/$id") {
                header("referer", "https://obejrzyj.to/")
            }.bodyAsText()
            json.decodeFromString<ObejrzyjDetailsResponse>(response)
        }

        val relatedDef = async {
            val response = httpClient.get("https://obejrzyj.to/api/v1/titles/$id/related") {
                header("referer", "https://obejrzyj.to/")
            }.bodyAsText()
            json.decodeFromString<ObejrzyjRelatedResponse>(response)
        }

        val details = detailsDef.await()
        val related = relatedDef.await()

        val t = details.title ?: return@coroutineScope null

        val allEpisodes = details.episodes?.data?.toMutableList() ?: mutableListOf()
        val knownSeasons = allEpisodes.mapNotNull { it.season_number }.distinct()

        val allSeasonNumbers = t.season_numbers_with_episodes.takeIf { it.isNotEmpty() }
            ?: if ((t.seasons_count ?: 0) > 0) (1..(t.seasons_count ?: 1)).toList() else emptyList()

        val missingSeasonNumbers = allSeasonNumbers.filter { it !in knownSeasons }

        val deferredEpisodes = missingSeasonNumbers.map { seasonNum ->
            async {
                try {
                    val resp = httpClient.get("https://obejrzyj.to/api/v1/titles/${t.id}/seasons/$seasonNum/episodes") {
                        header("referer", "https://obejrzyj.to/")
                    }.bodyAsText()
                    json.decodeFromString<ObejrzyjSeasonEpisodesResponse>(resp).pagination?.data ?: emptyList()
                } catch (e: Exception) {
                    emptyList()
                }
            }
        }

        deferredEpisodes.forEach { def ->
            allEpisodes.addAll(def.await())
        }

        val mappedSeasons = allSeasonNumbers.mapNotNull { seasonNum ->
            val seasonEpisodes =
                allEpisodes.filter { it.season_number == seasonNum }.mapNotNull { e ->
                    val videoId = e.primary_video?.id ?: e.id ?: return@mapNotNull null
                    val epTitle = e.name ?: "Odcinek ${e.episode_number}"
                    EpisodeLink(
                        title = epTitle,
                        url = "https://obejrzyj.to/watch/$videoId",
                    )
                }

            if (seasonEpisodes.isEmpty()) return@mapNotNull null

            Season(
                name = "Sezon $seasonNum",
                episodes = seasonEpisodes,
            )
        }

        val baseItem = MovieItem(
            url = "https://obejrzyj.to/titles/${t.id}",
            titlePl = t.name ?: "Unknown",
            titleEn = null,
            filmanRating = t.rating?.let {
                Rating(
                    it.toFloat(),
                    10f,
                )
            },
            imdbRating = null,
            posterUrl = t.poster ?: "",
            backgroundUrl = t.backdrop,
            description = t.description ?: "",
            routeToken = t.id?.toString(),
            seriesUrl = if (t.is_series == true) "https://obejrzyj.to/titles/${t.id}" else null,
            seasons = mappedSeasons,
        )

        val embeds = t.videos.mapNotNull { v ->
            if (v.src == null || v.name == null) return@mapNotNull null
            EmbedLink(
                url = v.src,
                serverName = v.name,
                quality = v.quality ?: "",
                version = v.language_type ?: "",
            )
        }

        val categories = t.genres.mapNotNull { g ->
            if (g.display_name == null) return@mapNotNull null
            CategoryInfo(name = g.display_name, url = "/browse?genre=${g.name}", id = g.id ?: 0)
        }

        val tags = t.keywords.mapNotNull { k ->
            if (k.display_name == null) return@mapNotNull null
            TagInfo(name = k.display_name, url = "/keyword/${k.name}")
        }

        val metaInfo = MediaMetadata(
            year = t.year,
            views = t.views,
            duration = t.runtime?.minutes,
            countries = t.production_countries.mapNotNull { it.display_name },
        )

        val actors = mutableListOf<ActorInfo>()
        details.credits?.directing?.forEach { p ->
            actors.add(
                ActorInfo(
                    role = ActorRole.DIRECTOR,
                    name = p.name ?: "Unknown",
                    avatarUrl = p.poster,
                    url = "https://obejrzyj.to/people/${p.id}",
                ),
            )
        }
        details.credits?.writing?.forEach { p ->
            actors.add(
                ActorInfo(
                    role = ActorRole.WRITER,
                    name = p.name ?: "Unknown",
                    avatarUrl = p.poster,
                    url = "https://obejrzyj.to/people/${p.id}",
                ),
            )
        }
        details.credits?.actors?.forEach { p ->
            actors.add(
                ActorInfo(
                    role = ActorRole.ACTOR,
                    name = p.name ?: "Unknown",
                    avatarUrl = p.poster,
                    url = "https://obejrzyj.to/people/${p.id}",
                ),
            )
        }

        val similarMovies = related.titles.mapNotNull { it.toMovieItem() }

        DetailedMedia(
            baseItem = baseItem,
            embeds = embeds,
            categories = categories,
            tags = tags,
            metaInfo = metaInfo,
            actors = actors,
            similarMovies = similarMovies,
        )
    }

    override suspend fun getCategories(): List<FilterOption> {
        TODO("Implement when API is provided")
    }
}
