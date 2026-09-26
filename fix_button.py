import re
with open("feature/details/src/main/java/com/pointlessapps/filman/ui/details/MovieDetailsViewModel.kt", "r") as f:
    content = f.read()

rep_str = """    data object Unavailable : WatchButtonState {
        override val url: String = ""
    }

    data object Loading : WatchButtonState {
        override val url: String = ""
    }"""
content = content.replace('    data object Unavailable : WatchButtonState {\n        override val url: String = ""\n    }', rep_str)

with open("feature/details/src/main/java/com/pointlessapps/filman/ui/details/MovieDetailsViewModel.kt", "w") as f:
    f.write(content)
