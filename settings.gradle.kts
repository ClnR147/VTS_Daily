pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
    plugins {
        id("com.android.application") version "8.6.1" apply false
        id("com.android.library") version "8.6.1" apply false

        kotlin("android") version "2.3.0"
        kotlin("jvm") version "2.3.0"
        kotlin("plugin.serialization") version "2.3.0"
        id("org.jetbrains.kotlin.plugin.compose") version "2.3.0"
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}

rootProject.name = "VTSDaily"
include(":app", ":drivervans")

// 🔎 If your module lives in a nonstandard folder name/path, map it explicitly:
//// project(":drivervans").projectDir = file("modules/drivervans")  // <-- adjust if needed
