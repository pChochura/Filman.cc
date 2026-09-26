with open("core/data/src/main/java/com/pointlessapps/filman/data/api/FilmanRepository.kt", "r") as f:
    content = f.read()

# Fix TvShow
content = content.replace(
    'url = if (it.mediaType == "TV_SHOW") "show_${it.id}" else "movie_${it.id}",\n                        titlePl = it.title,\n                        posterUrl = if (it.posterPath != null) tmdbImageBaseUrl + it.posterPath else "",\n                        backgroundUrl = if (it.backdropPath != null) tmdbImageBaseUrl + it.backdropPath else "",\n                        description = it.overview,\n                        year = it.firstAirDate?.take(4)?.toIntOrNull(),\n                        isTvShow = true',
    'url = "show_${it.id}",\n                        titlePl = it.title,\n                        posterUrl = if (it.posterPath != null) tmdbImageBaseUrl + it.posterPath else "",\n                        backgroundUrl = if (it.backdropPath != null) tmdbImageBaseUrl + it.backdropPath else "",\n                        description = it.overview,\n                        year = it.firstAirDate?.take(4)?.toIntOrNull(),\n                        isTvShow = true'
)

# Fix Movie
content = content.replace(
    'url = if (it.mediaType == "TV_SHOW") "show_${it.id}" else "movie_${it.id}",\n                        titlePl = it.title,\n                        posterUrl = if (it.posterPath != null) tmdbImageBaseUrl + it.posterPath else "",\n                        backgroundUrl = if (it.backdropPath != null) tmdbImageBaseUrl + it.backdropPath else "",\n                        description = it.overview,\n                        year = it.releaseDate?.take(4)?.toIntOrNull(),\n                        isTvShow = false',
    'url = "movie_${it.id}",\n                        titlePl = it.title,\n                        posterUrl = if (it.posterPath != null) tmdbImageBaseUrl + it.posterPath else "",\n                        backgroundUrl = if (it.backdropPath != null) tmdbImageBaseUrl + it.backdropPath else "",\n                        description = it.overview,\n                        year = it.releaseDate?.take(4)?.toIntOrNull(),\n                        isTvShow = false'
)

with open("core/data/src/main/java/com/pointlessapps/filman/data/api/FilmanRepository.kt", "w") as f:
    f.write(content)
