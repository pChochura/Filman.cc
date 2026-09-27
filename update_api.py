import re
with open("core/data/src/main/java/com/pointlessapps/filman/data/api/FilmanApi.kt", "r") as f:
    content = f.read()

# Add ActorDetail import
content = content.replace("import com.pointlessapps.filman.data.api.dto.Genre", "import com.pointlessapps.filman.data.api.dto.ActorDetail\nimport com.pointlessapps.filman.data.api.dto.Genre")

# Add the endpoints
endpoints = """
    suspend fun getCategories(): List<Genre> = get("/search/categories")
    
    suspend fun getActorDetails(id: Int): ActorDetail = get("/actors/$id/details")
    suspend fun getActorMovies(id: Int): List<Movie> = get("/actors/$id/movies")
"""
content = content.replace('suspend fun getCategories(): List<Genre> = get("/search/categories")', endpoints.strip())

with open("core/data/src/main/java/com/pointlessapps/filman/data/api/FilmanApi.kt", "w") as f:
    f.write(content)
