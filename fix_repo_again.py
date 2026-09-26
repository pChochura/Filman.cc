import re
with open("core/data/src/main/java/com/pointlessapps/filman/data/api/FilmanRepository.kt", "r") as f:
    content = f.read()

# 1. Remove prefixes from MovieItem urls
content = content.replace('url = "show_${it.id}",', 'url = it.id.toString(),')
content = content.replace('url = "movie_${it.id}",', 'url = it.id.toString(),')

get_media = """
    suspend fun getMediaDetails(mediaUrlRaw: String, isTvShow: Boolean? = null): DetailedMedia? {
        val id = mediaUrlRaw.toIntOrNull() ?: return null
        return try {
            if (isTvShow == true) {
                val show = api.getTvShowDetails(id)
                val mappedSeasons = show.seasons.map { s ->
                    Season(
                        name = s.name,
                        episodes = s.episodes.map { ep ->
                            EpisodeLink(
                                title = ep.name,
                                url = "${show.id}_${s.seasonNumber}_${ep.episodeNumber}"
                            )
                        }
                    )
                }
                DetailedMedia(
                    baseItem = MovieItem(
                        url = mediaUrlRaw,
                        titlePl = show.title,
                        posterUrl = if (show.posterPath != null) tmdbImageBaseUrl + show.posterPath else "",
                        backgroundUrl = if (show.backdropPath != null) tmdbImageBaseUrl + show.backdropPath else "",
                        description = show.overview,
                        year = show.firstAirDate?.take(4)?.toIntOrNull(),
                        isTvShow = true,
                        seasons = mappedSeasons
                    ),
                    embeds = emptyList(),
                    actors = show.cast.map { 
                        ActorInfo(
                            role = ActorRole.ACTOR,
                            name = it.name,
                            avatarUrl = if (it.profilePath != null) tmdbImageBaseUrl + it.profilePath else null,
                            url = it.id.toString()
                        )
                    },
                    similarMovies = emptyList()
                )
            } else {
                val movie = api.getMovieDetails(id)
                DetailedMedia(
                    baseItem = MovieItem(
                        url = mediaUrlRaw,
                        titlePl = movie.title,
                        posterUrl = if (movie.posterPath != null) tmdbImageBaseUrl + movie.posterPath else "",
                        backgroundUrl = if (movie.backdropPath != null) tmdbImageBaseUrl + movie.backdropPath else "",
                        description = movie.overview,
                        year = movie.releaseDate?.take(4)?.toIntOrNull(),
                        isTvShow = false
                    ),
                    embeds = emptyList(),
                    actors = movie.cast.map { 
                        ActorInfo(
                            role = ActorRole.ACTOR,
                            name = it.name,
                            avatarUrl = if (it.profilePath != null) tmdbImageBaseUrl + it.profilePath else null,
                            url = it.id.toString()
                        )
                    },
                    similarMovies = emptyList()
                )
            }
        } catch (e: Exception) {
            Log.e("FilmanRepository", "Error during API call", e)
            null
        }
    }
"""

content = re.sub(r'    suspend fun getMediaDetails\(mediaUrlRaw: String\): DetailedMedia\? \{[\s\S]*?        \} catch \(e: Exception\) \{\n            Log.e\("FilmanRepository", "Error during API call", e\)\n            null\n        \}\n    \}', get_media.strip(), content)

# Update getLinks to use original ID parsing
get_links = """
    suspend fun getLinks(url: String, isTvShow: Boolean): LinksResponse? {
        val id = url.substringBefore("_").toIntOrNull() ?: return null
        return try {
            if (!isTvShow) {
                api.getMovieLinks(id)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("FilmanRepository", "Error getting links", e)
            null
        }
    }
"""
content = re.sub(r'    suspend fun getLinks\(url: String, isTvShow: Boolean\): LinksResponse\? \{[\s\S]*?            null\n        \}\n    \}', get_links.strip(), content)

with open("core/data/src/main/java/com/pointlessapps/filman/data/api/FilmanRepository.kt", "w") as f:
    f.write(content)
