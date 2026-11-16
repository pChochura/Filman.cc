package com.pointlessapps.filman.ui.player

import com.pointlessapps.filman.ui.components.FilmanOverlayMenuItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LektorSubtitleMenuTest {
    @Test
    fun `subtitle options reflect the preference and send changes`() {
        val selected = mutableListOf<Boolean>()
        val options = lektorSubtitleMenuItems(false, selected::add)
            .filterIsInstance<FilmanOverlayMenuItem.Option>()
        assertEquals("lektor_show_subtitles", options.single { it.isSelected }.id)
        options.single { it.id == "lektor_hide_subtitles" }.onClick()
        assertEquals(listOf(true), selected)
        assertEquals(
            "lektor_hide_subtitles",
            lektorSubtitleMenuItems(true) {}.filterIsInstance<FilmanOverlayMenuItem.Option>()
                .single { it.isSelected }.id,
        )
    }

    @Test
    fun `voice only hides subtitles only when lektor is enabled without changing narration input`() {
        val state = PlayerState(selectedSubtitleUrl = "subtitle", rawSubtitleContent = "speech")
        assertTrue(state.showSubtitles)
        val voiceOnly = state.copy(isLektorEnabled = true, lektorHideSubtitles = true)
        assertFalse(voiceOnly.showSubtitles)
        assertEquals("subtitle", voiceOnly.selectedSubtitleUrl)
        assertEquals("speech", voiceOnly.rawSubtitleContent)
        assertTrue(voiceOnly.copy(isLektorEnabled = false).showSubtitles)
        assertTrue(voiceOnly.copy(lektorHideSubtitles = false).showSubtitles)
    }
}
