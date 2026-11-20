package com.pointlessapps.filman.data.api

import com.pointlessapps.filman.data.api.dto.LinksResponse
import com.pointlessapps.filman.data.api.dto.VideoLink
import com.pointlessapps.filman.data.api.extractors.ExtractedVideo
import com.pointlessapps.filman.data.api.extractors.Subtitle
import com.pointlessapps.filman.data.model.EmbedLink
import kotlinx.coroutines.delay

fun VideoLink.toExtractedVideo(): ExtractedVideo = ExtractedVideo(
    url = url,
    serverName = serverName.ifEmpty { "$sourceWebsite $language".trim() },
    version = language,
    sourceWebsite = sourceWebsite,
    quality = quality,
    headers = headers,
    filename = filename,
    subtitles = subtitles.map { sub ->
        Subtitle(
            url = sub.url,
            label = sub.label,
            language = sub.language,
        )
    },
)

fun List<VideoLink>.toExtractedVideos(): List<ExtractedVideo> = map { it.toExtractedVideo() }

fun VideoLink.toEmbedLink(): EmbedLink = EmbedLink(
    url = url,
    serverName = serverName.ifEmpty { "$sourceWebsite $language".trim() },
    version = language,
    quality = quality,
    sourceWebsite = sourceWebsite,
    headers = headers,
    subtitles = subtitles.map { sub ->
        Subtitle(
            url = sub.url,
            label = sub.label,
            language = sub.language,
        )
    },
)

fun List<VideoLink>.toEmbedLinks(): List<EmbedLink> = map { it.toEmbedLink() }

suspend fun TiwiRepository.pollStreamLinks(
    url: String,
    isTvShow: Boolean,
    maxAttempts: Int,
    delayMs: Long,
    shouldContinue: () -> Boolean = { true },
    onResponse: suspend (LinksResponse) -> Unit,
) {
    var attempts = 0
    while (attempts < maxAttempts && shouldContinue()) {
        attempts++
        val response = getLinks(url, isTvShow = isTvShow) ?: break
        onResponse(response)
        if (response.status == "READY" || response.status == "ERROR") {
            break
        }
        delay(delayMs)
    }
}
