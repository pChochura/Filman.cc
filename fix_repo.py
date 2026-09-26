import re
with open("core/data/src/main/java/com/pointlessapps/filman/data/api/FilmanRepository.kt", "r") as f:
    content = f.read()

content = content.replace("import com.pointlessapps.filman.data.api.dto.*", "import com.pointlessapps.filman.data.api.dto.LinksResponse")

with open("core/data/src/main/java/com/pointlessapps/filman/data/api/FilmanRepository.kt", "w") as f:
    f.write(content)
