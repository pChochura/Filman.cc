import sys

# Update app/build.gradle.kts
with open("app/build.gradle.kts", "r") as f:
    app_gradle = f.read()

# Remove the buildConfig fields from app
import re
app_gradle = re.sub(r'        val properties = Properties\(\)[\s\S]*?        val wyzieSubsApiKey = properties.getProperty\("WYZIE_SUBS_API_KEY", ""\)\n\n', '', app_gradle)
app_gradle = re.sub(r'            buildConfigField\("String", ".*?", ".*?"\)\n', '', app_gradle)

# Add dependencies
deps = """
    implementation(project(":core:data"))
    implementation(project(":core:player"))
"""
app_gradle = app_gradle.replace('    implementation(platform(libs.koin.bom))', deps + '    implementation(platform(libs.koin.bom))')

with open("app/build.gradle.kts", "w") as f:
    f.write(app_gradle)

# Update core/data/build.gradle.kts
with open("core/data/build.gradle.kts", "r") as f:
    data_gradle = f.read()

keys_block = """
        val properties = java.util.Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            properties.load(java.io.FileInputStream(localPropertiesFile))
        }
        val tmdbApiKey = properties.getProperty("TMDB_API_KEY", "")
        val openSubtitlesApiKey = properties.getProperty("OPEN_SUBTITLES_API_KEY", "")
        val wyzieSubsApiKey = properties.getProperty("WYZIE_SUBS_API_KEY", "")

        debug {
            buildConfigField("String", "TMDB_API_KEY", "\\"${tmdbApiKey}\\"")
            buildConfigField("String", "OPEN_SUBTITLES_API_KEY", "\\"${openSubtitlesApiKey}\\"")
            buildConfigField("String", "WYZIE_SUBS_API_KEY", "\\"${wyzieSubsApiKey}\\"")
        }
        release {
            buildConfigField("String", "TMDB_API_KEY", "\\"${tmdbApiKey}\\"")
            buildConfigField("String", "OPEN_SUBTITLES_API_KEY", "\\"${openSubtitlesApiKey}\\"")
            buildConfigField("String", "WYZIE_SUBS_API_KEY", "\\"${wyzieSubsApiKey}\\"")
        }
"""

data_gradle = data_gradle.replace("    buildFeatures {", "    buildTypes {" + keys_block + "    }\n\n    buildFeatures {")

with open("core/data/build.gradle.kts", "w") as f:
    f.write(data_gradle)
