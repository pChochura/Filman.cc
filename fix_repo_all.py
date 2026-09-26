import re
with open("core/data/src/main/java/com/pointlessapps/filman/data/api/FilmanRepository.kt", "r") as f:
    content = f.read()

content = content.replace('url = if (it.mediaType == "TV_SHOW") "show_${it.id}" else "movie_${it.id}",', 'url = it.id.toString(),\n                        detailsRequest = DetailsRequest.Url(it.id.toString(), it.mediaType == "TV_SHOW"),')

content = content.replace('url = if (isTv) "show_${it.id}" else "movie_${it.id}",', 'url = it.id.toString(),\n                        detailsRequest = DetailsRequest.Url(it.id.toString(), isTv),')

content = content.replace('movies.add(MovieItem(\n                        url = it.id.toString(),', 'movies.add(MovieItem(\n                        url = it.id.toString(),\n                        detailsRequest = DetailsRequest.Url(it.id.toString(), true),')

content = content.replace('movies.add(MovieItem(\n                        url = it.id.toString(),', 'movies.add(MovieItem(\n                        url = it.id.toString(),\n                        detailsRequest = DetailsRequest.Url(it.id.toString(), false),')

# Oh wait, the previous two lines might mess up! Let's just fix them with regex.
# For TV shows block
content = re.sub(r'movies.add\(\s*MovieItem\(\s*url = it.id.toString\(\),\n                        titlePl = it.title,\n                        posterUrl = if \(it.posterPath != null\) tmdbImageBaseUrl \+ it.posterPath else "",\n                        backgroundUrl = if \(it.backdropPath != null\) tmdbImageBaseUrl \+ it.backdropPath else "",\n                        description = it.overview,\n                        year = it.firstAirDate\?\.take\(4\)\?\.toIntOrNull\(\),\n                        isTvShow = true\n                    \)\)', r'movies.add(MovieItem(\n                        url = it.id.toString(),\n                        detailsRequest = DetailsRequest.Url(it.id.toString(), true),\n                        titlePl = it.title,\n                        posterUrl = if (it.posterPath != null) tmdbImageBaseUrl + it.posterPath else "",\n                        backgroundUrl = if (it.backdropPath != null) tmdbImageBaseUrl + it.backdropPath else "",\n                        description = it.overview,\n                        year = it.firstAirDate?.take(4)?.toIntOrNull(),\n                        isTvShow = true\n                    ))', content)

# For Movies block
content = re.sub(r'movies.add\(\s*MovieItem\(\s*url = it.id.toString\(\),\n                        titlePl = it.title,\n                        posterUrl = if \(it.posterPath != null\) tmdbImageBaseUrl \+ it.posterPath else "",\n                        backgroundUrl = if \(it.backdropPath != null\) tmdbImageBaseUrl \+ it.backdropPath else "",\n                        description = it.overview,\n                        year = it.releaseDate\?\.take\(4\)\?\.toIntOrNull\(\),\n                        isTvShow = false\n                    \)\)', r'movies.add(MovieItem(\n                        url = it.id.toString(),\n                        detailsRequest = DetailsRequest.Url(it.id.toString(), false),\n                        titlePl = it.title,\n                        posterUrl = if (it.posterPath != null) tmdbImageBaseUrl + it.posterPath else "",\n                        backgroundUrl = if (it.backdropPath != null) tmdbImageBaseUrl + it.backdropPath else "",\n                        description = it.overview,\n                        year = it.releaseDate?.take(4)?.toIntOrNull(),\n                        isTvShow = false\n                    ))', content)

with open("core/data/src/main/java/com/pointlessapps/filman/data/api/FilmanRepository.kt", "w") as f:
    f.write(content)
