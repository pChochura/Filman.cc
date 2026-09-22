import sys

file = "app/src/main/java/com/pointlessapps/filman/ui/details/MovieDetailsStateExt.kt"
with open(file, "r") as f:
    content = f.read()

content = content.replace("baseItem.seasons?.flatMapIndexed { sIndex, season ->\n                season.episodes.mapIndexed { eIndex, episode ->\n                    Triple(sIndex + 1, eIndex + 1, episode.url)\n                }\n            }", "baseItem.seasons?.flatMapIndexed { sIndex, season ->\n                season.episodes.mapIndexed { eIndex, episode ->\n                    Triple(sIndex + 1, eIndex + 1, episode.url)\n                }\n            } ?: emptyList()")

with open(file, "w") as f:
    f.write(content)
