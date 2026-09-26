with open("core/data/src/main/java/com/pointlessapps/filman/data/api/FilmanRepository.kt", "r") as f:
    content = f.read()

# Fix Season/EpisodeLink
season_str = """val mappedSeasons = show.seasons.map { s ->
                    com.pointlessapps.filman.data.model.Season(
                        name = s.name,
                        episodes = s.episodes.map { ep ->
                            com.pointlessapps.filman.data.model.EpisodeLink(
                                title = ep.name,
                                url = "${show.id}_${s.seasonNumber}_${ep.episodeNumber}"
                            )
                        }
                    )
                }"""

import re
content = re.sub(r'val mappedSeasons = show.seasons.map \{[\s\S]*?\}\n\s*\}', season_str, content)

# Fix ActorDetails
actor_str = """return ActorDetails(
            name = "Not Supported",
            avatarUrl = "",
            birthDate = null,
            birthPlace = null,
            height = null,
            description = null,
            filmwebRating = null,
            moviesDirector = emptyList(),
            moviesWriter = emptyList(),
            moviesCast = emptyList()
        )"""

content = re.sub(r'return ActorDetails\(ActorInfo\(actorUrl, "Not Supported", ""\), emptyList\(\)\)', actor_str, content)

with open("core/data/src/main/java/com/pointlessapps/filman/data/api/FilmanRepository.kt", "w") as f:
    f.write(content)
