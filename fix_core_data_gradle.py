import sys

with open("core/data/build.gradle.kts", "r") as f:
    content = f.read()

content = "import java.util.Properties\nimport java.io.FileInputStream\n\n" + content
content = content.replace("java.util.Properties()", "Properties()")
content = content.replace("java.io.FileInputStream(localPropertiesFile)", "FileInputStream(localPropertiesFile)")

with open("core/data/build.gradle.kts", "w") as f:
    f.write(content)
