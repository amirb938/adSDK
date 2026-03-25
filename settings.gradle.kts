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

rootProject.name = "AdSDK"

include(
    ":ad-sdk-core",
    ":ad-sdk-api",
    ":ad-sdk-parser",
    ":ad-sdk-scheduler",
    ":ad-sdk-tracking",
    ":ad-sdk-network",
    ":ad-sdk-ui-compose",
    ":ad-sdk-player-common",
    ":ad-sdk-player-media3",
    ":ad-sdk-player-exoplayer2",
    ":sample-app",
)

