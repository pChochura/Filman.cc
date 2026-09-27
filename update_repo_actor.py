import re
with open("core/data/src/main/java/com/pointlessapps/filman/data/api/FilmanRepository.kt", "r") as f:
    content = f.read()

get_actor = """
    suspend fun getActorDetails(actorUrl: String, page: Int = 1): ActorDetails? {
        val id = actorUrl.toIntOrNull() ?: return null
        return try {
            val actorDetails = api.getActorDetails(id)
            val actorMovies = api.getActorMovies(id)

            ActorDetails(
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
        } catch (e: Exception) {
            Log.e("FilmanRepository", "Error getting actor details", e)
            null
        }
    }
"""

content = re.sub(r'    suspend fun getActorDetails\(actorUrl: String, page: Int = 1\): ActorDetails \{[\s\S]*?\n    \}', get_actor.strip(), content)

with open("core/data/src/main/java/com/pointlessapps/filman/data/api/FilmanRepository.kt", "w") as f:
    f.write(content)
