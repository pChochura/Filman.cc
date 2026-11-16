package com.pointlessapps.filman.player.lektor.engine

import com.pointlessapps.filman.player.lektor.model.LektorVoice
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemVoiceModelTest {
    @Test
    fun `named system voices round trip without changing the default voice`() {
        val id = LektorVoice.systemVoiceId("pl-PL-x-test-local")
        assertEquals(LektorVoice.SYSTEM, LektorVoice.fromId(id))
        assertEquals("pl-PL-x-test-local", LektorVoice.systemVoiceName(id))
        assertEquals(LektorVoice.SYSTEM, LektorVoice.fromId("system"))
        assertNull(LektorVoice.systemVoiceName("system"))
        assertEquals(LektorVoice.DEFAULT, LektorVoice.fromId("meski"))
    }

    @Test
    fun `system voice requires no downloaded model`() {
        val manager = LektorModelManager()
        assertTrue(manager.isModelReady(LektorVoice.SYSTEM))
        assertNull(manager.getModelPaths(LektorVoice.SYSTEM))
        assertFalse(manager.getDownloadedVoices().contains(LektorVoice.SYSTEM.id))
    }

    @Test
    fun `online voice requires no downloaded model`() {
        val manager = LektorModelManager()
        assertTrue(manager.isModelReady(LektorVoice.EDGE))
        assertNull(manager.getModelPaths(LektorVoice.EDGE))
        assertFalse(manager.getDownloadedVoices().contains(LektorVoice.EDGE.id))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `system voice cannot be deleted as a downloaded model`() {
        LektorModelManager().deleteModel(LektorVoice.SYSTEM)
    }
}
