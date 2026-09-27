import re
with open("core/data/src/main/java/com/pointlessapps/filman/data/api/FilmanRepository.kt", "r") as f:
    content = f.read()

# Fix getMediaDetails
get_media_try = r'        return try \{\n            if \(tvShowFlag\) \{'
get_media_replace = r'        if (tvShowFlag) {'

content = re.sub(get_media_try, get_media_replace, content)

get_media_catch = r'        \} catch \(e: Exception\) \{\n            Log.e\("FilmanRepository", "Error during API call", e\)\n            null\n        \}'
content = re.sub(get_media_catch, '', content)

# Fix getActorDetails
get_actor_try = r'        return try \{\n            val actorDetails = api.getActorDetails\(id\)'
get_actor_replace = r'        val actorDetails = api.getActorDetails(id)'

content = re.sub(get_actor_try, get_actor_replace, content)

get_actor_catch = r'        \} catch \(e: Exception\) \{\n            Log.e\("FilmanRepository", "Error getting actor details", e\)\n            null\n        \}'
content = re.sub(get_actor_catch, '', content)

with open("core/data/src/main/java/com/pointlessapps/filman/data/api/FilmanRepository.kt", "w") as f:
    f.write(content)
