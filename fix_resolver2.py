import re
with open("core/data/src/main/java/com/pointlessapps/filman/data/api/VideoUrlResolver.kt", "r") as f:
    content = f.read()

get_alt = """
    fun getAlternativeUrls(mediaUrl: String, detailedMedia: DetailedMedia? = null): List<ExtractedVideo> {
        val embeds = detailedMedia?.embeds ?: return emptyList()
        return embeds.map { 
            ExtractedVideo(
                url = it.url,
                serverName = it.serverName,
                sourceWebsite = it.sourceWebsite
            )
        }
    }
"""

content = re.sub(r'    suspend fun getAlternativeUrls\([\s\S]*?    \}', get_alt.strip(), content)

with open("core/data/src/main/java/com/pointlessapps/filman/data/api/VideoUrlResolver.kt", "w") as f:
    f.write(content)
