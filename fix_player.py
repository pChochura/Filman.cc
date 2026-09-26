import re
with open("feature/player/src/main/java/com/pointlessapps/filman/ui/player/PlayerViewModel.kt", "r") as f:
    content = f.read()

find_str = """            val tvShowKey = details.getTvShowKey()
            val savedSettings =
                if (tvShowKey != null) {
                    tvShowSettingsManager.getSettingsForTvShow(tvShowKey)
                } else {
                    null
                }

            videoUrlResolver.prefetch(url, detailedMedia)
            var extracted = videoUrlResolver.getFastest(url, targetSettings = savedSettings)"""

rep_str = """            val tvShowKey = details.getTvShowKey()
            val savedSettings =
                if (tvShowKey != null) {
                    tvShowSettingsManager.getSettingsForTvShow(tvShowKey)
                } else {
                    null
                }

            // Propagate links!
            val linksResponse = repository.getLinks(url, details.isTvShow)
            if (linksResponse != null && linksResponse.links.isNotEmpty()) {
                val mappedEmbeds = linksResponse.links.map { 
                    com.pointlessapps.filman.data.model.EmbedLink(
                        url = it.url,
                        serverName = "${it.sourceWebsite} ${it.language}".trim(),
                        quality = it.quality,
                        sourceWebsite = it.sourceWebsite
                    )
                }
                detailedMedia = detailedMedia?.copy(embeds = mappedEmbeds)
            }

            videoUrlResolver.prefetch(url, detailedMedia)
            var extracted = videoUrlResolver.getFastest(url, detailedMedia = detailedMedia, targetSettings = savedSettings)"""

content = content.replace(find_str, rep_str)

with open("feature/player/src/main/java/com/pointlessapps/filman/ui/player/PlayerViewModel.kt", "w") as f:
    f.write(content)
