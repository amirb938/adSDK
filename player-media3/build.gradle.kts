plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "tech.done.adsdk.player.media3"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":player-common"))
    implementation(project(":core"))
    implementation(project(":parser"))
    implementation(project(":scheduler"))
    implementation(project(":tracking"))
    implementation(project(":network"))
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.common)
    implementation(libs.media3.ui)
    implementation(libs.coroutines.android)

    testImplementation(libs.junit4)
}
