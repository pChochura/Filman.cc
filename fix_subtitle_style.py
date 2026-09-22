import sys

with open("core/data/src/main/java/com/pointlessapps/filman/data/model/SubtitleStylePreferences.kt", "r") as f:
    content = f.read()

content = content.replace("import androidx.media3.ui.CaptionStyleCompat", "")
content = content.replace("CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW", "2")

with open("core/data/src/main/java/com/pointlessapps/filman/data/model/SubtitleStylePreferences.kt", "w") as f:
    f.write(content)
