plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.coroutines.core)
    implementation(project(":parser"))
    implementation(project(":scheduler"))
    implementation(project(":tracking"))
    implementation(project(":network"))
    implementation(project(":player-common"))

    testImplementation(kotlin("test"))
    testImplementation(libs.junit4)
}

