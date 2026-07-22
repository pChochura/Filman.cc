package com.example.filman.data.source

import com.example.filman.data.model.ActorDetails
import com.example.filman.data.model.DetailedMedia
import com.example.filman.data.model.FilterData
import com.example.filman.data.model.FilterOption
import com.example.filman.data.model.MovieItem
import com.example.filman.data.model.PageResult
import com.example.filman.data.model.Rating
import com.example.filman.data.model.SearchResults
import com.example.filman.data.source.obejrzyj.models.BootstrapData
import com.example.filman.data.source.obejrzyj.models.ObejrzyjMovie
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import org.jsoup.Jsoup

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

                if (category.config?.nestedLayout == "slider" || category.config?.nestedLayout == "carousel") {
                    featuredItems.addAll(items)
                } else {
                    movies.addAll(items)
                }
            }

            return PageResult(featuredItems = featuredItems, movies = movies, path = url)
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

    override suspend fun getMediaDetails(mediaUrl: String): DetailedMedia? {
        TODO("Implement when API is provided")
    }

    override suspend fun getCategories(): List<FilterOption> {
        TODO("Implement when API is provided")
    }
}
