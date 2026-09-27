import re
with open("core/data/src/main/java/com/pointlessapps/filman/data/api/FilmanRepository.kt", "r") as f:
    content = f.read()

get_links = """
    suspend fun getLinks(url: String, isTvShow: Boolean): LinksResponse? {
        val idStr = url.removePrefix("show_").removePrefix("movie_").substringBefore("_")
        val id = idStr.toIntOrNull() ?: return null
        return try {
            if (!isTvShow) {
                api.getMovieLinks(id)
            } else {
                null
            }
        } catch (e: AuthException) {
            throw e
        } catch (e: Exception) {
            Log.e("FilmanRepository", "Error getting links", e)
            null
        }
    }
"""

content = re.sub(r'    suspend fun getLinks\(url: String, isTvShow: Boolean\): LinksResponse\? \{[\s\S]*?\n    \}', get_links.strip(), content)

with open("core/data/src/main/java/com/pointlessapps/filman/data/api/FilmanRepository.kt", "w") as f:
    f.write(content)
