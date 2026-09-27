import re
with open("core/data/src/main/java/com/pointlessapps/filman/data/api/FilmanApi.kt", "r") as f:
    content = f.read()

# Import SessionManager
if "import com.pointlessapps.filman.data.local.SessionManager" not in content:
    content = content.replace("import okhttp3.OkHttpClient", "import okhttp3.OkHttpClient\nimport com.pointlessapps.filman.data.local.SessionManager")

# Update constructor
content = content.replace("class FilmanApi(private val client: OkHttpClient) {", "class FilmanApi(private val client: OkHttpClient, private val sessionManager: SessionManager) {")

# Update get()
old_get = r'    private suspend inline fun <reified T> get\(path: String\): T = withContext\(Dispatchers\.IO\) \{\n        val url = "\$baseUrl\$path"\n        val request = Request\.Builder\(\)\.url\(url\)\.build\(\)\n'
new_get = """    private suspend inline fun <reified T> get(path: String): T = withContext(Dispatchers.IO) {
        val url = "$baseUrl$path"
        val requestBuilder = Request.Builder().url(url)
        val cookie = sessionManager.getCookie()
        if (!cookie.isNullOrEmpty()) {
            requestBuilder.header("X-Filman-Cookie", cookie)
        }
        val request = requestBuilder.build()
"""
content = re.sub(old_get, new_get, content)

with open("core/data/src/main/java/com/pointlessapps/filman/data/api/FilmanApi.kt", "w") as f:
    f.write(content)
