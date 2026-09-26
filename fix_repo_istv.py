import re
with open("core/data/src/main/java/com/pointlessapps/filman/data/api/FilmanRepository.kt", "r") as f:
    content = f.read()

get_media = """
    suspend fun getMediaDetails(mediaUrlRaw: String, isTvShow: Boolean? = null): DetailedMedia? {
        val id = mediaUrlRaw.substringBefore("_").toIntOrNull() ?: return null
        val tvShowFlag = isTvShow ?: mediaUrlRaw.contains("_")
        return try {
            if (tvShowFlag) {
"""

content = re.sub(r'    suspend fun getMediaDetails\(mediaUrlRaw: String, isTvShow: Boolean\? = null\): DetailedMedia\? \{\n        val id = mediaUrlRaw.toIntOrNull\(\) \?: return null\n        return try \{\n            if \(isTvShow == true\) \{', get_media.strip(), content)

get_links = """
    suspend fun getLinks(url: String, isTvShow: Boolean): LinksResponse? {
        val id = url.substringBefore("_").toIntOrNull() ?: return null
"""

content = re.sub(r'    suspend fun getLinks\(url: String, isTvShow: Boolean\): LinksResponse\? \{\n        val id = url.substringBefore\("_"\).toIntOrNull\(\) \?: return null', get_links.strip(), content)

with open("core/data/src/main/java/com/pointlessapps/filman/data/api/FilmanRepository.kt", "w") as f:
    f.write(content)
