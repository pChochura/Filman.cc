import re
with open("core/data/src/main/java/com/pointlessapps/filman/data/api/FilmanRepository.kt", "r") as f:
    content = f.read()

get_category = """
    suspend fun getCategoryPage(path: String, page: Int = 1): PageResult {
        return try {
            val featuredItems = mutableListOf<MovieItem>()
            val movies = mutableListOf<MovieItem>()
            val genreId = if (path.contains("category:")) path.substringAfter("category:").substringBefore("/").toIntOrNull() else null

            if (genreId != null) {
                val res = api.search(genreId = genreId, page = page)
                res.results.forEach {
                    val isTv = it.mediaType == "TV_SHOW"
                    val item = MovieItem(
                        url = it.id.toString(),
                        detailsRequest = DetailsRequest.Url(
                            it.id.toString(),
                            isTv,
                        ),
                        titlePl = it.title,
                        posterUrl = if (it.posterPath != null) tmdbImageBaseUrl + it.posterPath else "",
                        backgroundUrl = if (it.backdropPath != null) tmdbImageBaseUrl + it.backdropPath else "",
                        description = it.overview,
                        isTvShow = isTv,
                    )
                    movies.add(item)
                }
            } else if (path.contains("seriale") || path.contains("tv_shows")) {
                val res = api.getFeaturedShows(page)
                res.results.forEach {
                    movies.add(
                        MovieItem(
                            url = it.id.toString(),
                            detailsRequest = DetailsRequest.Url(it.id.toString(), true),
                            titlePl = it.title,
                            posterUrl = if (it.posterPath != null) tmdbImageBaseUrl + it.posterPath else "",
                            backgroundUrl = if (it.backdropPath != null) tmdbImageBaseUrl + it.backdropPath else "",
                            description = it.overview,
                            year = it.firstAirDate?.take(4)?.toIntOrNull(),
                            isTvShow = true,
                        ),
                    )
                }
            } else if (path.contains("filmy") || path.contains("movies")) {
                val res = api.getFeaturedMovies(page)
                res.results.forEach {
                    movies.add(
                        MovieItem(
                            url = it.id.toString(),
                            detailsRequest = DetailsRequest.Url(it.id.toString(), false),
                            titlePl = it.title,
                            posterUrl = if (it.posterPath != null) tmdbImageBaseUrl + it.posterPath else "",
                            backgroundUrl = if (it.backdropPath != null) tmdbImageBaseUrl + it.backdropPath else "",
                            description = it.overview,
                            year = it.releaseDate?.take(4)?.toIntOrNull(),
                            isTvShow = false,
                        ),
                    )
                }
            } else {
                val res = api.getFeaturedAll(page)
                res.results.forEach {
                    val isTv = it.mediaType == "TV_SHOW"
                    val item = MovieItem(
                        url = it.id.toString(),
                        detailsRequest = DetailsRequest.Url(
                            it.id.toString(),
                            isTv,
                        ),
                        titlePl = it.title,
                        posterUrl = if (it.posterPath != null) tmdbImageBaseUrl + it.posterPath else "",
                        backgroundUrl = if (it.backdropPath != null) tmdbImageBaseUrl + it.backdropPath else "",
                        description = it.overview,
                        isTvShow = isTv,
                    )
                    movies.add(item)
                    if (featuredItems.size < 5) {
                        featuredItems.add(item)
                    }
                }
            }

            PageResult(featuredItems, movies, path = path)
"""

content = re.sub(r'    suspend fun getCategoryPage\(path: String, page: Int = 1\): PageResult \{[\s\S]*?PageResult\(featuredItems, movies, path = path\)', get_category.strip(), content)

with open("core/data/src/main/java/com/pointlessapps/filman/data/api/FilmanRepository.kt", "w") as f:
    f.write(content)
