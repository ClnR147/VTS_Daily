import com.android.build.api.variant.ApplicationAndroidComponentsExtension

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
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

    // Match your Kotlin/Compose toolchain; 1.5.11 pairs with modern AGP/Kotlin
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
    // Compose BOM (single source of truth)
    implementation(platform("androidx.compose:compose-bom:2024.10.00"))

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

    // Window size classes
    implementation("androidx.compose.material3:material3-window-size-class-android:1.3.0")
    implementation("androidx.window:window:1.3.0")

    // Other libs
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("net.sourceforge.jexcelapi:jxl:2.6.12")
    implementation("com.google.android.material:material:1.9.0") // only if using XML views
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("com.google.accompanist:accompanist-flowlayout:0.30.1")
    implementation("com.opencsv:opencsv:5.9")

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
 * - Finalizes assemble<Variant> or package<Variant> with that copy task
 */
val apkDropDir = file("C:/AutoSyncToPhone/PassengerSchedules")

extensions.configure<ApplicationAndroidComponentsExtension>("androidComponents") {
    onVariants(selector().all()) { variant ->
        val cap = variant.name.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

        // Copy any *.apk produced for this variant
        val copyTask = tasks.register<Copy>("copy${cap}ApkToCustomFolder") {
            val apkDir = layout.buildDirectory.dir("outputs/apk/${variant.name}")
            from(apkDir)
            include("*.apk")
            into(apkDropDir)
            doLast { println("✅ Copied APK(s) for ${variant.name} to: $apkDropDir") }
        }

        // Finalize assemble<Variant> if present
        tasks.matching { it.name == "assemble$cap" }.configureEach {
            finalizedBy(copyTask)
        }
        // Fallback: finalize package<Variant> (covers AGP naming differences)
        tasks.matching { it.name == "package$cap" }.configureEach {
            finalizedBy(copyTask)
        }
    }
}
