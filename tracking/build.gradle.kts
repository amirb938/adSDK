plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.coroutines.core)
    implementation(project(":network"))
    testImplementation(kotlin("test"))
    testImplementation(libs.junit4)
}

