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

rootProject.name = "AdSDK"

include(
    ":core",
    ":api",
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

