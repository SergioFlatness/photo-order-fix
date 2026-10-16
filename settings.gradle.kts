pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "photo-order-fix"

// Pure Kotlin logic (date detection, reorder planning). Its own build so it can be tested without Android.
includeBuild("core")
include(":app")
