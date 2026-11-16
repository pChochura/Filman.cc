/*
 * Derived from edge-tts src/edge_tts/drm.py, copyright edge-tts contributors.
 * https://github.com/rany2/edge-tts/blob/360fa8e1de354e1dda74c09bd695887d6a44df74/src/edge_tts/drm.py
 * This file is distributed under the GNU LGPL version 3.
 */
package com.pointlessapps.filman.player.lektor.engine

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale

internal object EdgeGecToken {
    private fun words(vararg values: Long) = IntArray(values.size) { values[it].toInt() }

    private val initial = words(
        0x6A09E667, 0xBB67AE85, 0x3C6EF372, 0xA54FF53A,
        0x510E527F, 0x9B05688C, 0x1F83D9AB, 0x5BE0CD19,
    )
    private val partialState = words(
        0xFD9FBEA2, 0x91243CE7, 0xBE499094, 0xCD2E1E0A,
        0x6C006F49, 0x04BD35AB, 0xFEB0A7E4, 0x03EFFBD2,
    )
    private val partialSchedule = words(
        0xEB24BF9E, 0x1558691F, 0xA0C09EE2, 0x0CFA32BA,
        0xA622EE8C, 0xD1DD9491, 0xC0DD25A0, 0xF9E70AB6,
        0xF0C31573, 0x7058CE4E, 0x7188E606, 0x00E5AB33,
        0xD258B060, 0x72766564,
    )
    private val roundConstants = words(
        0x428A2F98, 0x71374491, 0xB5C0FBCF, 0xE9B5DBA5, 0x3956C25B, 0x59F111F1, 0x923F82A4, 0xAB1C5ED5,
        0xD807AA98, 0x12835B01, 0x243185BE, 0x550C7DC3, 0x72BE5D74, 0x80DEB1FE, 0x9BDC06A7, 0xC19BF174,
        0xE49B69C1, 0xEFBE4786, 0x0FC19DC6, 0x240CA1CC, 0x2DE92C6F, 0x4A7484AA, 0x5CB0A9DC, 0x76F988DA,
        0x983E5152, 0xA831C66D, 0xB00327C8, 0xBF597FC7, 0xC6E00BF3, 0xD5A79147, 0x06CA6351, 0x14292967,
        0x27B70A85, 0x2E1B2138, 0x4D2C6DFC, 0x53380D13, 0x650A7354, 0x766A0ABB, 0x81C2C92E, 0x92722C85,
        0xA2BFE8A1, 0xA81A664B, 0xC24B8B70, 0xC76C51A3, 0xD192E819, 0xD6990624, 0xF40E3585, 0x106AA070,
        0x19A4C116, 0x1E376C08, 0x2748774C, 0x34B0BCB5, 0x391C0CB3, 0x4ED8AA4A, 0x5B9CCA4F, 0x682E6FF3,
        0x748F82EE, 0x78A5636F, 0x84C87814, 0x8CC70208, 0x90BEFFFA, 0xA4506CEB, 0xBEF9A3F7, 0xC67178F2,
    )
    private val coefficients = arrayOf(
        words(0xE82E91B1, 0xB32700D0), words(0x44DE24C6, 0x88DFF51D),
        words(0x3BEB65BC, 0x29514038), words(0xC89C5933, 0xB328CBDF),
        words(0x8D1EC211, 0xD405B4D5), words(0x314EF5D5, 0x26E55F3C),
        words(0xE92491B1, 0x607801C6), words(0xF0A5B9D5, 0x82B2C1FB),
        words(0x06064CF3, 0xFA51A3DA), words(0xDA79FA60, 0x207CC464),
        words(0xBED20560, 0x3C3C74F3), words(0x89F9C9F8, 0xD2D14B61),
        words(0x35FA38C6, 0xBB04A2C1), words(0xCB49E3C6, 0xC5626167),
    )
    private const val ADJUSTMENT = 0xC9DC06A7.toInt()

    fun generate(unixSeconds: Long, clientToken: String): String {
        val seconds = unixSeconds + 11_644_473_600L
        val ticks = (seconds - Math.floorMod(seconds, 300L)) * 10_000_000L
        val data = "$ticks$clientToken".toByteArray(Charsets.US_ASCII)
        val digest = digest(data)
        val cs64 = checksum(data, digest)
        return "${hex(cs64)}-${hex(digest)}"
    }

    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02X".format(Locale.US, it.toInt() and 255) }
    private fun sigma0(x: Int) = Integer.rotateRight(x, 7) xor Integer.rotateRight(x, 18) xor (x ushr 3)
    private fun sigma1(x: Int) = Integer.rotateRight(x, 17) xor Integer.rotateRight(x, 19) xor (x ushr 10)

    private fun expand(w: IntArray, start: Int) {
        for (i in start until 64) w[i] = sigma1(w[i - 2]) + w[i - 7] + sigma0(w[i - 15]) + w[i - 16]
    }

