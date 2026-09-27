import re
with open("core/data/src/main/java/com/pointlessapps/filman/data/api/dto/ServerDtos.kt", "r") as f:
    content = f.read()

actor_detail = """
@Serializable
data class ActorDetail(
    val id: Int,
    val name: String,
    val biography: String? = null,
    val profilePath: String? = null,
    val knownForDepartment: String? = null,
    val birthday: String? = null,
    val deathday: String? = null,
    val placeOfBirth: String? = null
)
"""

if "ActorDetail" not in content:
    content += "\n" + actor_detail.strip() + "\n"

with open("core/data/src/main/java/com/pointlessapps/filman/data/api/dto/ServerDtos.kt", "w") as f:
    f.write(content)
