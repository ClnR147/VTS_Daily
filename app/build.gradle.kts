plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.serialization")
}

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

    // AGP 8.x -> use Java 17
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    // Match your Kotlin/Compose toolchain; 1.5.3 is safe with AGP 8.1.x + Kotlin 1.9.x
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.11"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.activity:activity-compose:1.7.2")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("net.sourceforge.jexcelapi:jxl:2.6.12")
    implementation("com.google.android.material:material:1.9.0")
    implementation("com.google.code.gson:gson:2.10.1")

    implementation(platform("androidx.compose:compose-bom:2023.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material:material:1.5.0")
    implementation("androidx.compose.material3:material3:1.2.1")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("com.google.accompanist:accompanist-flowlayout:0.30.1")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2023.10.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

// --- Copy APK tasks ---
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
