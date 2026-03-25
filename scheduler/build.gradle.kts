plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":parser"))
    testImplementation(kotlin("test"))
    testImplementation(libs.junit4)
}

