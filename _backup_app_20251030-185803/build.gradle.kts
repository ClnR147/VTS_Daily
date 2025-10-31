plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.vtsdaily" // or your actual app package
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.vtsdaily"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        debug {
            // Normally no minify for debug
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.15" }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.00"))

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.navigation:navigation-compose:2.8.3")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    // If you have your DriverVans library module, depend on it here:
    // implementation(project(":drivervans"))

    implementation("com.google.code.gson:gson:2.11.0")
    implementation("net.sourceforge.jexcelapi:jxl:2.6.12")

    debugImplementation("androidx.compose.ui:ui-tooling")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.10.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

// --- Copy APK tasks (APP ONLY) ---
tasks.register<Copy>("copyDebugApkToCustomFolder") {
    val apkPath = "$buildDir/outputs/apk/debug/app-debug.apk"
    val destinationDir = file("C:/AutoSyncToPhone/PassengerSchedules")
    from(apkPath); into(destinationDir)
    doLast { println("✅ Debug APK copied to: $destinationDir") }
}

tasks.register<Copy>("copyReleaseApkToCustomFolder") {
    val apkPath = "$buildDir/outputs/apk/release/app-release.apk"
    val destinationDir = file("C:/AutoSyncToPhone/PassengerSchedules")
    from(apkPath); into(destinationDir)
    doLast { println("✅ Release APK copied to: $destinationDir") }
}

afterEvaluate {
    tasks.named("assembleDebug").configure { finalizedBy("copyDebugApkToCustomFolder") }
    tasks.named("assembleRelease").configure { finalizedBy("copyReleaseApkToCustomFolder") }
}
