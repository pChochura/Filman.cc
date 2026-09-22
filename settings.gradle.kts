pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
            }
        }
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "Filman"
include(":app")
include(":benchmark")

include(":core:data")
include(":core:player")

include(":core:ui")

include(":feature:actor")
include(":feature:details")
include(":feature:forkids")
include(":feature:home")
include(":feature:login")
include(":feature:movies")
include(":feature:player")
include(":feature:screensaver")
include(":feature:search")
include(":feature:tvshows")
include(":feature:watchhistory")