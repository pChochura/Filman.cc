import re
with open("feature/details/src/main/java/com/pointlessapps/filman/ui/details/MovieDetailsViewModel.kt", "r") as f:
    content = f.read()

state_repl = """@Immutable
data class MovieDetailsState(
    override val shared: SharedState = SharedState(),
    val mediaDetails: DetailedMedia? = null,
    val isExtractingLinks: Boolean = false,
"""
content = re.sub(r'@Immutable\ndata class MovieDetailsState\(\n    override val shared: SharedState = SharedState\(\),\n    val mediaDetails: DetailedMedia\? = null,\n', state_repl, content)

with open("feature/details/src/main/java/com/pointlessapps/filman/ui/details/MovieDetailsViewModel.kt", "w") as f:
    f.write(content)
