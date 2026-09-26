import re
with open("core/data/src/main/java/com/pointlessapps/filman/data/api/FilmanRepository.kt", "r") as f:
    content = f.read()

get_media = """
    suspend fun getMediaDetails(mediaUrlRaw: String): DetailedMedia? {
        val isExplicitShow = mediaUrlRaw.startsWith("show_")
        val isExplicitMovie = mediaUrlRaw.startsWith("movie_")
        val idStr = mediaUrlRaw.removePrefix("show_").removePrefix("movie_").substringBefore("_")
        val id = idStr.toIntOrNull() ?: return null
"""

content = re.sub(r'    suspend fun getMediaDetails\(mediaUrlRaw: String\): DetailedMedia\? \{\n        val isExplicitShow = mediaUrlRaw.startsWith\("show_"\)\n        val isExplicitMovie = mediaUrlRaw.startsWith\("movie_"\)\n        val idStr = mediaUrlRaw.removePrefix\("show_"\).removePrefix\("movie_"\)\n        val id = idStr.toIntOrNull\(\) \?: return null', get_media.strip(), content)

with open("core/data/src/main/java/com/pointlessapps/filman/data/api/FilmanRepository.kt", "w") as f:
    f.write(content)
