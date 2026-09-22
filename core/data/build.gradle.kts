import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.library")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.parcelize)
}

android {
    namespace = "com.pointlessapps.filman.core.data"
    compileSdk = 37
    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    
    buildTypes {
        val properties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            properties.load(FileInputStream(localPropertiesFile))
        }
        val tmdbApiKey = properties.getProperty("TMDB_API_KEY", "")
        val openSubtitlesApiKey = properties.getProperty("OPEN_SUBTITLES_API_KEY", "")
        val wyzieSubsApiKey = properties.getProperty("WYZIE_SUBS_API_KEY", "")

        debug {
            buildConfigField("String", "TMDB_API_KEY", "\"${tmdbApiKey}\"")
            buildConfigField("String", "OPEN_SUBTITLES_API_KEY", "\"${openSubtitlesApiKey}\"")
            buildConfigField("String", "WYZIE_SUBS_API_KEY", "\"${wyzieSubsApiKey}\"")
        }
        release {
            buildConfigField("String", "TMDB_API_KEY", "\"${tmdbApiKey}\"")
            buildConfigField("String", "OPEN_SUBTITLES_API_KEY", "\"${openSubtitlesApiKey}\"")
            buildConfigField("String", "WYZIE_SUBS_API_KEY", "\"${wyzieSubsApiKey}\"")
        }
    }

    buildFeatures {
        buildConfig = true
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)

    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)

    implementation(libs.jsoup)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.newpipe.extractor)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
}
