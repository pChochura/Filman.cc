package com.pointlessapps.filman.data.scraper

import com.pointlessapps.filman.data.api.CloudflareChallengeDetected
import com.pointlessapps.filman.data.api.FilmanRepository
import com.pointlessapps.filman.data.api.dto.Movie
import com.pointlessapps.filman.data.api.tmdb.TmdbApiClient
import com.pointlessapps.filman.data.local.CdaHdSessionManager
import com.pointlessapps.filman.data.local.MediaAssociationManager
import com.pointlessapps.filman.data.local.SessionManager
import com.pointlessapps.filman.data.local.SettingsManager
import com.pointlessapps.filman.data.local.ZaluknijSessionManager
import com.pointlessapps.filman.data.model.EmbedLink
import com.pointlessapps.filman.data.model.SourcePriorityConfig
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

class LocalLinkExtractorSourceSettingsTest {
    private class Fixture(cookie: String? = "session", zaluknijEnabled: Boolean = false) {
        val sources = MutableStateFlow(
            listOf(
                SourcePriorityConfig("tmdb", true),
                SourcePriorityConfig("zaluknij", zaluknijEnabled),
                SourcePriorityConfig("ekino", false),
                SourcePriorityConfig("cda_hd", false),
                SourcePriorityConfig("filman", false),
            ),
        )
        val filman = mockk<FilmanScraper>()
        val zaluknij = mockk<ZaluknijScraper>()
        val zaluknijGeneration = MutableStateFlow(0L)
        val tmdb = mockk<TmdbEmbedSource> {
            every { getEmbeds(any(), any(), any()) } returns listOf(
                EmbedLink("https://example.com/tmdb", "Example", sourceWebsite = "tmdb"),
            )
        }
        val session = mockk<SessionManager>(relaxed = true) {
            every { verificationGeneration } returns MutableStateFlow(0L)
            every { getCookie() } returns cookie
            every { hasCookie() } returns !cookie.isNullOrBlank()
        }
        val extractor = LocalLinkExtractor(
            filmanScraper = filman,
            zaluknijScraper = zaluknij,
            ekinoScraper = mockk(relaxed = true),
            cdaHdScraper = mockk(relaxed = true),
            tmdbEmbedSource = tmdb,
            sessionManager = session,
            zaluknijSessionManager = mockk<ZaluknijSessionManager>(relaxed = true) {
                every { verificationGeneration } returns zaluknijGeneration
            },
            cdaHdSessionManager = mockk<CdaHdSessionManager>(relaxed = true) {
                every { verificationGeneration } returns MutableStateFlow(0L)
            },
            settingsManager = mockk<SettingsManager> {
                every { sourcesPriorityFlow } returns sources
            },
            mediaAssociationManager = mockk<MediaAssociationManager>(relaxed = true),
        )
        val repository = FilmanRepository(
            tmdbApiClient = mockk<TmdbApiClient> {
                coEvery { getMovieDetails(1) } returns Movie(1, "Example", "")
            },
            localLinkExtractor = extractor,
        )
        val key = extractor.linkKey(1)

