// Every artifact this project needs is mirrored inside the mainland: the
// Aliyun repositories proxy both Google's Maven and Maven Central, so nothing
// has to reach dl.google.com or repo1.maven.org directly. The official
// repositories stay at the end as a fallback for anything a mirror has not
// picked up yet.
pluginManagement {
    repositories {
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
        maven("https://maven.aliyun.com/repository/central")
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
        maven("https://maven.aliyun.com/repository/central")
        google()
        mavenCentral()
    }
}

rootProject.name = "Kite"
include(":app")

// Contract generated from api/openapi.json — see docs/ROADMAP.md (M0/M1).
include(":api")
