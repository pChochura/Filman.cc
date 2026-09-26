import re
with open("core/data/src/main/java/com/pointlessapps/filman/data/api/dto/ServerDtos.kt", "r") as f:
    content = f.read()

actor_str = """
@Serializable
data class Actor(
    val id: Int,
    val name: String,
    val profilePath: String? = null,
    val character: String? = null
)

@Serializable
data class Genre(
    val id: Int,
    val name: String
)

@Serializable
data class VideoLink(
"""
content = content.replace("@Serializable\ndata class VideoLink(", actor_str)

movie_str = """    val releaseDate: String? = null,
    val voteAverage: Double? = null,
    val cast: List<Actor> = emptyList()
)"""
content = content.replace("    val releaseDate: String? = null,\n    val voteAverage: Double? = null\n)", movie_str)

tv_str = """    val voteAverage: Double? = null,
    val seasons: List<Season> = emptyList(),
    val cast: List<Actor> = emptyList()
)"""
content = content.replace("    val voteAverage: Double? = null,\n    val seasons: List<Season> = emptyList()\n)", tv_str)

with open("core/data/src/main/java/com/pointlessapps/filman/data/api/dto/ServerDtos.kt", "w") as f:
    f.write(content)
