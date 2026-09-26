import re
with open("feature/details/src/main/java/com/pointlessapps/filman/ui/details/MovieDetailsViewModel.kt", "r") as f:
    content = f.read()

find_str = """                                            com.pointlessapps.filman.data.model.EmbedLink(
                                                title = "${it.sourceWebsite} ${it.quality} ${it.language}".trim(),
                                                url = it.url
                                            )"""
rep_str = """                                            com.pointlessapps.filman.data.model.EmbedLink(
                                                url = it.url,
                                                serverName = "${it.sourceWebsite} ${it.language}".trim(),
                                                quality = it.quality,
                                                sourceWebsite = it.sourceWebsite
                                            )"""

content = content.replace(find_str, rep_str)

with open("feature/details/src/main/java/com/pointlessapps/filman/ui/details/MovieDetailsViewModel.kt", "w") as f:
    f.write(content)
