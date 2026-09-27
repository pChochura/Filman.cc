import re
with open("core/data/src/main/java/com/pointlessapps/filman/data/api/dto/ServerDtos.kt", "r") as f:
    content = f.read()

movie_dto = """
@Serializable
data class Movie(
    val id: Int,
    val title: String,
    val overview: String,
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val releaseDate: String? = null,
    val voteAverage: Double? = null,
    val cast: List<Actor> = emptyList(),
    val genres: List<Genre> = emptyList(),
    val countries: List<String> = emptyList(),
    val duration: Int? = null
)
"""
content = re.sub(r'@Serializable\ndata class Movie\([\s\S]*?val cast: List<Actor> = emptyList\(\)\n\)', movie_dto.strip(), content)


show_dto = """
@Serializable
data class TvShow(
    val id: Int,
    val title: String,
    val overview: String,
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val firstAirDate: String? = null,
    val voteAverage: Double? = null,
    val seasons: List<Season> = emptyList(),
    val cast: List<Actor> = emptyList(),
    val genres: List<Genre> = emptyList(),
    val countries: List<String> = emptyList(),
    val duration: Int? = null
)
"""
content = re.sub(r'@Serializable\ndata class TvShow\([\s\S]*?val cast: List<Actor> = emptyList\(\)\n\)', show_dto.strip(), content)

with open("core/data/src/main/java/com/pointlessapps/filman/data/api/dto/ServerDtos.kt", "w") as f:
    f.write(content)
