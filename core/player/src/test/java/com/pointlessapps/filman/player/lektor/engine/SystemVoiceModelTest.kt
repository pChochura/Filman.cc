package com.pointlessapps.filman.player.lektor.engine

import com.pointlessapps.filman.player.lektor.model.LektorVoice
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemVoiceModelTest {
    @Test
    fun `system voice requires no downloaded model`() {
        val manager = LektorModelManager()
        assertTrue(manager.isModelReady(LektorVoice.SYSTEM))
        assertNull(manager.getModelPaths(LektorVoice.SYSTEM))
        assertFalse(manager.getDownloadedVoices().contains(LektorVoice.SYSTEM.id))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `system voice cannot be deleted as a downloaded model`() {
        LektorModelManager().deleteModel(LektorVoice.SYSTEM)
    }
}