    private fun rounds(state: IntArray, w: IntArray, start: Int): IntArray {
        var a = state[0]; var b = state[1]; var c = state[2]; var d = state[3]
        var e = state[4]; var f = state[5]; var g = state[6]; var h = state[7]
        for (i in start until 64) {
            val s1 = Integer.rotateRight(e, 6) xor Integer.rotateRight(e, 11) xor Integer.rotateRight(e, 25)
            val s0 = Integer.rotateRight(a, 2) xor Integer.rotateRight(a, 13) xor Integer.rotateRight(a, 22)
            val t1 = h + s1 + (g xor (e and (f xor g))) +
                (if (start == 14 && i == 14) ADJUSTMENT else roundConstants[i]) + w[i]
            val t2 = s0 + ((a and b) or (c and (a or b)))
            h = g; g = f; f = e; e = d + t1; d = c; c = b; b = a; a = t1 + t2
        }
        return intArrayOf(a, b, c, d, e, f, g, h)
    }

    private fun digest(data: ByteArray): ByteArray {
        require(data.size >= 7)
        val w = IntArray(64)
        w[14] = ((data[0].toInt() and 255) shl 16) or ((data[1].toInt() and 255) shl 8) or (data[2].toInt() and 255)
        w[15] = ByteBuffer.wrap(data, 3, 4).int
        for (i in 16 until 30) {
            var value = partialSchedule[i - 16]
            value += sigma1(w[i - 2]) xor if (i == 16) 0x000B92C0 else 0
            if (i - 7 >= 14) value += w[i - 7]
            if (i - 15 >= 14) value += sigma0(w[i - 15]) xor if (i == 29) 0x059C0B80 else 0
            w[i] = value
        }
        w[30] = sigma1(w[28]) + w[23] + sigma0(w[15]) + w[14] + ADJUSTMENT - roundConstants[14]
        expand(w, 31)
        val state = rounds(partialState, w, 14)
        for (i in state.indices) state[i] += initial[i]
        val length = 57 + data.size
        val padding = Math.floorMod(55 - length, 64)
        val buffer = ByteBuffer.allocate(data.size - 7 + 1 + padding + 8)
        buffer.put(data, 7, data.size - 7).put(0x80.toByte())
        buffer.position(buffer.position() + padding)
        buffer.putLong(length * 8L).flip()
        while (buffer.hasRemaining()) {
            for (i in 0 until 16) w[i] = buffer.int
            expand(w, 16)
            val working = rounds(state, w, 0)
            for (i in state.indices) state[i] += working[i]
        }
        val result = ByteBuffer.allocate(32)
        state.forEach { result.putInt(it) }
        return result.array()
    }

    private fun checksum(data: ByteArray, digest: ByteArray): ByteArray {
        val keys = ByteBuffer.wrap(digest).order(ByteOrder.LITTLE_ENDIAN)
        val a = keys.int or 1
        val b = keys.int or 1
        val factors = intArrayOf(a * 0x82893933.toInt(), b * 0x82893933.toInt())
        val count = ((data.size + 8) / 8) * 2
        val terms = IntArray(14 + count)
        val offsets = IntArray(terms.size)
        for (i in coefficients.indices) {
            terms[i] = coefficients[i][0] * factors[i % 2]
            offsets[i] = coefficients[i][1]
        }
        val input = ByteBuffer.allocate(count * 4).order(ByteOrder.LITTLE_ENDIAN)
        input.put(0.toByte()).put(data).rewind()
        for (i in 0 until count) {
            val word = input.int
            terms[14 + i] = word * if (i % 2 == 0) a else b
            offsets[14 + i] = Integer.rotateLeft(word, 16) *
                if (i % 2 == 0) 0xEF0569FB.toInt() else 0xC31713DB.toInt()
            if (i == 0) {
                terms[14] += 0xF2F90B1A.toInt() * factors[0]
                offsets[14] += 0x0B1A0000
            }
        }
        var swapAcc = 0; var swapLast = 0; var state = 0; var acc = 0
        for (i in terms.indices step 2) {
            val x = terms[i]
            val y = terms[i + 1]
            var q = Integer.rotateLeft(offsets[i] + x, 16) * 0x76F50F34
            swapAcc += Integer.rotateLeft(q, 16) * 0xC3EFEA97.toInt() + q
            q = Integer.rotateLeft(offsets[i + 1] + y, 16) * 0x1881CE3C
            swapLast = Integer.rotateLeft(q, 16) * 0x35BD1EC9 + q
            swapAcc += swapLast
            state = (state * a + x)
            for (factor in words(0xCF98B111, 0x87085B9F, 0x12CEB96D, 0x257E1D83)) {
                state = Integer.rotateLeft(state, 16) * factor
            }
            acc += state
            var t = state * b + y
            for (factor in words(0xA27416F5, 0xD38396FF, 0x7C932B89, 0xBFA49F69)) {
                t = Integer.rotateLeft(t, 16) * factor
            }
            state = t
            acc += state
        }
        return ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putInt(swapLast xor state).putInt(swapAcc xor acc).array()
    }
}
