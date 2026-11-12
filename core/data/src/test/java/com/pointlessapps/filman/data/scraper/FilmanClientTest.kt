package com.pointlessapps.filman.data.scraper

import android.webkit.CookieManager
import com.pointlessapps.filman.config.FilmanConfig
import com.pointlessapps.filman.data.local.SessionManager
import com.sun.net.httpserver.HttpServer
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.Runs
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class FilmanClientTest {

    private lateinit var server: HttpServer
    private lateinit var baseUrl: String
    private lateinit var cookieManager: CookieManager
    private lateinit var sessionManager: SessionManager
    private lateinit var isChallengeActiveFlow: MutableStateFlow<Boolean>
    private lateinit var isChallengeCancelledFlow: MutableStateFlow<Boolean>

    @Before
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.start()
        baseUrl = "http://127.0.0.1:${server.address.port}"

        cookieManager = mockk(relaxed = true)
        every { cookieManager.getCookie(any()) } returns ""

        mockkStatic(CookieManager::class)
        every { CookieManager.getInstance() } returns cookieManager

        isChallengeActiveFlow = MutableStateFlow(false)
        isChallengeCancelledFlow = MutableStateFlow(false)

        sessionManager = mockk(relaxed = true) {
            every { getUserAgent() } returns "TestAgent/1.0"
            every { getCookie() } returns null
            every { getCloudflareCookie() } returns null
            every { hasCookie() } returns false
            every { isChallengeActive } returns isChallengeActiveFlow
            every { isChallengeCancelled } returns isChallengeCancelledFlow
            every { clearCookie() } just Runs
        }
    }

    @After
    fun tearDown() {
        unmockkStatic(CookieManager::class)
        server.stop(0)
    }

    @Test
    fun `redirect to login with turnstile clears expired cookie and throws AuthException without Cloudflare challenge`() =
        runTest {
            every { sessionManager.hasCookie() } returns true
            every { sessionManager.getCookie() } returns "PHPSESSID=expired_session"

            server.createContext("/movies") { exchange ->
                exchange.responseHeaders.add("Location", "$baseUrl${FilmanConfig.LOGIN_PATH}")
                exchange.sendResponseHeaders(302, -1)
                exchange.close()
            }
            server.createContext(FilmanConfig.LOGIN_PATH) { exchange ->
                val html = """
                    <html>
                        <head><title>Logowanie</title></head>
                        <body>
                            <div class="cf-turnstile"></div>
                            <script src="https://challenges.cloudflare.com/turnstile/v0/api.js"></script>
                        </body>
                    </html>
                """.trimIndent().toByteArray()
                exchange.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
                exchange.sendResponseHeaders(200, html.size.toLong())
                exchange.responseBody.use { it.write(html) }
            }

            val client = FilmanClient(sessionManager)
            try {
                client.getDocument("$baseUrl/movies", passCookies = true)
                fail("Expected AuthException")
            } catch (e: AuthException) {
                assertTrue(e.message.orEmpty().contains(FilmanConfig.LOGIN_PATH))
            }

            verify(exactly = 1) { sessionManager.clearCookie() }
            verify(exactly = 0) { sessionManager.requestChallenge() }
        }

    @Test
    fun `403 Cloudflare response triggers challenge and retries after solution`() =
        runTest {
            val attempts = AtomicInteger(0)

            every { sessionManager.requestChallenge() } answers {
                isChallengeActiveFlow.value = true
                launch {
                    delay(50)
                    isChallengeActiveFlow.value = false
                }
            }

            server.createContext("/series") { exchange ->
                if (attempts.incrementAndGet() == 1) {
                    val html = "<html><head><title>Just a moment...</title></head><body>cf</body></html>"
                        .toByteArray()
                    exchange.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
                    exchange.responseHeaders.add("cf-mitigated", "challenge")
                    exchange.sendResponseHeaders(403, html.size.toLong())
                    exchange.responseBody.use { it.write(html) }
                } else {
                    val html = "<html><head><title>Filman</title></head><body>OK</body></html>"
                        .toByteArray()
                    exchange.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
                    exchange.sendResponseHeaders(200, html.size.toLong())
                    exchange.responseBody.use { it.write(html) }
                }
            }

            val client = FilmanClient(sessionManager)
            val doc = client.getDocument("$baseUrl/series", passCookies = false)

            assertEquals("Filman", doc.title())
            assertEquals(2, attempts.get())
            verify(exactly = 1) { sessionManager.requestChallenge() }
        }

    @Test
    fun `getEffectiveCookies prefers fresher cf_clearance and strips PHPSESSID when logged out`() =
        runTest {
            val capturedCookieHeader = AtomicReference<String?>()

            every { sessionManager.hasCookie() } returns true
            every { sessionManager.getCookie() } returns "PHPSESSID=sess_123; cf_clearance=stale_from_login"
            every { sessionManager.getCloudflareCookie() } returns "cf_clearance=fresher_cf"
            every { cookieManager.getCookie(FilmanConfig.BASE_URL) } returns "cf_clearance=newest_cm"

            server.createContext("/check") { exchange ->
                capturedCookieHeader.set(exchange.requestHeaders.getFirst("Cookie"))
                val html = "<html><head><title>OK</title></head><body></body></html>".toByteArray()
                exchange.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
                exchange.sendResponseHeaders(200, html.size.toLong())
                exchange.responseBody.use { it.write(html) }
            }

            val client = FilmanClient(sessionManager)
            client.getDocument("$baseUrl/check", passCookies = true)

            val loggedInCookies = capturedCookieHeader.get().orEmpty()
            assertTrue("Expected PHPSESSID to be present: $loggedInCookies", loggedInCookies.contains("PHPSESSID=sess_123"))
            assertTrue("Expected newest cf_clearance: $loggedInCookies", loggedInCookies.contains("cf_clearance=newest_cm"))
            assertFalse("Stale cf_clearance must not override newest: $loggedInCookies", loggedInCookies.contains("stale_from_login"))

            // When logged out, PHPSESSID leaked from WebView CookieManager must be stripped
            every { sessionManager.hasCookie() } returns false
            every { sessionManager.getCookie() } returns null
            every { sessionManager.getCloudflareCookie() } returns null
            every { cookieManager.getCookie(FilmanConfig.BASE_URL) } returns "PHPSESSID=stale_wv; cf_clearance=newest_cm"

            client.getDocument("$baseUrl/check", passCookies = true)

            val loggedOutCookies = capturedCookieHeader.get().orEmpty()
            assertFalse("PHPSESSID must be stripped when logged out: $loggedOutCookies", loggedOutCookies.contains("PHPSESSID="))
            assertTrue("cf_clearance must remain: $loggedOutCookies", loggedOutCookies.contains("cf_clearance=newest_cm"))
        }
}
