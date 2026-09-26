import re
with open("core/data/src/main/java/com/pointlessapps/filman/data/api/FilmanRepository.kt", "r") as f:
    content = f.read()

content = content.replace("detailsRequest = DetailsRequest.Url(it.id.toString(), false),\n                        detailsRequest = DetailsRequest.Url(it.id.toString(), true),", "detailsRequest = DetailsRequest.Url(it.id.toString(), true),")

# Wait, for movies it should be false!
content = re.sub(r'movies.add\(\s*MovieItem\(\s*url = it.id.toString\(\),\s*detailsRequest = DetailsRequest.Url\(it.id.toString\(\), true\),\s*titlePl = it.title,\s*posterUrl = if \(it.posterPath != null\) tmdbImageBaseUrl \+ it.posterPath else "",\s*backgroundUrl = if \(it.backdropPath != null\) tmdbImageBaseUrl \+ it.backdropPath else "",\s*description = it.overview,\s*year = it.releaseDate\?\.take\(4\)\?\.toIntOrNull\(\),\s*isTvShow = false\s*\)\)', r'movies.add(MovieItem(\n                        url = it.id.toString(),\n                        detailsRequest = DetailsRequest.Url(it.id.toString(), false),\n                        titlePl = it.title,\n                        posterUrl = if (it.posterPath != null) tmdbImageBaseUrl + it.posterPath else "",\n                        backgroundUrl = if (it.backdropPath != null) tmdbImageBaseUrl + it.backdropPath else "",\n                        description = it.overview,\n                        year = it.releaseDate?.take(4)?.toIntOrNull(),\n                        isTvShow = false\n                    ))', content)

with open("core/data/src/main/java/com/pointlessapps/filman/data/api/FilmanRepository.kt", "w") as f:
    f.write(content)
