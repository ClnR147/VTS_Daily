pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("com.android.application") version "8.3.2"
        id("com.android.library")     version "8.3.2"
        kotlin("android")             version "1.9.23"
        kotlin("jvm")                 version "1.9.23"
        kotlin("plugin.serialization") version "1.9.23"
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "VTSDaily"
include(":app")
include(":drivervans")
