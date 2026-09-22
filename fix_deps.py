import sys

with open("core/data/build.gradle.kts", "r") as f:
    content = f.read()

content = content.replace("    implementation(libs.kotlinx.serialization.json)\n}", "    implementation(libs.kotlinx.serialization.json)\n    implementation(libs.newpipe.extractor)\n}")

with open("core/data/build.gradle.kts", "w") as f:
    f.write(content)
