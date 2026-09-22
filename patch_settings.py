with open("settings.gradle.kts", "r") as f:
    content = f.read()

content += '\ninclude(":core:data")\ninclude(":core:player")\n'

with open("settings.gradle.kts", "w") as f:
    f.write(content)
