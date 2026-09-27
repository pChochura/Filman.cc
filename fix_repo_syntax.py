import re
with open("core/data/src/main/java/com/pointlessapps/filman/data/api/FilmanRepository.kt", "r") as f:
    content = f.read()

get_media = """
    suspend fun getMediaDetails(mediaUrlRaw: String, isTvShow: Boolean? = null): DetailedMedia? {
        val id = mediaUrlRaw.substringBefore("_").toIntOrNull() ?: return null
        val tvShowFlag = isTvShow ?: mediaUrlRaw.contains("_")
        return if (tvShowFlag) {
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
                        filmanRating = show.voteAverage?.let { Rating(it.toFloat(), 10f) },
                        imdbRating = show.voteAverage?.let { Rating(it.toFloat(), 10f) },
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
                    categories = show.genres.map { CategoryInfo(name = it.name, url = it.id.toString(), id = it.id) },
                    metaInfo = MediaMetadata(
                        year = show.firstAirDate?.take(4)?.toIntOrNull(),
                        views = null,
                        duration = show.duration?.let { kotlin.time.Duration.parseIsoString("PT${it}M") },
                        countries = show.countries
                    ),
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
                        filmanRating = movie.voteAverage?.let { Rating(it.toFloat(), 10f) },
                        imdbRating = movie.voteAverage?.let { Rating(it.toFloat(), 10f) },
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
                    categories = movie.genres.map { CategoryInfo(name = it.name, url = it.id.toString(), id = it.id) },
                    metaInfo = MediaMetadata(
                        year = movie.releaseDate?.take(4)?.toIntOrNull(),
                        views = null,
                        duration = movie.duration?.let { kotlin.time.Duration.parseIsoString("PT${it}M") },
                        countries = movie.countries
                    ),
                    similarMovies = emptyList()
                )
            }
    }
"""
content = re.sub(r'    suspend fun getMediaDetails\(mediaUrlRaw: String, isTvShow: Boolean\? = null\): DetailedMedia\? \{[\s\S]*?\n    \}', get_media.strip(), content)

get_actor = """
    suspend fun getActorDetails(actorUrl: String, page: Int = 1): ActorDetails? {
        val id = actorUrl.toIntOrNull() ?: return null
        val actorDetails = api.getActorDetails(id)
        val actorMovies = api.getActorMovies(id)

        return ActorDetails(
            name = actorDetails.name,
            avatarUrl = if (actorDetails.profilePath != null) tmdbImageBaseUrl + actorDetails.profilePath else "",
            birthDate = actorDetails.birthday,
            birthPlace = actorDetails.placeOfBirth,
            height = null,
            description = actorDetails.biography,
            filmwebRating = null,
            moviesDirector = emptyList(),
            moviesWriter = emptyList(),
            moviesCast = actorMovies.map { movie ->
                MovieItem(
                    url = movie.id.toString(),
                    detailsRequest = DetailsRequest.Url(movie.id.toString(), false),
                    titlePl = movie.title,
                    posterUrl = if (movie.posterPath != null) tmdbImageBaseUrl + movie.posterPath else "",
                    backgroundUrl = if (movie.backdropPath != null) tmdbImageBaseUrl + movie.backdropPath else "",
                    description = movie.overview,
                    year = movie.releaseDate?.take(4)?.toIntOrNull(),
                    filmanRating = movie.voteAverage?.let { Rating(it.toFloat(), 10f) },
                    imdbRating = movie.voteAverage?.let { Rating(it.toFloat(), 10f) },
                    isTvShow = false
                )
            }
        )
    }
"""

content = re.sub(r'    suspend fun getActorDetails\(actorUrl: String, page: Int = 1\): ActorDetails\? \{[\s\S]*?\n    \}', get_actor.strip(), content)

with open("core/data/src/main/java/com/pointlessapps/filman/data/api/FilmanRepository.kt", "w") as f:
    f.write(content)
