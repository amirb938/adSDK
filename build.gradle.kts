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

    // JitPack expects publishToMavenLocal to exist. Provide a default publication for
    // JVM and Android library modules, while skipping app modules.
    plugins.withId("org.jetbrains.kotlin.jvm") {
        apply(plugin = "maven-publish")

        extensions.configure<org.gradle.api.publish.PublishingExtension>("publishing") {
            publications {
                create<org.gradle.api.publish.maven.MavenPublication>("maven") {
                    from(components["java"])
                }
            }
        }
    }

    plugins.withId("com.android.library") {
        apply(plugin = "maven-publish")

        // Ensure the 'release' component exists for publishing.
        extensions.configure<com.android.build.gradle.LibraryExtension>("android") {
            publishing {
                singleVariant("release") {
                    withSourcesJar()
                }
            }
        }

        afterEvaluate {
            extensions.configure<org.gradle.api.publish.PublishingExtension>("publishing") {
                publications {
                    create<org.gradle.api.publish.maven.MavenPublication>("maven") {
                        from(components["release"])
                    }
                }
            }
        }
    }
}
