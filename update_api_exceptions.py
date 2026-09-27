import re
with open("core/data/src/main/java/com/pointlessapps/filman/data/api/FilmanApi.kt", "r") as f:
    content = f.read()

# Add AuthException
if "class AuthException" not in content:
    content = content.replace("class FilmanApi(", "class AuthException(message: String) : IOException(message)\n\nclass FilmanApi(")

# Update get() to throw AuthException on 401
old_get_check = r'        if \(!response\.isSuccessful\) \{\n            throw IOException\("Unexpected code \$response"\)\n        \}'
new_get_check = """        if (!response.isSuccessful) {
            if (response.code == 401) {
                throw AuthException("Unauthorized: ${response.message}")
            }
            throw IOException("Unexpected code $response")
        }"""
content = re.sub(old_get_check, new_get_check, content)

with open("core/data/src/main/java/com/pointlessapps/filman/data/api/FilmanApi.kt", "w") as f:
    f.write(content)
