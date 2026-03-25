plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.xmlpull.kxml2)

    testImplementation(kotlin("test"))
    testImplementation(libs.junit4)
    testImplementation(libs.coroutines.core)
}

