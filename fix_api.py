import re
with open("core/data/src/main/java/com/pointlessapps/filman/data/api/FilmanApi.kt", "r") as f:
    content = f.read()

import_str = "import com.pointlessapps.filman.data.api.dto.LinksResponse\nimport com.pointlessapps.filman.data.api.dto.Genre\n"
content = content.replace("import com.pointlessapps.filman.data.api.dto.LinksResponse\n", import_str)

fun_str = """
    suspend fun getTvShowDetails(id: Int): TvShow = get("/shows/$id/details")
    suspend fun getCategories(): List<Genre> = get("/search/categories")
"""
content = content.replace('    suspend fun getTvShowDetails(id: Int): TvShow = get("/shows/$id/details")', fun_str.strip())

with open("core/data/src/main/java/com/pointlessapps/filman/data/api/FilmanApi.kt", "w") as f:
    f.write(content)
