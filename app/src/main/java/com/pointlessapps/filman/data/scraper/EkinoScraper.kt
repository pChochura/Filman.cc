package com.pointlessapps.filman.data.scraper

import com.pointlessapps.filman.data.model.EmbedLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

internal class EkinoScraper {

    suspend fun getEmbeds(title: String, year: String?): List<EmbedLink> =
        withContext(Dispatchers.IO) {
            val embeds = mutableListOf<EmbedLink>()
            try {
                val searchUrl = "https://ekino.ws/search/qf/?q=${title.replace(" ", "+")}"
                val searchDoc = Jsoup.connect(searchUrl)
                    .userAgent("Mozilla/5.0")
                    .get()

                val movieLinks = searchDoc.select(".movies-list-item a[href*='/movie/show/']")
                var selectedUrl: String? = null

                for (link in movieLinks) {
                    val href = link.attr("href")
                    val itemTitle = link.text()
                    val itemText = link.closest(".movies-list-item")?.text() ?: ""

                    if (itemTitle.contains(title, ignoreCase = true) || title.contains(
                            itemTitle,
                            ignoreCase = true,
                        )
                    ) {
                        if (year != null && itemText.contains(year)) {
                            selectedUrl = href
                            break
                        } else if (selectedUrl == null) {
                            selectedUrl = href
                        }
                    }
                }

                if (selectedUrl == null) return@withContext emptyList()
                if (!selectedUrl.startsWith("http")) selectedUrl = "https://ekino.ws$selectedUrl"

                val movieDoc = Jsoup.connect(selectedUrl)
                    .userAgent("Mozilla/5.0")
                    .get()

                val playerLinks = movieDoc.select("a[onClick*='ShowPlayer']")
                for (player in playerLinks) {
                    val onClick = player.attr("onClick")
                    val regex = "ShowPlayer\\('([^']+)',\\s*'([^']+)'\\)".toRegex()
                    val match = regex.find(onClick)
                    if (match != null) {
                        val host = match.groupValues[1]
                        val id = match.groupValues[2]

                        try {
                            val watchUrl = "https://ekino.ws/watch/f/$host/$id"
                            val watchDoc = Jsoup.connect(watchUrl)
                                .userAgent("Mozilla/5.0")
                                .get()

                            val realEmbedLink = watchDoc.select("a.buttonprch").attr("href")
                            if (realEmbedLink.isNotEmpty()) {
                                embeds.add(
                                    EmbedLink(
                                        url = realEmbedLink,
                                        serverName = "ekino.ws",
                                        version = host,
                                        quality = "Ekino",
                                    ),
                                )
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            embeds
        }
}
