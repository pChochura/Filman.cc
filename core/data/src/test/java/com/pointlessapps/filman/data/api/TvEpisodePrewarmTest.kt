package com.pointlessapps.filman.data.api

import com.pointlessapps.filman.data.api.dto.LinksResponse
import com.pointlessapps.filman.data.api.tmdb.TmdbApiClient
import com.pointlessapps.filman.data.scraper.LocalLinkExtractor
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class TvEpisodePrewarmTest {
    @Test
    fun `episode extraction starts while season details are still loading and retries reuse metadata`() = runBlocking {
        val releaseSeason = CountDownLatch(1)
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return if (request.path!!.contains("/season/")) {
                    if (!releaseSeason.await(5, TimeUnit.SECONDS)) {
                        MockResponse().setResponseCode(500)
                    } else {
                        MockResponse().setBody(
                            """{"name":"Season 1","episodes":[{"id":10,"name":"Episode 1","episode_number":1}]}""",
                        )
                    }
                } else {
                    MockResponse().setBody(
                        """{"id":1,"name":"Example","original_name":"Original","first_air_date":"2020-01-01","seasons":[{"season_number":1}]}""",
                    )
                }
            }
        }
        server.start()
        try {
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                val original = chain.request()
                val destination = server.url(original.url.encodedPath).newBuilder()
                    .encodedQuery(original.url.encodedQuery)
                    .build()
                chain.proceed(original.newBuilder().url(destination).build())
            }.build()
            val started = CompletableDeferred<Unit>()
            val extractor = mockk<LocalLinkExtractor>(relaxed = true)
            every {
                extractor.startExtraction(1, "Example", "Original", 2020, true, 1, 1, any())
            } answers {
                started.complete(Unit)
                LinksResponse(status = "PENDING")
            }
            val repository = FilmanRepository(TmdbApiClient(client), extractor)

            val details = async { repository.getMediaDetails("show_1_1_1") }
            withTimeout(3_000) { started.await() }
            assertFalse(details.isCompleted)
            releaseSeason.countDown()
            val result = withTimeout(3_000) { details.await() }
            assertEquals("Episode 1", result?.baseItem?.episodeTitle)

            repository.getMediaDetails("show_1_1_1")
            repository.getMediaDetails("show_1")
            repository.getLinks("show_1_1_1", isTvShow = true, force = true)
            assertEquals(2, server.requestCount)
            verify(exactly = 3) {
                extractor.startExtraction(1, "Example", "Original", 2020, true, 1, 1, any())
            }
        } finally {
            releaseSeason.countDown()
            server.shutdown()
        }
    }
}
