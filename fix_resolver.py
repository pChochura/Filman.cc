import re
with open("core/data/src/main/java/com/pointlessapps/filman/data/api/VideoUrlResolver.kt", "r") as f:
    content = f.read()

get_fastest = """
    suspend fun getFastest(
        mediaUrl: String,
        detailedMedia: DetailedMedia? = null,
        targetSettings: TvShowSourceSettings? = null
    ): ExtractedVideo? {
        val media = detailedMedia ?: repository.getMediaDetails(mediaUrl) ?: return null
        
        var embeds = media.embeds
        if (embeds.isEmpty()) {
            val linksResponse = repository.getLinks(mediaUrl, media.baseItem.isTvShow)
            if (linksResponse != null && linksResponse.links.isNotEmpty()) {
                embeds = linksResponse.links.map {
                    EmbedLink(
                        url = it.url,
                        serverName = "${it.sourceWebsite} ${it.language}".trim(),
                        quality = it.quality,
                        sourceWebsite = it.sourceWebsite
                    )
                }
            }
        }
        
        val bestEmbed = embeds.firstOrNull() ?: return null
        return ExtractedVideo(
            url = bestEmbed.url,
            serverName = bestEmbed.serverName,
            sourceWebsite = bestEmbed.serverName
        )
    }

    suspend fun getAlternativeUrls(mediaUrl: String, detailedMedia: DetailedMedia? = null): List<ExtractedVideo> {
        val media = detailedMedia ?: repository.getMediaDetails(mediaUrl) ?: return emptyList()
        var embeds = media.embeds
        if (embeds.isEmpty()) {
            val linksResponse = repository.getLinks(mediaUrl, media.baseItem.isTvShow)
            if (linksResponse != null && linksResponse.links.isNotEmpty()) {
                embeds = linksResponse.links.map {
                    EmbedLink(
                        url = it.url,
                        serverName = "${it.sourceWebsite} ${it.language}".trim(),
                        quality = it.quality,
                        sourceWebsite = it.sourceWebsite
                    )
                }
            }
        }
        
        return embeds.map { 
            ExtractedVideo(
                url = it.url,
                serverName = it.serverName,
                sourceWebsite = it.sourceWebsite
            )
        }
    }
"""

content = re.sub(r'    suspend fun getFastest\([\s\S]*?    fun getAlternativeUrls\([\s\S]*?    \}', get_fastest.strip(), content)

with open("core/data/src/main/java/com/pointlessapps/filman/data/api/VideoUrlResolver.kt", "w") as f:
    f.write(content)
