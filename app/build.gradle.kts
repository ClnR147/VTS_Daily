// app/build.gradle.kts

import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.artifact.SingleArtifact
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization") // ✅ add this
    id("org.jetbrains.kotlin.plugin.compose")

}

android {
    namespace = "com.example.vtsdaily"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.vtsdaily"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // AGP 8.x -> use Java 17
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Compose BOM (single source of truth)
    implementation(platform("androidx.compose:compose-bom:2026.02.00"))

    // Core / lifecycle / activity
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.activity:activity-compose:1.9.3")

    // Compose UI + Material
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.3")
    implementation("androidx.compose.foundation:foundation") // stickyHeader, LazyColumn, etc.

    // Window size classes
    implementation("androidx.compose.material3:material3-window-size-class:1.3.0")
    implementation("androidx.window:window:1.3.0")

    // Other libs
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("net.sourceforge.jexcelapi:jxl:2.6.12")
    implementation("com.google.android.material:material:1.9.0") // only if using XML views
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("com.google.accompanist:accompanist-flowlayout:0.30.1")
    implementation("com.opencsv:opencsv:5.9")
    implementation("androidx.compose.ui:ui-unit:1.10.3")


    // Tests
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.10.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")

    // Debug
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Belt-and-suspenders: force window 1.3.0 if anything tries to pull 1.0.0
    constraints {
        implementation("androidx.window:window:1.3.0") {
            because("material3-window-size-class can pull window:1.0.0 transitively")
        }
    }
}

/**
 * Variant-agnostic APK copy:
 * - Registers copy<Variant>ApkToCustomFolder for every variant (debug, release, flavors…)
 * - Finalizes assemble<Variant> with that copy task
 */
val apkDropDir = file("C:/AutoSyncToPhone/PassengerSchedules")

extensions.configure<ApplicationAndroidComponentsExtension>("androidComponents") {
    onVariants(selector().all()) { variant ->
        val cap = variant.name.replaceFirstChar { it.uppercaseChar() }

        // Provider-backed artifact (AGP 8+). May be a file OR a directory.
        val apkArtifact = variant.artifacts.get(SingleArtifact.APK)

        // Copy after assemble<Variant>; search for *.apk (skip output-metadata.json)
        val copyTask = tasks.register("copy${cap}ApkToCustomFolder") {
            doLast {
                apkDropDir.mkdirs()

                val artifactFile = apkArtifact.get().asFile
                val base = if (artifactFile.isDirectory) artifactFile else artifactFile.parentFile

                val apkTree = project.fileTree(base) { include("**/*.apk") }
                val apkFiles = apkTree.files

                if (apkFiles.isEmpty()) {
                    logger.warn(
                        "⚠ No APKs found for variant '${variant.name}'. " +
                                "Did you run a bundle task? Build with assemble<Variant>."
                    )
                } else {
                    copy {
                        from(apkTree)
                        into(apkDropDir)
                        duplicatesStrategy = DuplicatesStrategy.INCLUDE
                    }
                    println("✅ Copied ${apkFiles.size} APK(s) for ${variant.name} to: $apkDropDir")
                }
            }
        }

        // Finalize ONLY assemble<Variant> so we run when an APK is built
        tasks.configureEach {
            if (name == "assemble$cap") {
                finalizedBy(copyTask)
            }
        }
    }
}
