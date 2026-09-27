import re
with open("core/data/src/main/java/com/pointlessapps/filman/data/api/FilmanApi.kt", "r") as f:
    content = f.read()

search_old = r'    suspend fun search\(query: String, page: Int = 1\): PaginatedResponse<MediaSummary> =\n        get\("/search\?query=\$query&page=\$page"\)'
search_new = """    suspend fun search(query: String = "", genreId: Int? = null, page: Int = 1): PaginatedResponse<MediaSummary> {
        val url = StringBuilder("/search?page=$page")
        if (query.isNotEmpty()) url.append("&query=$query")
        if (genreId != null) url.append("&genreId=$genreId")
        return get(url.toString())
    }"""

content = re.sub(search_old, search_new, content)

with open("core/data/src/main/java/com/pointlessapps/filman/data/api/FilmanApi.kt", "w") as f:
    f.write(content)
