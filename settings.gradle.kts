import java.net.URI

include(":smolvectordb")


pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com.android.*")
                includeGroupByRegex("com.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("java-library")
        id("org.jetbrains.kotlin.jvm") version "2.0.0"
        id("org.jetbrains.kotlin.plugin.serialization") version "2.0.0"
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = URI("https://jitpack.io") }
        maven { url = URI("https://oss.sonatype.org/content/repositories/snapshots/") }
    }
}

rootProject.name = "DrishtiAI"
include(":app")
include(":smollm")
include(":hf-model-hub-api")