import re
with open("core/data/src/main/java/com/pointlessapps/filman/data/api/FilmanRepository.kt", "r") as f:
    content = f.read()

get_links = """
    suspend fun getLinks(url: String, isTvShow: Boolean): LinksResponse? {
        val idStr = url.removePrefix("show_").removePrefix("movie_").substringBefore("_")
        val id = idStr.toIntOrNull() ?: return null
"""

content = re.sub(r'    suspend fun getLinks\(url: String, isTvShow: Boolean\): LinksResponse\? \{\n        val idStr = url.removePrefix\("show_"\).removePrefix\("movie_"\)\n        val id = idStr.toIntOrNull\(\) \?: return null', get_links.strip(), content)

with open("core/data/src/main/java/com/pointlessapps/filman/data/api/FilmanRepository.kt", "w") as f:
    f.write(content)
