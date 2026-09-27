import re
with open("feature/details/src/main/java/com/pointlessapps/filman/ui/details/MovieDetailsViewModel.kt", "r") as f:
    content = f.read()

# Replace launchHandled { ... } with finally block
old_launch = r'                            launchHandled \{\n                                updateState \{ it\.copy\(isExtractingLinks = true\) \}\n                                var isFinished = false\n                                while \(!isFinished\) \{'
new_launch = """                            launchHandled(
                                onError = {
                                    updateState { state -> state.copy(isExtractingLinks = false) }
                                    handleError(it)
                                }
                            ) {
                                updateState { it.copy(isExtractingLinks = true) }
                                try {
                                    var isFinished = false
                                    while (!isFinished) {"""

content = re.sub(old_launch, new_launch, content)

old_end = r'                                        if \(!isFinished\) \{\n                                            kotlinx\.coroutines\.delay\(2000\)\n                                        \}\n                                    \}\n                                \}\n                            \}\n                        \}'
new_end = """                                        if (!isFinished) {
                                            kotlinx.coroutines.delay(2000)
                                        }
                                    }
                                } finally {
                                    updateState { it.copy(isExtractingLinks = false) }
                                }
                            }
                        }"""

content = re.sub(old_end, new_end, content)

with open("feature/details/src/main/java/com/pointlessapps/filman/ui/details/MovieDetailsViewModel.kt", "w") as f:
    f.write(content)
