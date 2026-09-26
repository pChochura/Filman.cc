import re
with open("feature/screensaver/src/main/java/com/pointlessapps/filman/ui/screensaver/ScreensaverViewModel.kt", "r") as f:
    content = f.read()

replace_str = """
            val res = filmanScraper.getCategoryPage("")
            _movies.value = res.movies.map {
                TrendingMovie(
                    title = it.titlePl,
                    overview = it.description,
                    rating = 0.0,
                    posterUrl = it.posterUrl,
                    releaseYear = it.year
                )
            }
"""
content = re.sub(r'val res = filmanScraper\.getCategoryPage[\s\S]*?\}', replace_str.strip(), content)

with open("feature/screensaver/src/main/java/com/pointlessapps/filman/ui/screensaver/ScreensaverViewModel.kt", "w") as f:
    f.write(content)
