import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.androidx.baselineprofile)
}

android {
    namespace = "com.pointlessapps.filman"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.pointlessapps.filman"
        minSdk = 24
        targetSdk = 37
        versionCode = 39
        versionName = "1.33"
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
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        aidl = false
        buildConfig = true
        shaders = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    jvmToolchain(17)
}

composeCompiler {
    stabilityConfigurationFiles.add(
        rootProject.layout.projectDirectory.file("compose_stability.txt"),
    )
}

dependencies {
    baselineProfile(project(":benchmark"))

    implementation(platform(libs.androidx.compose.bom))

    // Core Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.material.icons.extended)

    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.koin.compose.navigation3)

    // Bundles
    implementation(libs.bundles.androidx.lifecycle)
    implementation(libs.bundles.androidx.compose)
    implementation(libs.bundles.androidx.media3)
    implementation(libs.bundles.androidx.tv)

    // Tooling
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Third-party
    implementation(libs.coil.compose)
    implementation(libs.jsoup)
    implementation(libs.nanohttpd)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.newpipe.extractor)
    implementation(libs.zxing.core)
    testImplementation(libs.junit)
}
