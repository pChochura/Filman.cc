package com.pointlessapps.filman.data.api

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.FormBody
import okhttp3.Request
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.MediaType.Companion.toMediaType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChallengeVerifierTest {
    private fun verifier(code: Int = 200, body: String = "<html>Provider</html>", challenge: Boolean = false): ChallengeVerifier {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("test")
                .apply { if (challenge) header("cf-mitigated", "challenge") }
                .body(body.toResponseBody("text/html".toMediaType()))
                .build()
        }.build()
        return ChallengeVerifier(client)
    }

    @Test
    fun `only an actual successful provider response verifies`() = runTest {
        assertTrue(verifier().verify("https://example.com/", "test"))
        assertFalse(verifier(challenge = true).verify("https://example.com/", "test"))
        assertFalse(verifier(code = 403).verify("https://example.com/", "test"))
        assertFalse(verifier(code = 302).verify("https://example.com/", "test"))
        assertFalse(verifier(body = "<title>Just a moment...</title>").verify("https://example.com/", "test"))
        assertFalse(verifier(body = "").verify("https://example.com/", "test"))
        assertFalse(verifier().verify("http://example.com/", "test"))
    }

    @Test
    fun `ordinary cloudflare scripts do not make a provider page a challenge`() = runTest {
        val page = """<html><head><title>Provider</title></head><body>
            <script src="/cdn-cgi/challenge-platform/scripts/normal.js"></script>
            <main>Provider content</main></body></html>"""
        assertTrue(verifier(body = page).verify("https://example.com/", "test"))
        assertFalse(verifier(body = page, challenge = true).verify("https://example.com/", "test"))
    }

    @Test
    fun `challenge structures are rejected without a challenge header`() = runTest {
        val pages = listOf(
            """<html><head><title>Just a moment...</title></head></html>""",
            """<html><body><form id="challenge-form"></form></body></html>""",
            """<html><body><div id="cf-browser-verification"></div></body></html>""",
            """<html><script>window._cf_chl_opt={};</script><script src="/cdn-cgi/challenge-platform/x.js"></script></html>""",
        )
        for (page in pages) {
            assertFalse(verifier(body = page).verify("https://example.com/", "test"))
        }
    }

    @Test
    fun `verification retains a safe search request but removes its stale cookie`() = runTest {
        var observed: Request? = null
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            observed = chain.request()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK")
                .body("<html>results</html>".toResponseBody("text/html".toMediaType()))
                .build()
        }.build()
        val request = Request.Builder().url("https://provider.example/search")
            .post(FormBody.Builder().add("phrase", "example").build())
            .header("Cookie", "cf_clearance=stale").build()
        val safeRequest = challengeVerificationRequest(request, "provider.example", "/search")!!

        assertTrue(ChallengeVerifier(client).verify(safeRequest, "test"))
        assertEquals("POST", observed?.method)
        assertEquals("/search", observed?.url?.encodedPath)
        assertNull(observed?.header("Cookie"))
        assertNull(challengeVerificationRequest(request, "other.example", "/search"))
        assertNull(challengeVerificationRequest(request, "provider.example", "/other"))
        assertNull(challengeVerificationRequest(
            Request.Builder().url("https://provider.example.attacker.test/").build(),
            "provider.example",
        ))
        assertNull(challengeVerificationRequest(
            Request.Builder().url("http://provider.example/").build(),
            "provider.example",
        ))
    }
}
