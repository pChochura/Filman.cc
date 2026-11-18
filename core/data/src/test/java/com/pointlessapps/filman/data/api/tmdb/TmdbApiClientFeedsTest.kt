package com.pointlessapps.filman.data.api.tmdb

import com.pointlessapps.filman.data.model.MediaType
import com.pointlessapps.filman.data.model.TmdbFeed
import com.pointlessapps.filman.data.model.TrendingWindow
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
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
}
