import re
with open("feature/details/src/main/java/com/pointlessapps/filman/ui/details/MovieDetailsViewModel.kt", "r") as f:
    content = f.read()

content = content.replace("private suspend fun fetchWithFallback(url: String): DetailedMedia? {", "private suspend fun fetchWithFallback(url: String, isTvShow: Boolean? = null): DetailedMedia? {")
content = content.replace("var details = repository.getMediaDetails(url)", "var details = repository.getMediaDetails(url, isTvShow)")
content = content.replace("val newDetails = repository.getMediaDetails(resolvedUrl)", "val newDetails = repository.getMediaDetails(resolvedUrl, isTvShow)")

content = content.replace("val details = fetchWithFallback(url) ?: continue", "val details = fetchWithFallback(url, false) ?: continue")
content = content.replace("val details = fetchWithFallback(url)", "val details = fetchWithFallback(url, request.isTvShow)")

# Also, there's `val request = request.url` in `DetailsRequest.Url -> {` block!
content = content.replace("val url = request.url\n                val current = currentState", "val url = request.url\n                val isTvShow = request.isTvShow\n                val current = currentState")
content = content.replace("val details = fetchWithFallback(url, request.isTvShow)", "val details = fetchWithFallback(url, isTvShow)")

with open("feature/details/src/main/java/com/pointlessapps/filman/ui/details/MovieDetailsViewModel.kt", "w") as f:
    f.write(content)
