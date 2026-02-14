pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }

    // 🔥 DAS WAR DER FEHLENDE TEIL
    plugins {
        id("com.android.application") version "8.13.2"
        id("com.android.test") version "8.13.2"
        id("org.jetbrains.kotlin.android") version "1.9.23"
        // Baseline Profile Gradle plugin (Jetpack)
        id("androidx.baselineprofile") version "1.4.1"
        // add:
        id("com.android.test") version "8.13.2"
        id("androidx.baselineprofile") version "1.4.1"
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "TapSync"
include(":app")
include(":baselineprofile")
