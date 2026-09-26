import re
with open("core/data/src/main/java/com/pointlessapps/filman/data/api/FilmanApi.kt", "r") as f:
    content = f.read()

import_str = "import com.pointlessapps.filman.data.api.dto.TvShow\nimport com.pointlessapps.filman.data.api.dto.LinksResponse\n"
content = content.replace("import com.pointlessapps.filman.data.api.dto.TvShow", import_str)

fun_str = "suspend fun getMovieDetails(id: Int): Movie = get(\"/movies/$id/details\")\n    suspend fun getMovieLinks(id: Int): LinksResponse = get(\"/movies/$id/links\")\n    suspend fun getTvShowDetails(id: Int): TvShow = get(\"/shows/$id/details\")\n"

content = re.sub(r'suspend fun getMovieDetails\(id: Int\): Movie = get\("/movies/\$id/details"\)\n    suspend fun getTvShowDetails\(id: Int\): TvShow = get\("/shows/\$id/details"\)', fun_str, content)

with open("core/data/src/main/java/com/pointlessapps/filman/data/api/FilmanApi.kt", "w") as f:
    f.write(content)
