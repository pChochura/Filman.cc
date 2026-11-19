package com.pointlessapps.filman.data.api.tmdb

import com.pointlessapps.filman.data.model.DecadeOption
import com.pointlessapps.filman.data.model.MediaFilterState
import com.pointlessapps.filman.data.model.MediaType
import com.pointlessapps.filman.data.model.SortOption
import com.pointlessapps.filman.data.model.TmdbFeed
import com.pointlessapps.filman.data.model.TrendingWindow
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale
import java.util.concurrent.TimeUnit

class TmdbApiClientFeedsTest {
    private fun clientFor(server: MockWebServer) = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request()
            val url = server.url(request.url.encodedPath).newBuilder()
                .encodedQuery(request.url.encodedQuery)
                .build()
            chain.proceed(request.newBuilder().url(url).build())
        }
        .build()

    private fun pageJson(count: Int, mediaType: String = "movie"): String {
        val results = (1..count).joinToString(",") { i ->
            """
            {
              "id": ${100 + i},
              "title": "Item $i",
              "overview": "Overview $i",
              "poster_path": "/poster$i.jpg",
              "media_type": "$mediaType"
            }
            """.trimIndent()
        }
        return """{"page":1,"total_pages":2,"total_results":$count,"results":[$results]}"""
    }

    @Test
    fun `TrendingAll hits weekly trending endpoint with device language and include_adult=false`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(pageJson(2)))
            val localeProvider = TmdbLocaleProvider { Locale.Builder().setLanguage("pl").setRegion("PL").build() }
            val api = TmdbApiClient(clientFor(server), localeProvider)

            val response = api.fetchFeed(TmdbFeed.TrendingAll(TrendingWindow.WEEK), page = 1)

            val url = server.takeRequest(5, TimeUnit.SECONDS)!!.requestUrl!!
            assertEquals("/3/trending/all/week", url.encodedPath)
            assertEquals("pl-PL", url.queryParameter("language"))
            assertEquals("false", url.queryParameter("include_adult"))
            assertEquals("1", url.queryParameter("page"))
            assertEquals(2, response.results.size)
        }
    }

    @Test
    fun `MoviesNowPlaying passes device country as region`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(pageJson(3)))
            val localeProvider = TmdbLocaleProvider { Locale.Builder().setLanguage("pl").setRegion("PL").build() }
            val api = TmdbApiClient(clientFor(server), localeProvider)

            api.fetchFeed(TmdbFeed.MoviesNowPlaying, page = 2)

            val url = server.takeRequest(5, TimeUnit.SECONDS)!!.requestUrl!!
            assertEquals("/3/movie/now_playing", url.encodedPath)
            assertEquals("pl-PL", url.queryParameter("language"))
            assertEquals("PL", url.queryParameter("region"))
            assertEquals("2", url.queryParameter("page"))
        }
    }

    @Test
    fun `KidsPopularMovies uses local PL cert and falls back to US PG when sparse`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(pageJson(2))) // < 5 results on PL
            server.enqueue(MockResponse().setBody(pageJson(6))) // fallback US response
            val localeProvider = TmdbLocaleProvider { Locale.Builder().setLanguage("pl").setRegion("PL").build() }
            val api = TmdbApiClient(clientFor(server), localeProvider)

            val response = api.fetchFeed(TmdbFeed.KidsPopularMovies, page = 1)

            val firstUrl = server.takeRequest(5, TimeUnit.SECONDS)!!.requestUrl!!
            assertEquals("/3/discover/movie", firstUrl.encodedPath)
            assertEquals("PL", firstUrl.queryParameter("certification_country"))
            assertEquals("12", firstUrl.queryParameter("certification.lte"))
            assertEquals("16|10751", firstUrl.queryParameter("with_genres"))
            assertEquals("false", firstUrl.queryParameter("include_adult"))

            val fallbackUrl = server.takeRequest(5, TimeUnit.SECONDS)!!.requestUrl!!
            assertEquals("/3/discover/movie", fallbackUrl.encodedPath)
            assertEquals("US", fallbackUrl.queryParameter("certification_country"))
            assertEquals("PG", fallbackUrl.queryParameter("certification.lte"))
            assertEquals(6, response.results.size)
        }
    }

    @Test
    fun `DiscoverByGenre maps movie Action genre to TV Action and Adventure`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(pageJson(2, mediaType = "tv")))
            val localeProvider = TmdbLocaleProvider { Locale.US }
            val api = TmdbApiClient(clientFor(server), localeProvider)

            api.fetchFeed(TmdbFeed.DiscoverByGenre(MediaType.TV, 28), page = 1)

            val url = server.takeRequest(5, TimeUnit.SECONDS)!!.requestUrl!!
            assertEquals("/3/discover/tv", url.encodedPath)
            assertEquals("10759", url.queryParameter("with_genres"))
            assertEquals("false", url.queryParameter("include_adult"))
        }
    }

    @Test
    fun `DiscoverFiltered builds movie discover URL with genres, sort, decade, rating, and region`() = runBlocking {
        MockWebServer().use { server ->
            val json = """
                {
                  "page": 1,
                  "total_pages": 3,
                  "total_results": 1,
                  "results": [
                    {
                      "id": 42,
                      "title": "Sci-Fi Action",
                      "overview": "Overview",
                      "poster_path": "/p.jpg",
                      "genre_ids": [28, 878],
                      "vote_count": 512
                    }
                  ]
                }
            """.trimIndent()
            server.enqueue(MockResponse().setBody(json))
            val localeProvider = TmdbLocaleProvider { Locale.Builder().setLanguage("pl").setRegion("PL").build() }
            val api = TmdbApiClient(clientFor(server), localeProvider)

            val filter = MediaFilterState(
                selectedGenreIds = listOf(28, 878),
                decade = DecadeOption.YEAR_2010S,
                minRating = 7.0f,
                sortBy = SortOption.RATING_DESC,
            )
            val response = api.fetchFeed(TmdbFeed.DiscoverFiltered(MediaType.MOVIE, filter), page = 2)

            val url = server.takeRequest(5, TimeUnit.SECONDS)!!.requestUrl!!
            assertEquals("/3/discover/movie", url.encodedPath)
            assertEquals("28,878", url.queryParameter("with_genres"))
            assertEquals("vote_average.desc", url.queryParameter("sort_by"))
            assertEquals("7.0", url.queryParameter("vote_average.gte"))
            assertEquals("50", url.queryParameter("vote_count.gte"))
            assertEquals("2010-01-01", url.queryParameter("primary_release_date.gte"))
            assertEquals("2019-12-31", url.queryParameter("primary_release_date.lte"))
            assertEquals("PL", url.queryParameter("region"))
            assertEquals("false", url.queryParameter("include_adult"))
            assertEquals("2", url.queryParameter("page"))
            assertEquals(listOf(28, 878), response.results.single().genreIds)
            assertEquals(512, response.results.single().voteCount)
        }
    }

    @Test
    fun `DiscoverFiltered builds TV discover URL with first_air_date sort, mapped genres, and no region`() =
        runBlocking {
            MockWebServer().use { server ->
                server.enqueue(MockResponse().setBody(pageJson(1, mediaType = "tv")))
                val localeProvider = TmdbLocaleProvider { Locale.US }
                val api = TmdbApiClient(clientFor(server), localeProvider)

                val filter = MediaFilterState(
                    selectedGenreIds = listOf(28),
                    decade = DecadeOption.YEAR_2024_PLUS,
                    sortBy = SortOption.RELEASE_DATE_DESC,
                )
                api.fetchFeed(TmdbFeed.DiscoverFiltered(MediaType.TV, filter), page = 1)

                val url = server.takeRequest(5, TimeUnit.SECONDS)!!.requestUrl!!
                assertEquals("/3/discover/tv", url.encodedPath)
                assertEquals("10759", url.queryParameter("with_genres"))
                assertEquals("first_air_date.desc", url.queryParameter("sort_by"))
                assertEquals("2024-01-01", url.queryParameter("first_air_date.gte"))
                assertEquals(TmdbUrlBuilder.todayIsoDate(), url.queryParameter("first_air_date.lte"))
                assertEquals("20", url.queryParameter("vote_count.gte"))
                assertNull(url.queryParameter("region"))
            }
        }
}
