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
    ":player-exoplayer2",
    ":sample-app",
)

