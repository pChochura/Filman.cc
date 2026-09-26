import re
with open("feature/details/src/main/java/com/pointlessapps/filman/ui/details/MovieDetailsStateExt.kt", "r") as f:
    content = f.read()

find_str = """        if (!isSeries) {
            if (mediaDetails.embeds.isEmpty()) {
                return WatchButtonState.Unavailable
            }"""

rep_str = """        if (!isSeries) {
            if (mediaDetails.embeds.isEmpty()) {
                if (isExtractingLinks) return WatchButtonState.Loading
                return WatchButtonState.Unavailable
            }"""

content = content.replace(find_str, rep_str)

with open("feature/details/src/main/java/com/pointlessapps/filman/ui/details/MovieDetailsStateExt.kt", "w") as f:
    f.write(content)
