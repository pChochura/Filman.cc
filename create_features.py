import os
import shutil

features = [
    "actor",
    "details",
    "forkids",
    "home",
    "login",
    "movies",
    "player",
    "screensaver",
    "search",
    "tvshows",
    "watchhistory"
]

base_dir = "/Users/pipistrelus/AndroidStudioProjects/filman.cc"

build_gradle_template = """plugins {
    id("com.android.library")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.pointlessapps.filman.ui.%%NAME%%"
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
    api(project(":core:ui"))
    api(project(":core:data"))
%%EXTRA_DEPS%%

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

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
}
"""

settings_path = os.path.join(base_dir, "settings.gradle.kts")
with open(settings_path, "r") as f:
    settings = f.read()

app_build_path = os.path.join(base_dir, "app/build.gradle.kts")
with open(app_build_path, "r") as f:
    app_build = f.read()

for feat in features:
    # create dir
    feat_dir = os.path.join(base_dir, f"feature/{feat}")
    os.makedirs(feat_dir, exist_ok=True)
    
    # extra deps
    extra_deps = ""
    if feat == "player":
        extra_deps = '    api(project(":core:player"))\n    implementation(libs.androidx.media3.exoplayer)\n    implementation(libs.androidx.media3.ui)\n    implementation(libs.androidx.media3.session)'
    elif feat == "search":
        extra_deps = '    implementation(libs.androidx.tv.foundation)'
    
    # create build.gradle.kts
    with open(os.path.join(feat_dir, "build.gradle.kts"), "w") as f:
        f.write(build_gradle_template.replace("%%NAME%%", feat).replace("%%EXTRA_DEPS%%", extra_deps))
        
    # move source code
    src_dir = os.path.join(base_dir, f"app/src/main/java/com/pointlessapps/filman/ui/{feat}")
    dest_dir = os.path.join(base_dir, f"feature/{feat}/src/main/java/com/pointlessapps/filman/ui/{feat}")
    
    os.makedirs(os.path.dirname(dest_dir), exist_ok=True)
    if os.path.exists(src_dir):
        shutil.move(src_dir, dest_dir)
        
    # update settings
    if f'include(":feature:{feat}")' not in settings:
        settings += f'\ninclude(":feature:{feat}")'
        
    # update app dependencies
    if f'implementation(project(":feature:{feat}"))' not in app_build:
        app_build = app_build.replace('implementation(project(":core:ui"))', f'implementation(project(":core:ui"))\n    implementation(project(":feature:{feat}"))')

with open(settings_path, "w") as f:
    f.write(settings)
    
with open(app_build_path, "w") as f:
    f.write(app_build)
    
