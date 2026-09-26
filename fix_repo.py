import re
with open("core/data/src/main/java/com/pointlessapps/filman/data/api/FilmanRepository.kt", "r") as f:
    content = f.read()

# 1. Update MovieItem generation to use prefixes
content = content.replace("url = it.id.toString(),", "url = if (it.mediaType == \"TV_SHOW\") \"show_${it.id}\" else \"movie_${it.id}\",")

# For the featuredAll endpoint, isTv is already calculated
content = content.replace("val item = MovieItem(\n                        url = it.id.toString(),\n                        titlePl = it.title,\n                        posterUrl = if (it.posterPath != null) tmdbImageBaseUrl + it.posterPath else \"\",\n                        backgroundUrl = if (it.backdropPath != null) tmdbImageBaseUrl + it.backdropPath else \"\",\n                        description = it.overview,\n                        isTvShow = isTv\n                    )", "val item = MovieItem(\n                        url = if (isTv) \"show_${it.id}\" else \"movie_${it.id}\",\n                        titlePl = it.title,\n                        posterUrl = if (it.posterPath != null) tmdbImageBaseUrl + it.posterPath else \"\",\n                        backgroundUrl = if (it.backdropPath != null) tmdbImageBaseUrl + it.backdropPath else \"\",\n                        description = it.overview,\n                        isTvShow = isTv\n                    )")

# 2. Update getMediaDetails to handle prefixes
get_media = """
    suspend fun getMediaDetails(mediaUrlRaw: String): DetailedMedia? {
        val isExplicitShow = mediaUrlRaw.startsWith("show_")
        val isExplicitMovie = mediaUrlRaw.startsWith("movie_")
        val idStr = mediaUrlRaw.removePrefix("show_").removePrefix("movie_")
        val id = idStr.toIntOrNull() ?: return null
        return try {
            if (isExplicitShow) {
                val show = api.getTvShowDetails(id)
                val mappedSeasons = show.seasons.map { s ->
                    Season(
                        name = s.name,
                        episodes = s.episodes.map { ep ->
                            EpisodeLink(
                                title = ep.name,
                                url = "show_${show.id}_${s.seasonNumber}_${ep.episodeNumber}"
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

# 3. Update getLinks to handle prefixes
get_links = """
    suspend fun getLinks(url: String, isTvShow: Boolean): LinksResponse? {
        val idStr = url.removePrefix("show_").removePrefix("movie_")
        val id = idStr.toIntOrNull() ?: return null
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