        init {
            coEvery { filman.getEmbeds(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
                ScraperMatchResult(null, embeds = listOf(
                    EmbedLink("https://example.com/filman", "Example", sourceWebsite = "filman.cc"),
                ))
        }

        fun enableFilman(enabled: Boolean) {
            sources.value = sources.value.map { if (it.name == "filman") it.copy(isEnabled = enabled) else it }
        }

        suspend fun awaitLinks(count: Int) = withTimeout(5_000) {
            while (extractor.getLinks(key)?.links?.size != count ||
                extractor.getLinks(key)?.status != "READY"
            ) delay(10)
        }
    }

    @Test
    fun `enabling retries a cached item without repeating other sources and disabling filters it`() = runBlocking {
        val fixture = Fixture()
        fixture.repository.getLinks("movie_1", isTvShow = false)
        fixture.awaitLinks(1)
        coVerify(exactly = 0) { fixture.filman.getEmbeds(any(), any(), any(), any(), any(), any(), any(), any(), any()) }

        fixture.enableFilman(true)
        assertTrue(fixture.extractor.shouldRetryForSettings(fixture.key))
        withTimeout(5_000) {
            while (fixture.extractor.shouldRetryForSettings(fixture.key)) {
                fixture.repository.getLinks("movie_1", isTvShow = false)
                delay(10)
            }
        }
        fixture.awaitLinks(2)
        verify(exactly = 1) { fixture.tmdb.getEmbeds(any(), any(), any()) }

        fixture.enableFilman(false)
        val disabled = fixture.extractor.getLinks(fixture.key)!!
        assertEquals(listOf("tmdb"), disabled.links.map { it.sourceWebsite })
        assertFalse(disabled.requiresFilmanAuth)
        assertFalse(fixture.extractor.shouldRetryForSettings(fixture.key))

        fixture.enableFilman(true)
        fixture.awaitLinks(2)
    }

    @Test
    fun `enabling during an extraction retries after the current attempt finishes`() = runBlocking {
        val fixture = Fixture(zaluknijEnabled = true)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        coEvery { fixture.zaluknij.getEmbeds(any(), any(), any(), any(), any(), any(), any(), any(), any()) } coAnswers {
            entered.complete(Unit)
            release.await()
            ScraperMatchResult(null)
        }
        fixture.extractor.startExtraction(1, "Example")
        withTimeout(5_000) { entered.await() }

        fixture.enableFilman(true)
        assertEquals("PENDING", fixture.extractor.startExtraction(1, "Example").status)
        coVerify(exactly = 0) { fixture.filman.getEmbeds(any(), any(), any(), any(), any(), any(), any(), any(), any()) }
        release.complete(Unit)
        withTimeout(5_000) {
            while (fixture.extractor.shouldRetryForSettings(fixture.key)) {
                fixture.extractor.startExtraction(1, "Example")
                delay(10)
            }
        }
        fixture.awaitLinks(2)
        coVerify(exactly = 1) { fixture.filman.getEmbeds(any(), any(), any(), any(), any(), any(), any(), any(), any()) }
        coVerify(exactly = 1) { fixture.zaluknij.getEmbeds(any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `disabling hides cached Filman authentication requirements`() = runBlocking {
        val fixture = Fixture(cookie = null)
        fixture.enableFilman(true)
        fixture.extractor.startExtraction(1, "Example")
        withTimeout(5_000) {
            while (fixture.extractor.getLinks(fixture.key)?.let {
                    it.requiresFilmanAuth && it.status == "READY"
                } != true
            ) delay(10)
        }
        fixture.enableFilman(false)
        val disabled = fixture.extractor.getLinks(fixture.key)!!
        assertEquals("READY", disabled.status)
        assertFalse(disabled.requiresFilmanAuth)
        assertFalse(fixture.extractor.shouldRetryForAuth(fixture.key))
    }

    @Test
    fun `disabling during Filman extraction does not start a new challenge`() = runBlocking {
        val fixture = Fixture()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        coEvery { fixture.filman.getEmbeds(any(), any(), any(), any(), any(), any(), any(), any(), any()) } coAnswers {
            entered.complete(Unit)
            release.await()
            throw CloudflareChallengeDetected("filman.cc")
        }
        fixture.enableFilman(true)
        fixture.extractor.startExtraction(1, "Example")
        withTimeout(5_000) { entered.await() }
        fixture.enableFilman(false)
        release.complete(Unit)
        withTimeout(5_000) {
            while (fixture.extractor.getLinks(fixture.key)?.let {
                    it.status == "READY" && it.completedSources == it.totalSources
                } != true
            ) delay(10)
        }
        val response = fixture.extractor.getLinks(fixture.key)!!
        assertTrue(response.challengeSources.isEmpty())
        assertFalse(response.requiresCloudflare)
        verify(exactly = 0) { fixture.session.requestChallenge(any()) }
    }

    @Test
    fun `retrying another provider while Filman is disabled marks it for a later retry`() = runBlocking {
        val fixture = Fixture(zaluknijEnabled = true)
        val blocked = AtomicBoolean(true)
        coEvery { fixture.zaluknij.getEmbeds(any(), any(), any(), any(), any(), any(), any(), any(), any()) } coAnswers {
            if (blocked.get()) throw CloudflareChallengeDetected("zaluknij.cc")
            ScraperMatchResult(null)
        }
        fixture.enableFilman(true)
        fixture.extractor.startExtraction(1, "Example")
        withTimeout(5_000) {
            while (fixture.extractor.getLinks(fixture.key)?.let {
                    it.links.size == 2 && "zaluknij" in it.challengeSources
                } != true
            ) delay(10)
        }

        fixture.enableFilman(false)
        fixture.zaluknijGeneration.value = 1
        blocked.set(false)
        withTimeout(5_000) {
            while (fixture.extractor.shouldRetryForAuth(fixture.key)) {
                fixture.extractor.startExtraction(1, "Example")
                delay(10)
            }
        }
        fixture.awaitLinks(1)

        fixture.enableFilman(true)
        assertTrue(fixture.extractor.shouldRetryForSettings(fixture.key))
        withTimeout(5_000) {
            while (fixture.extractor.shouldRetryForSettings(fixture.key)) {
                fixture.extractor.startExtraction(1, "Example")
                delay(10)
            }
        }
        fixture.awaitLinks(2)
    }
}
