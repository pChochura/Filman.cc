package com.pointlessapps.filman.ui.player

import com.pointlessapps.filman.core.ui.R
import com.pointlessapps.filman.data.model.LektorDucking
import com.pointlessapps.filman.data.model.LektorTiming
import com.pointlessapps.filman.player.lektor.engine.SystemVoiceList
import com.pointlessapps.filman.player.lektor.model.LektorVoice
import com.pointlessapps.filman.ui.components.FilmanOverlayMenuItem
import com.pointlessapps.filman.ui.core.TextValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LektorSettingsMenuTest {

    @Test
    fun `lektor voice catalog contains exactly two local models`() {
        val localModels = LektorVoice.entries.filter { it.requiresModel }
        assertEquals(2, localModels.size)
        assertEquals(listOf(LektorVoice.MESKI, LektorVoice.DARKMAN), localModels)
    }

    @Test
    fun `lektor menu creates nested menus for online models, local models, timing and ducking`() {
        val selectedVoice = mutableListOf<String>()
        val toggled = mutableListOf<Boolean>()
        val menu = buildLektorNestedMenu(
            isLektorEnabled = true,
            lektorVoiceId = "meski",
            downloadedVoices = setOf("meski"),
            downloadProgress = null,
            isExtracting = false,
            systemVoices = SystemVoiceList(loading = false, names = emptyList()),
            lektorHideSubtitles = false,
            lektorTimingOffsetMs = LektorTiming.DEFAULT_OFFSET_MS,
            lektorDuckingVolume = LektorDucking.DEFAULT_VOLUME,
            onToggleLektor = toggled::add,
            onSelectLektorVoice = selectedVoice::add,
            onDownloadLektorModel = {},
            onDeleteLektorModel = {},
            onPreviewSystemVoice = {},
            onRefreshSystemVoices = {},
            onSetLektorHideSubtitles = {},
            onChangeLektorTimingOffset = {},
            onChangeLektorDuckingVolume = {},
        )

        assertEquals("menu_lektor", menu.id)

        // Timing is in a nested menu
        val timingMenu = menu.items.filterIsInstance<FilmanOverlayMenuItem.NestedMenu>()
            .single { it.id == "menu_lektor_timing" }
        assertEquals(TextValue.StringResource(R.string.player_lektor_timing), timingMenu.label)
        assertNotNull(timingMenu.value)
        assertEquals(5, timingMenu.items.filterIsInstance<FilmanOverlayMenuItem.Option>().size)

        // Ducking is in a nested menu
        val duckingMenu = menu.items.filterIsInstance<FilmanOverlayMenuItem.NestedMenu>()
            .single { it.id == "menu_lektor_ducking" }
        assertEquals(TextValue.StringResource(R.string.player_lektor_ducking), duckingMenu.label)
        assertNotNull(duckingMenu.value)
        assertEquals(4, duckingMenu.items.filterIsInstance<FilmanOverlayMenuItem.Option>().size)

        // Online models is in a nested menu when there are multiple online models
        val onlineMenu = menu.items.filterIsInstance<FilmanOverlayMenuItem.NestedMenu>()
            .single { it.id == "menu_lektor_online_models" }
        assertEquals(2, onlineMenu.items.filterIsInstance<FilmanOverlayMenuItem.Option>().size)

        // Local models is in a nested menu since there are two local models
        val localMenu = menu.items.filterIsInstance<FilmanOverlayMenuItem.NestedMenu>()
            .single { it.id == "menu_lektor_local_models" }
        assertEquals(2, localMenu.items.filterIsInstance<FilmanOverlayMenuItem.Option>().size)
        assertEquals(TextValue.DynamicString(LektorVoice.MESKI.displayName), localMenu.value)

        // Since meski is downloaded, delete button is present inside local models menu
        assertTrue(localMenu.items.any { it.id == "lektor_model_delete" })

        // System voice has <= 1 choice, so it is kept flat in the root lektor menu
        val systemOption = menu.items.filterIsInstance<FilmanOverlayMenuItem.Option>()
            .single { it.id == "lektor_voice_system" }
        assertNotNull(systemOption)
    }

    @Test
    fun `system voice is hidden in nested menu when multiple system voices are available`() {
        val menu = buildLektorNestedMenu(
            isLektorEnabled = true,
            lektorVoiceId = "system:pl-PL-voice-b",
            downloadedVoices = emptySet(),
            downloadProgress = null,
            isExtracting = false,
            systemVoices = SystemVoiceList(
                loading = false,
                names = listOf("pl-PL-voice-a", "pl-PL-voice-b"),
            ),
            lektorHideSubtitles = false,
            lektorTimingOffsetMs = 0,
            lektorDuckingVolume = 1.0f,
            onToggleLektor = {},
            onSelectLektorVoice = {},
            onDownloadLektorModel = {},
            onDeleteLektorModel = {},
            onPreviewSystemVoice = {},
            onRefreshSystemVoices = {},
            onSetLektorHideSubtitles = {},
            onChangeLektorTimingOffset = {},
            onChangeLektorDuckingVolume = {},
        )

        // System voice is in a nested menu because names.size > 1
        val systemMenu = menu.items.filterIsInstance<FilmanOverlayMenuItem.NestedMenu>()
            .single { it.id == "menu_lektor_system_voices" }
        assertEquals(2, systemMenu.items.filterIsInstance<FilmanOverlayMenuItem.Option>().size)
        assertEquals(TextValue.DynamicString("pl-PL-voice-b"), systemMenu.value)

        // Root lektor items should NOT have flat lektor_voice_system
        assertTrue(menu.items.none { it.id == "lektor_voice_system" })
    }

    @Test
    fun `local models nested menu shows download button when model is not downloaded`() {
        val downloads = mutableListOf<String>()
        val menu = buildLektorNestedMenu(
            isLektorEnabled = true,
            lektorVoiceId = "darkman",
            downloadedVoices = emptySet(),
            downloadProgress = null,
            isExtracting = false,
            systemVoices = SystemVoiceList(loading = false, names = emptyList()),
            lektorHideSubtitles = false,
            lektorTimingOffsetMs = 0,
            lektorDuckingVolume = 1.0f,
            onToggleLektor = {},
            onSelectLektorVoice = {},
            onDownloadLektorModel = downloads::add,
            onDeleteLektorModel = {},
            onPreviewSystemVoice = {},
            onRefreshSystemVoices = {},
            onSetLektorHideSubtitles = {},
            onChangeLektorTimingOffset = {},
            onChangeLektorDuckingVolume = {},
        )

        val localMenu = menu.items.filterIsInstance<FilmanOverlayMenuItem.NestedMenu>()
            .single { it.id == "menu_lektor_local_models" }
        val downloadBtn = localMenu.items.filterIsInstance<FilmanOverlayMenuItem.Button>()
            .single { it.id == "lektor_model_download" }
        downloadBtn.onClick(object : com.pointlessapps.filman.ui.components.FilmanOverlayClickScope {
            override fun popBack() = Unit
        })
        assertEquals(listOf("darkman"), downloads)
    }
}
