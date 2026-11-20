package com.pointlessapps.filman.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoSignatureExtractorTest {

    @Test
    fun `extracts release name from Content-Disposition header`() {
        val headers = mapOf(
            "Content-Disposition" to "attachment; filename=\"Gladiator.II.2024.1080p.AMZN.WEB-DL.DDP5.1.Atmos.H.264-FLUX.mkv\""
        )
        val extracted = VideoSignatureExtractor.extract(
            videoUrl = "https://cdn.example.com/stream/v123",
            headers = headers,
        )
        assertEquals("Gladiator.II.2024.1080p.AMZN.WEB-DL.DDP5.1.Atmos.H.264-FLUX", extracted)
    }

    @Test
    fun `extracts release name from URL query parameters`() {
        val url = "https://host.com/download?id=123&title=Dune.Part.Two.2024.2160p.WEB-DL.DDP5.1.Atmos.DV.HDR.H.265-FLUX.mp4&token=xyz"
        val extracted = VideoSignatureExtractor.extract(url)
        assertEquals("Dune.Part.Two.2024.2160p.WEB-DL.DDP5.1.Atmos.DV.HDR.H.265-FLUX", extracted)
    }

    @Test
    fun `extracts URL-encoded release name from URL query parameters`() {
        val url = "https://host.com/stream?file=Breaking.Bad.S05E14.720p.HDTV.x264-EVO.mkv"
        val extracted = VideoSignatureExtractor.extract(url)
        assertEquals("Breaking.Bad.S05E14.720p.HDTV.x264-EVO", extracted)
    }

    @Test
    fun `extracts release name from URL path segment ignoring generic m3u8 playlist`() {
        val url = "https://edge.host.com/hls/The.Bear.S02E01.1080p.Hulu.WEB-DL.DDP5.1.H.264-FLUX/master.m3u8"
        val extracted = VideoSignatureExtractor.extract(url)
        assertEquals("The.Bear.S02E01.1080p.Hulu.WEB-DL.DDP5.1.H.264-FLUX", extracted)
    }

    @Test
    fun `ignores generic segment names and hex hashes`() {
        val url = "https://edge.host.com/video/66e04d799f2b84ef3381a17c/master.m3u8"
        val extracted = VideoSignatureExtractor.extract(url)
        assertNull(extracted)
    }

    @Test
    fun `falls back to serverName and quality when URL is purely generic`() {
        val url = "https://edge.host.com/stream/video.mp4"
        val extracted = VideoSignatureExtractor.extract(
            videoUrl = url,
            serverName = "vidsrc (1080p WEB-DL)",
            quality = "1080p",
        )
        assertEquals("vidsrc (1080p WEB-DL).1080p", extracted)
    }

    @Test
    fun `extracts release name from proxy url parameter`() {
        val proxyUrl = "https://filman.cc/api/m3u8-proxy?url=" +
            java.net.URLEncoder.encode("https://storage.cdn.com/stream/Breaking.Bad.S01E01.1080p.BluRay.x264-FLUX.m3u8", "UTF-8")
        val extracted = VideoSignatureExtractor.extract(proxyUrl)
        assertEquals("Breaking.Bad.S01E01.1080p.BluRay.x264-FLUX", extracted)
    }

    @Test
    fun `cleanFilename strips extensions and query parameters`() {
        assertEquals("My.Movie.2024.1080p", VideoSignatureExtractor.cleanFilename("My.Movie.2024.1080p.mkv?token=123"))
        assertEquals("My.Movie.2024.1080p", VideoSignatureExtractor.cleanFilename("\"My.Movie.2024.1080p.mp4\""))
    }
}
