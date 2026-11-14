package com.pointlessapps.filman.data.scraper

import com.pointlessapps.filman.data.api.CloudflareChallengeDetected
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

class LocalLinkExtractorChallengeTest {
    @Test
    fun `only verified provider is retried and other links remain available`() = runBlocking {
        val verified = MutableStateFlow(0L)
        val zaluknij = mockk<ZaluknijSessionManager>(relaxed = true) {
            every { verificationGeneration } returns verified
            every { getCookie() } returns "cf_clearance=stale"
        }
        val cda = mockk<CdaHdSessionManager>(relaxed = true) {
            every { verificationGeneration } returns MutableStateFlow(0L)
        }
        val settings = mockk<SettingsManager> {
            every { sourcesPriorityFlow } returns MutableStateFlow(
                listOf(
                    SourcePriorityConfig("tmdb", true),
                    SourcePriorityConfig("zaluknij", true),
                    SourcePriorityConfig("cda_hd", false),
                    SourcePriorityConfig("ekino", false),
                    SourcePriorityConfig("filman", false),
                ),
            )
        }
        val tmdb = mockk<TmdbEmbedSource> {
            every { getEmbeds(any(), any(), any()) } returns listOf(
                EmbedLink("https://example.com/video", "Example", sourceWebsite = "tmdb"),
            )
        }
        val scraper = mockk<ZaluknijScraper>()
        val blocked = AtomicBoolean(true)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        coEvery { scraper.getEmbeds(any(), any(), any(), any(), any(), any(), any(), any()) } coAnswers {
            if (blocked.get()) {
                entered.complete(Unit)
                release.await()
                throw CloudflareChallengeDetected("zaluknij.cc")
            }
            ScraperMatchResult(null, embeds = listOf(EmbedLink("https://example.com/other", "Other", sourceWebsite = "zaluknij")))
        }
        val extractor = LocalLinkExtractor(
            filmanScraper = mockk(relaxed = true),
            zaluknijScraper = scraper,
            ekinoScraper = mockk(relaxed = true),
            cdaHdScraper = mockk(relaxed = true),
            tmdbEmbedSource = tmdb,
            sessionManager = mockk(relaxed = true) {
                every { verificationGeneration } returns MutableStateFlow(0L)
            },
            zaluknijSessionManager = zaluknij,
            cdaHdSessionManager = cda,
            settingsManager = settings,
            mediaAssociationManager = mockk<MediaAssociationManager>(relaxed = true),
        )
        val key = extractor.linkKey(1)
        extractor.startExtraction(1, "Example")
        withTimeout(5_000) { entered.await() }
        extractor.startExtraction(1, "Example", force = true)
        delay(50)
        coVerify(exactly = 1) { scraper.getEmbeds(any(), any(), any(), any(), any(), any(), any(), any()) }
        release.complete(Unit)
        withTimeout(5_000) {
            while (extractor.getLinks(key)?.status != "READY") delay(10)
        }
        assertEquals(listOf("zaluknij"), extractor.getLinks(key)?.challengeSources)
        assertFalse(extractor.shouldRetryForAuth(key))

        blocked.set(false)
        verified.value = 1
        assertTrue(extractor.shouldRetryForAuth(key))
        delay(50)
        extractor.startExtraction(1, "Example")
        withTimeout(5_000) {
            while (extractor.getLinks(key)?.links?.size != 2) delay(10)
        }
        assertFalse(extractor.shouldRetryForAuth(key))
        verify(exactly = 1) { tmdb.getEmbeds(any(), any(), any()) }
        coVerify(exactly = 2) { scraper.getEmbeds(any(), any(), any(), any(), any(), any(), any(), any()) }
    }
}
