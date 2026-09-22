import sys

with open("core/data/build.gradle.kts", "r") as f:
    content = f.read()

deps = """
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
}
"""

content = content.replace("}", "") + deps

with open("core/data/build.gradle.kts", "w") as f:
    f.write(content)
