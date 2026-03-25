plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
}

allprojects {
    group = "tech.done.adsdk"
    version = "0.1.0-SNAPSHOT"
}

subprojects {
    // Keep configuration minimal and module-local; avoid cross-module Android leakage.
}
