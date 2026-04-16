pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

rootProject.name = "DMA"

include(
    ":core",
    ":parser",
    ":scheduler",
    ":tracking",
    ":network",
    ":ui-compose",
    ":player-common",
    ":player-media3",
    ":sample-app",
)

