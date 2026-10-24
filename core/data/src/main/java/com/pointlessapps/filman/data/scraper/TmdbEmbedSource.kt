package com.pointlessapps.filman.data.scraper

import android.util.Log
import com.pointlessapps.filman.data.model.EmbedLink

class TmdbEmbedSource {
    private val tag = "TmdbEmbedSource"

    private val videasyServers = listOf(
        "Yoru" to "yoru",
        "Breach" to "breach",
        "Neon" to "neon",
        "Cypher" to "cypher",
        "Vyse" to "vyse",
    )

    fun getEmbeds(
        tmdbId: Int,
        season: Int? = null,
        episode: Int? = null,
    ): List<EmbedLink> {
        val isTvShow = season != null && episode != null
        Log.d(tag, "Generating embeds for tmdbId=$tmdbId (isTvShow=$isTvShow)")
        val embeds = mutableListOf<EmbedLink>()

        if (isTvShow) {
            embeds.add(
                EmbedLink(
                    url = "https://vsembed.ru/embed/tv?tmdb=$tmdbId&season=$season&episode=$episode",
                    serverName = "VidSrc",
                    quality = "1080p",
                    sourceWebsite = "tmdb",
                ),
            )
            embeds.add(
                EmbedLink(
                    url = "https://vidcore.io/tv/$tmdbId/$season/$episode?autoPlay=true",
                    serverName = "VidCore",
                    quality = "1080p",
                    sourceWebsite = "tmdb",
                ),
            )
            embeds.add(
                EmbedLink(
                    url = "https://vidfast.vc/tv/$tmdbId/$season/$episode?autoPlay=true",
                    serverName = "VidFast",
                    quality = "1080p",
                    sourceWebsite = "tmdb",
                ),
            )
            embeds.add(
                EmbedLink(
                    url = "https://vidnest.fun/tv/$tmdbId/$season/$episode",
                    serverName = "VidNest",
                    quality = "1080p",
                    sourceWebsite = "tmdb",
                ),
            )
            videasyServers.forEach { (name, param) ->
                embeds.add(
                    EmbedLink(
                        url = "https://player.videasy.to/tv/$tmdbId/$season/$episode?server=$param",
                        serverName = "Videasy ($name)",
                        quality = "1080p",
                        sourceWebsite = "tmdb",
                    ),
                )
            }
        } else {
            embeds.add(
                EmbedLink(
                    url = "https://vsembed.ru/embed/movie?tmdb=$tmdbId",
                    serverName = "VidSrc",
                    quality = "1080p",
                    sourceWebsite = "tmdb",
                ),
            )
            embeds.add(
                EmbedLink(
                    url = "https://vidcore.io/movie/$tmdbId?autoPlay=true",
                    serverName = "VidCore",
                    quality = "1080p",
                    sourceWebsite = "tmdb",
                ),
            )
            embeds.add(
                EmbedLink(
                    url = "https://vidfast.vc/movie/$tmdbId?autoPlay=true",
                    serverName = "VidFast",
                    quality = "1080p",
                    sourceWebsite = "tmdb",
                ),
            )
            embeds.add(
                EmbedLink(
                    url = "https://vidnest.fun/movie/$tmdbId",
                    serverName = "VidNest",
                    quality = "1080p",
                    sourceWebsite = "tmdb",
                ),
            )
            videasyServers.forEach { (name, param) ->
                embeds.add(
                    EmbedLink(
                        url = "https://player.videasy.to/movie/$tmdbId?server=$param",
                        serverName = "Videasy ($name)",
                        quality = "1080p",
                        sourceWebsite = "tmdb",
                    ),
                )
            }
        }

        return embeds
    }
}
