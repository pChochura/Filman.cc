import re
with open("core/data/src/main/java/com/pointlessapps/filman/data/api/FilmanRepository.kt", "r") as f:
    content = f.read()

content = content.replace('val idStr = mediaUrlRaw.removePrefix("show_").removePrefix("movie_")\n        val id = idStr.toIntOrNull() ?: return null', 'val idStr = mediaUrlRaw.removePrefix("show_").removePrefix("movie_").substringBefore("_")\n        val id = idStr.toIntOrNull() ?: return null')

content = content.replace('val idStr = url.removePrefix("show_").removePrefix("movie_")\n        val id = idStr.toIntOrNull() ?: return null', 'val idStr = url.removePrefix("show_").removePrefix("movie_").substringBefore("_")\n        val id = idStr.toIntOrNull() ?: return null')

with open("core/data/src/main/java/com/pointlessapps/filman/data/api/FilmanRepository.kt", "w") as f:
    f.write(content)
