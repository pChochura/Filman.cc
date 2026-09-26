import re
with open("feature/details/src/main/java/com/pointlessapps/filman/ui/details/MovieDetailsContent.kt", "r") as f:
    content = f.read()

rep_str = """
                    is WatchButtonState.Unavailable -> {
                        resources.getString(R.string.details_unavailable)
                    }

                    is WatchButtonState.Loading -> {
                        "Loading..." // Hardcoded for now
                    }
"""
content = content.replace("                    is WatchButtonState.Unavailable -> {\n                        resources.getString(R.string.details_unavailable)\n                    }", rep_str.strip())

# Make the button disabled when Loading
content = content.replace("isWatchButtonEnabled = state.watchButtonState !is WatchButtonState.Unavailable,", "isWatchButtonEnabled = state.watchButtonState !is WatchButtonState.Unavailable && state.watchButtonState !is WatchButtonState.Loading,")

with open("feature/details/src/main/java/com/pointlessapps/filman/ui/details/MovieDetailsContent.kt", "w") as f:
    f.write(content)
