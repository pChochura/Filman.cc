import re
with open("core/data/src/main/java/com/pointlessapps/filman/data/model/DetailsRequest.kt", "r") as f:
    content = f.read()

content = content.replace("data class Url(val url: String) : DetailsRequest", "data class Url(val url: String, val isTvShow: Boolean = false) : DetailsRequest")

with open("core/data/src/main/java/com/pointlessapps/filman/data/model/DetailsRequest.kt", "w") as f:
    f.write(content)
