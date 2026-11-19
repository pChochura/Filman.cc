package com.pointlessapps.filman.data.api

import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

internal object SubtitleTextDecoder {
    private val WINDOWS_1250: Charset = Charset.forName("windows-1250")
    private val ISO_8859_2: Charset = Charset.forName("ISO-8859-2")

    private val CP1250_CONTROL_MARKERS = setOf(0x8C, 0x8F, 0x9C, 0x9F)
    private val CP1250_EXTRA_MARKERS = setOf(0xA5, 0xB9)
    private val ISO_8859_2_MARKERS = setOf(0xA1, 0xA6, 0xAC, 0xB1, 0xB6, 0xBC)

    fun decode(bytes: ByteArray, declaredCharset: Charset? = null): String {
        if (bytes.isEmpty()) return ""

        decodeBom(bytes)?.let { return it }

        if (declaredCharset != null && declaredCharset != Charsets.UTF_8) {
            decodeStrict(bytes, declaredCharset)?.let { return it }
        }

        decodeStrict(bytes, Charsets.UTF_8)?.let { return it }

        return String(bytes, detectCentralEuropeanCharset(bytes))
    }

    private fun decodeBom(bytes: ByteArray): String? {
        if (bytes.size >= 3 &&
            (bytes[0].toInt() and 0xFF) == 0xEF &&
            (bytes[1].toInt() and 0xFF) == 0xBB &&
            (bytes[2].toInt() and 0xFF) == 0xBF
        ) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }
        if (bytes.size >= 2) {
            val b0 = bytes[0].toInt() and 0xFF
            val b1 = bytes[1].toInt() and 0xFF
            if (b0 == 0xFF && b1 == 0xFE) {
                return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
            }
            if (b0 == 0xFE && b1 == 0xFF) {
                return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
            }
        }
        return null
    }

    private fun decodeStrict(bytes: ByteArray, charset: Charset): String? = runCatching {
        charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    }.getOrNull()

    private fun detectCentralEuropeanCharset(bytes: ByteArray): Charset {
        var cp1250ControlMarkers = 0
        var cp1250AllMarkers = 0
        var iso88592Markers = 0
        for (byte in bytes) {
            val value = byte.toInt() and 0xFF
            if (value in CP1250_CONTROL_MARKERS) {
                cp1250ControlMarkers++
                cp1250AllMarkers++
            } else if (value in CP1250_EXTRA_MARKERS) {
                cp1250AllMarkers++
            } else if (value in ISO_8859_2_MARKERS) {
                iso88592Markers++
            }
        }
        return if (cp1250ControlMarkers == 0 && iso88592Markers > cp1250AllMarkers) {
            ISO_8859_2
        } else {
            WINDOWS_1250
        }
    }
}
