package com.pointlessapps.filman.data.scraper

import com.pointlessapps.filman.data.api.dto.VideoLink
import com.pointlessapps.filman.data.model.EmbedLink
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PublishResolvedEmbedsTest {
    @Test
    fun `fast links are published before another embed finishes`() = runTest {
        val slow = CompletableDeferred<Unit>()
        val published = mutableListOf<String>()
        val embeds = listOf(
            EmbedLink("https://example.com/slow", "slow"),
            EmbedLink("https://example.com/fast", "fast"),
        )
        val job = async {
            publishResolvedEmbeds(
                embeds,
                resolve = { embed ->
                    if (embed.serverName == "slow") slow.await()
                    listOf(VideoLink(url = embed.url))
                },
                publish = { links -> published.addAll(links.map { it.url }) },
            )
        }
        yield()
        yield()
        assertEquals(listOf("https://example.com/fast"), published)
        assertFalse(job.isCompleted)
        slow.complete(Unit)
        job.await()
        assertEquals(listOf("https://example.com/fast", "https://example.com/slow"), published)
    }
}
