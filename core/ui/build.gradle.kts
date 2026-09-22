plugins {
    id("com.android.library")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.pointlessapps.filman.core.ui"
    compileSdk = 37
    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
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
    api(project(":core:data"))
    
    implementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.material.icons.extended)

    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    implementation(libs.bundles.androidx.lifecycle)
    implementation(libs.bundles.androidx.compose)
    implementation(libs.bundles.androidx.tv)

    implementation(libs.coil.compose)
    implementation(libs.zxing.core)
}
