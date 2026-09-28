package com.pointlessapps.filman.data.api

import com.pointlessapps.filman.data.local.SessionManager
import com.pointlessapps.filman.data.local.ZaluknijSessionManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FilmanApiTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var sessionManager: SessionManager
    private lateinit var zaluknijSessionManager: ZaluknijSessionManager
    private lateinit var filmanApi: FilmanApi

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        sessionManager = mockk(relaxed = true)
        zaluknijSessionManager = mockk(relaxed = true)

        every { sessionManager.getCookie() } returns "filman_session_cookie=123"
        every { zaluknijSessionManager.getCookie() } returns "zaluknij_cookie=456"
        every { sessionManager.getUserAgent() } returns "TestUserAgent/1.0"
        every { sessionManager.isChallengeActive } returns kotlinx.coroutines.flow.MutableStateFlow(false)

        NetworkClient.setCloudflareCookie("filman.cc", "cf_clearance=cf123")

        val client = OkHttpClient.Builder().build()
        filmanApi = FilmanApi(
            client = client,
            sessionManager = sessionManager,
            zaluknijSessionManager = zaluknijSessionManager,
        )

        // Reflection to point baseUrl to mockWebServer
        val baseUrlField = FilmanApi::class.java.getDeclaredField("baseUrl")
        baseUrlField.isAccessible = true
        baseUrlField.set(filmanApi, mockWebServer.url("").toString().removeSuffix("/"))
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `getMovieLinks throws AuthException when user not logged in on HTTP 401`() = runBlocking {
        every { sessionManager.getCookie() } returns null
        every { sessionManager.hasCookie() } returns false
        mockWebServer.enqueue(MockResponse().setResponseCode(401).setBody("""{"status":"NEEDS_AUTHORIZATION"}"""))
        var caught = false
        try {
            filmanApi.getMovieLinks(123)
        } catch (e: AuthException) {
            caught = true
        }
        assertTrue(caught)
    }

    @Test
    fun `getMovieLinks throws CloudflareChallengeDetected on NEEDS_AUTHORIZATION status when logged in without clearance`() = runBlocking {
        every { sessionManager.getCookie() } returns "filman_session_cookie=123"
        every { sessionManager.hasCookie() } returns true
        NetworkClient.setCloudflareCookie("filman.cc", "")
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"status":"NEEDS_AUTHORIZATION","links":[]}"""))
        var caught = false
        try {
            filmanApi.getMovieLinks(123)
        } catch (e: CloudflareChallengeDetected) {
            caught = true
        }
        assertTrue(caught)
    }

    @Test
    fun `getMovieLinks passes Cloudflare, Filman, Zaluknij, and UserAgent headers`() = runBlocking {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"status":"READY","links":[]}"""))
        val response = filmanApi.getMovieLinks(123)
        assertEquals("READY", response.status)

        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("filman_session_cookie=123", recordedRequest.getHeader("X-Filman-Cookie"))
        assertEquals("zaluknij_cookie=456", recordedRequest.getHeader("X-Zaluknij-Cookie"))
        assertEquals("cf_clearance=cf123", recordedRequest.getHeader("X-Cloudflare-Cookie"))
        assertEquals("cf_clearance=cf123", recordedRequest.getHeader("Cookie"))
        assertEquals("TestUserAgent/1.0", recordedRequest.getHeader("User-Agent"))
        assertEquals("TestUserAgent/1.0", recordedRequest.getHeader("X-User-Agent"))
    }
}
