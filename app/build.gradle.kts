// app/build.gradle.kts

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Capture catalog versions at top-level (safe in any scope below)
val composeCompilerVersion = libs.versions.composeCompiler.get()

android {
    namespace = "com.example.vtsdaily"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.vtsdaily"
        minSdk = 26
        targetSdk = 34
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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures { compose = true }

    composeOptions {
        // Use captured version to avoid 'implicit receiver' error
        kotlinCompilerExtensionVersion = composeCompilerVersion
    }

    kotlinOptions { jvmTarget = "17" }

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

dependencies {
    // --- Kotlin coroutines ---
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // --- Core / lifecycle / activity ---
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // --- Room (KSP) ---
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // --- Compose (managed by BOM) ---
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material)
    implementation(libs.androidx.compose.icons)
    implementation(libs.androidx.compose.material3)

    // --- Other libs ---
    implementation(libs.constraintlayout)
    implementation(libs.material.components)
    implementation(libs.gson)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.accompanist.flowlayout)
    implementation(libs.jexcelapi)

    // --- Tests ---
    testImplementation(libs.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.test.monitor)

    // --- Dev-only compose tooling ---
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

/*
// Optional: prevent RC core artifacts from sneaking in (uses captured or project.libs)
val coreKtxVersion = libs.versions.coreKtx.get()
configurations.all {
    resolutionStrategy {
        force("androidx.core:core-ktx:$coreKtxVersion")
    }
}
*/

// ====== Your custom APK copy tasks (unchanged) ======

tasks.register<Copy>("copyDebugApkToCustomFolder") {
    val apkPath = "$buildDir/outputs/apk/debug/app-debug.apk"
    val destinationDir = file("C:/AutoSyncToPhone/PassengerSchedules")

    from(apkPath)
    into(destinationDir)

    doLast { println("✅ Debug APK copied to: $destinationDir") }
}

tasks.register<Copy>("copyReleaseApkToCustomFolder") {
    val apkPath = "$buildDir/outputs/apk/release/app-release.apk"
    val destinationDir = file("C:/AutoSyncToPhone/PassengerSchedules")

    from(apkPath)
    into(destinationDir)

    doLast { println("✅ Release APK copied to: $destinationDir") }
}

afterEvaluate {
    tasks.named("assembleDebug").configure { finalizedBy("copyDebugApkToCustomFolder") }
    tasks.named("assembleRelease").configure { finalizedBy("copyReleaseApkToCustomFolder") }
}
