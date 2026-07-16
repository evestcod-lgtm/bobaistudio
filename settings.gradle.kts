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
        // Community mirror hosting FFmpegKit .aar binaries after the official
        // Maven Central artifacts were pulled on 2025-04-01 (upstream retired).
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "Bob AI Studio"
include(":app")
