import sys

with open("settings.gradle.kts", "r") as f:
    content = f.read()

if ':core:ui' not in content:
    content += '\ninclude(":core:ui")\n'

with open("settings.gradle.kts", "w") as f:
    f.write(content)

with open("app/build.gradle.kts", "r") as f:
    app_gradle = f.read()

if ':core:ui' not in app_gradle:
    app_gradle = app_gradle.replace('implementation(project(":core:data"))', 'implementation(project(":core:data"))\n    implementation(project(":core:ui"))')

with open("app/build.gradle.kts", "w") as f:
    f.write(app_gradle)
