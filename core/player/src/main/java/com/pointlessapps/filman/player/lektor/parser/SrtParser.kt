package com.pointlessapps.filman.player.lektor.parser

import com.pointlessapps.filman.player.lektor.model.SubtitleCue
import com.pointlessapps.filman.player.lektor.normalizer.PolishTextNormalizer
import java.util.regex.Pattern

object SrtParser {

    private val TIMESTAMP_LINE_REGEX = Pattern.compile(
        "((?:\\d{1,2}:)?\\d{1,2}:\\d{2}[,.]\\d{3})\\s*-->\\s*((?:\\d{1,2}:)?\\d{1,2}:\\d{2}[,.]\\d{3})"
    )

    fun parse(rawSrt: String, normalizePolish: Boolean = true): List<SubtitleCue> {
        val sanitized = rawSrt.removePrefix("\uFEFF").replace("\r\n", "\n").replace("\r", "\n")
        val lines = sanitized.lines()
        val cues = mutableListOf<SubtitleCue>()

        var currentIndex = 1
        var currentStartMs = -1L
        var currentEndMs = -1L
        val textLines = mutableListOf<String>()

        fun flushCurrentCue() {
            if (currentStartMs >= 0 && currentEndMs > currentStartMs && textLines.isNotEmpty()) {
                val rawText = textLines.joinToString("\n").trim()
                val finalText = if (normalizePolish) {
                    PolishTextNormalizer.normalize(rawText)
                } else {
                    rawText
                }

                if (finalText.isNotBlank()) {
                    cues.add(
                        SubtitleCue(
                            index = currentIndex,
                            startMs = currentStartMs,
                            endMs = currentEndMs,
                            text = finalText,
                        )
                    )
                    currentIndex++
                }
            }
            currentStartMs = -1L
            currentEndMs = -1L
            textLines.clear()
        }

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("WEBVTT") || trimmed.startsWith("NOTE") || trimmed.startsWith("STYLE") || trimmed.startsWith("REGION")) {
                continue
            }

            val matcher = TIMESTAMP_LINE_REGEX.matcher(trimmed)
            if (matcher.find()) {
                // If there's an existing cue being built, flush it
                flushCurrentCue()
                val startStr = matcher.group(1).orEmpty()
                val endStr = matcher.group(2).orEmpty()
                currentStartMs = parseTimestampToMs(startStr)
                currentEndMs = parseTimestampToMs(endStr)
            } else if (trimmed.isEmpty()) {
                flushCurrentCue()
            } else if (currentStartMs >= 0) {
                // We're inside text lines
                textLines.add(line)
            }
        }

        // Flush last cue if file doesn't end with empty line
        flushCurrentCue()

        return cues
    }

    private fun parseTimestampToMs(timestamp: String): Long {
        val normalized = timestamp.replace(',', '.').trim()
        val parts = normalized.split(":")
        return when (parts.size) {
            3 -> {
                val hours = parts[0].toLongOrNull() ?: 0L
                val minutes = parts[1].toLongOrNull() ?: 0L
                val secondsParts = parts[2].split(".")
                val seconds = secondsParts.getOrNull(0)?.toLongOrNull() ?: 0L
                val millis = secondsParts.getOrNull(1)?.padEnd(3, '0')?.take(3)?.toLongOrNull() ?: 0L
                (hours * 3600_000L) + (minutes * 60_000L) + (seconds * 1000L) + millis
            }
            2 -> {
                val minutes = parts[0].toLongOrNull() ?: 0L
                val secondsParts = parts[1].split(".")
                val seconds = secondsParts.getOrNull(0)?.toLongOrNull() ?: 0L
                val millis = secondsParts.getOrNull(1)?.padEnd(3, '0')?.take(3)?.toLongOrNull() ?: 0L
                (minutes * 60_000L) + (seconds * 1000L) + millis
            }
            else -> 0L
        }
    }
}
