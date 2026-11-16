package com.pointlessapps.filman.ui.player

import android.text.TextUtils
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import com.pointlessapps.filman.data.api.VideoUrlResolver
import com.pointlessapps.filman.data.local.SettingsManager
import com.pointlessapps.filman.ui.components.FilmanOverlayMenuItem
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.After
import org.junit.Test

class AudioTrackReferencesTest {
    @After
    fun tearDown() {
        unmockkStatic(TextUtils::class)
    }

    @Test
    fun `duplicate format identifiers produce distinct selectable audio tracks`() {
        mockkStatic(TextUtils::class)
        every { TextUtils.isEmpty(any()) } answers { firstArg<CharSequence?>().isNullOrEmpty() }
        val format = Format.Builder().setId("0:").setSampleMimeType(MimeTypes.AUDIO_AAC).build()
        val first = Tracks.Group(TrackGroup("same", format, format), false, intArrayOf(C.FORMAT_HANDLED, C.FORMAT_HANDLED), booleanArrayOf(false, false))
        val second = Tracks.Group(TrackGroup("same", format), false, intArrayOf(C.FORMAT_HANDLED), booleanArrayOf(false))
        val videoFormat = Format.Builder().setSampleMimeType(MimeTypes.VIDEO_H264).build()
        val video = Tracks.Group(TrackGroup(videoFormat), false, intArrayOf(C.FORMAT_HANDLED), booleanArrayOf(false))
        val tracks = Tracks(listOf(video, first, second))
        val references = audioTrackReferences(tracks)

        assertEquals(listOf("0:0", "0:1", "1:0"), references.map { it.id })
        assertSame(first, references[1].group)
        assertEquals(1, references[1].trackIndex)
        assertSame(second, references[2].group)
        assertEquals(0, references[2].trackIndex)

        val settings = mockk<SettingsManager> {
            every { sourcesPriorityFlow } returns MutableStateFlow(emptyList())
        }
        val resolver = mockk<VideoUrlResolver> {
            every { settingsManager } returns settings
        }
        val events = mutableListOf<com.pointlessapps.filman.ui.base.FilmanEvent>()
        val state = PlayerState(audioTracks = references.map { PlayerAudioTrack(it.id, "Audio") })
        val menu = PlayerMenuBuilder.buildOverlayMenuData(state, resolver, null) { events.add(it) }
        val audioMenu = menu.items.filterIsInstance<FilmanOverlayMenuItem.NestedMenu>()
            .first { it.id == "menu_audio" }
        val options = audioMenu.items.filterIsInstance<FilmanOverlayMenuItem.Option>()

        assertEquals(3, options.map { it.id }.distinct().size)
        options[2].onClick()
        assertEquals("1:0", (events.last() as PlayerEvent.SelectAudioTrack).trackId)
        assertSame(second, references.single { it.id == (events.last() as PlayerEvent.SelectAudioTrack).trackId }.group)
    }
}
