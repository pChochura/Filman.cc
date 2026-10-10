package com.pointlessapps.filman.lite.download

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HlsPlaylistTest {

    @Test
    fun detectsHlsUrls() {
        assertTrue(HlsPlaylist.isHls("https://a/b/index.m3u8?token=1"))
        assertFalse(HlsPlaylist.isHls("https://a/b/video.mp4"))
    }

    @Test
    fun masterPlaylistVariantsAreResolvedAgainstBase() {
        val master = """
            #EXTM3U
            #EXT-X-STREAM-INF:PROGRAM-ID=1,AVERAGE-BANDWIDTH=900,BANDWIDTH=1000
            low/index.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=5000
            /high/index.m3u8
        """.trimIndent()
        val variants = HlsPlaylist.parseVariants("https://host/path/master.m3u8", master)
        assertEquals(listOf(1000L, 5000L), variants.map { it.bandwidth })
        assertEquals("https://host/path/low/index.m3u8", variants[0].url)
        assertEquals("https://host/high/index.m3u8", variants[1].url)
    }

    @Test
    fun mediaPlaylistListsSegmentsAndInitSegment() {
        val media = """
            #EXTM3U
            #EXT-X-MAP:URI="init.mp4"
            #EXTINF:4.0,
            seg0.m4s
            #EXTINF:4.0,
            https://cdn/seg1.m4s
            #EXT-X-ENDLIST
        """.trimIndent()
        val playlist = HlsPlaylist.parseMedia("https://host/v/index.m3u8", media)
        assertEquals("https://host/v/init.mp4", playlist.initSegment)
        assertEquals(listOf("https://host/v/seg0.m4s", "https://cdn/seg1.m4s"), playlist.segments)
        assertFalse(playlist.encrypted)
    }

    @Test
    fun encryptedPlaylistsAreFlagged() {
        val media = "#EXTM3U\n#EXT-X-KEY:METHOD=AES-128,URI=\"k\"\n#EXTINF:4,\nseg.ts"
        val playlist = HlsPlaylist.parseMedia("https://host/index.m3u8", media)
        assertTrue(playlist.encrypted)
        assertNull(playlist.initSegment)
    }
}
