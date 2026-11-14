package com.pointlessapps.filman.data.api

import android.webkit.CookieManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class NetworkClientCookieTest {
    private val manager = mockk<CookieManager>(relaxed = true)

    @Before
    fun setUp() {
        mockkStatic(CookieManager::class)
        every { CookieManager.getInstance() } returns manager
        every { manager.getCookie(any<String>()) } returns null
    }

    @After
    fun tearDown() {
        NetworkClient.setCloudflareCookie("provider.example", "")
        unmockkStatic(CookieManager::class)
    }

    @Test
    fun `request cookies come from the scoped cookie manager rather than legacy strings`() {
        NetworkClient.setCloudflareCookie("provider.example", "cf_clearance=secret")
        val jar = NetworkClient.okHttpClient.cookieJar
        assertTrue(jar.loadForRequest("https://provider.example.attacker.test/".toHttpUrl()).isEmpty())
        assertTrue(jar.loadForRequest("https://provider.example/".toHttpUrl()).isEmpty())

        every { manager.getCookie("https://provider.example/") } returns "cf_clearance=fresh"
        assertEquals("fresh", jar.loadForRequest("https://provider.example/".toHttpUrl()).single().value)
    }

    @Test
    fun `restoring a legacy cookie does not overwrite a newer browser cookie`() {
        every { manager.getCookie("https://provider.example") } returns "cf_clearance=fresh"
        NetworkClient.setCloudflareCookie("provider.example", "cf_clearance=stale")

        verify(exactly = 0) { manager.setCookie(any<String>(), any<String>()) }
    }

    @Test
    fun `request retains same named cookies selected by browser for different paths`() {
        every { manager.getCookie("https://provider.example/deep") } returns "session=deep; session=root"
        val cookies = NetworkClient.okHttpClient.cookieJar.loadForRequest("https://provider.example/deep".toHttpUrl())
        assertEquals(listOf("deep", "root"), cookies.map { it.value })
    }

    @Test
    fun `native verification uses scoped browser cookies and does not reuse expired legacy cookies`() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val destination = server.url("/verify")
            val client = NetworkClient.okHttpClient.newBuilder().addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().url(destination).build())
            }.build()
            val verifier = ChallengeVerifier(client)
            every { manager.getCookie(destination.toString()) } returns "cf_clearance=fresh"
            server.enqueue(MockResponse().setHeader("Content-Type", "text/html").setBody("<html>OK</html>"))
            assertTrue(verifier.verify("https://provider.example/verify", "TV agent"))
            val first = server.takeRequest()
            assertEquals("cf_clearance=fresh", first.getHeader("Cookie"))
            assertEquals("TV agent", first.getHeader("User-Agent"))

            NetworkClient.setCloudflareCookie("provider.example", "cf_clearance=expired")
            every { manager.getCookie(destination.toString()) } returns null
            server.enqueue(MockResponse().setResponseCode(403).setHeader("cf-mitigated", "challenge"))
            assertFalse(verifier.verify("https://provider.example/verify", "TV agent"))
            assertEquals(null, server.takeRequest().getHeader("Cookie"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `native verification does not write response cookies into browser session`() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val destination = server.url("/verify")
            val client = NetworkClient.okHttpClient.newBuilder().addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().url(destination).build())
            }.build()
            every { manager.getCookie(destination.toString()) } returns "__cf_bm=browser"
            server.enqueue(
                MockResponse()
                    .setResponseCode(403)
                    .setHeader("cf-mitigated", "challenge")
                    .addHeader("Set-Cookie", "__cf_bm=probe; Path=/")
                    .addHeader("Set-Cookie", "cf_clearance=probe; Path=/"),
            )
            assertFalse(ChallengeVerifier(client).verify("https://provider.example/verify", "TV agent"))
            assertEquals("__cf_bm=browser", server.takeRequest().getHeader("Cookie"))
            verify(exactly = 0) { manager.setCookie(any<String>(), any<String>()) }
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `native verification times out a stalled response`() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val client = NetworkClient.okHttpClient.newBuilder().addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().url(server.url("/verify")).build())
            }.build()
            val start = System.nanoTime()
            assertFalse(ChallengeVerifier(client).verify("https://provider.example/verify", "TV agent"))
            val elapsedMs = (System.nanoTime() - start) / 1_000_000
            assertTrue("Probe completed too early: ${elapsedMs}ms", elapsedMs >= 7_000)
            assertTrue("Probe was not bounded: ${elapsedMs}ms", elapsedMs < 15_000)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `cancelling native verification stops waiting for a stalled request`() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val client = NetworkClient.okHttpClient.newBuilder().addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().url(server.url("/verify")).build())
            }.build()
            val verification = async {
                ChallengeVerifier(client).verify("https://provider.example/verify", "TV agent")
            }
            val request = withContext(Dispatchers.IO) { server.takeRequest(3, TimeUnit.SECONDS) }
            assertNotNull(request)
            withTimeout(2_000) { verification.cancelAndJoin() }
            assertTrue(verification.isCancelled)
        } finally {
            server.shutdown()
        }
    }
}
