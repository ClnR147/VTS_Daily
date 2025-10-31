pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
    plugins {
        id("com.android.application") version "8.1.2"
        id("com.android.library") version "8.1.2"
        kotlin("android") version "1.9.23"
        kotlin("jvm") version "1.9.23"
        kotlin("plugin.serialization") version "1.9.23"
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
