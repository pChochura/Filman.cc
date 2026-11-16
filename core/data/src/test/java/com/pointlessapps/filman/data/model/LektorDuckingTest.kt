package com.pointlessapps.filman.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class LektorDuckingTest {
    @Test
    fun `legacy ducking levels map to audible settings`() {
        assertEquals(0.6f, LektorDucking.fromStored(0.12f))
        assertEquals(0.7f, LektorDucking.fromStored(0.22f))
        assertEquals(0.8f, LektorDucking.fromStored(0.35f))
        assertEquals(0.8f, LektorDucking.fromStored(0.75f))
        assertEquals(1.0f, LektorDucking.fromStored(LektorDucking.DEFAULT_VOLUME))
    }
}
