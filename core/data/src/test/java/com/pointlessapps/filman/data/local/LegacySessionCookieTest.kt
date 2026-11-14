package com.pointlessapps.filman.data.local

import org.junit.Assert.assertEquals
import org.junit.Test

class LegacySessionCookieTest {
    @Test
    fun `restoring legacy login cookies does not revive expired clearance cookies`() {
        assertEquals(
            "session=abc; theme=dark",
            legacySessionWithoutClearance("session=abc; cf_clearance=expired; __cf_bm=old; theme=dark"),
        )
        assertEquals("", legacySessionWithoutClearance("cf_clearance=expired; __cf_bm=old"))
    }
}
