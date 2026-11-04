import java.util.Base64

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")   // Kotlin 2.x Compose plugin
    id("com.google.gms.google-services")
    id("com.google.devtools.ksp")
}

android {
    namespace  = "com.rajib.tutorial"
    compileSdk = 35

    defaultConfig {
        applicationId   = "com.rajib.tutorial"
        minSdk          = 26
        targetSdk       = 35
        versionCode     = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionName     = "1.0.${System.getenv("GITHUB_RUN_NUMBER") ?: "0"}"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        create("fixedSigning") {
            val b64 = System.getenv("KEYSTORE_BASE64") ?: ""
            if (b64.isNotEmpty()) {
                // layout.buildDirectory — buildDir deprecation fix
                val ksFile = layout.buildDirectory.file("rasfocus_fixed.keystore").get().asFile
                ksFile.parentFile?.mkdirs()
                ksFile.writeBytes(Base64.getDecoder().decode(b64))
                storeFile     = ksFile
                storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "rasfocus123"
                keyAlias      = System.getenv("KEY_ALIAS")          ?: "rasfocus_debug"
                keyPassword   = System.getenv("KEY_PASSWORD")       ?: "rasfocus123"
            }
        }
    }

    buildTypes {
        debug {
            if ((System.getenv("KEYSTORE_BASE64") ?: "").isNotEmpty())
                signingConfig = signingConfigs.getByName("fixedSigning")
            // R8 shrinking — unused code বাদ দেয়, APK ছোট হয়
            isMinifyEnabled   = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        release {
            if ((System.getenv("KEYSTORE_BASE64") ?: "").isNotEmpty())
                signingConfig = signingConfigs.getByName("fixedSigning")
            isMinifyEnabled   = true
            isShrinkResources = true
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

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += "-Xjvm-default=all"
    }

    buildFeatures {
        compose     = true
        buildConfig = true
    }

    // Kotlin 2.x + compose plugin ব্যবহার করলে composeOptions লাগে না
    // (kotlinCompilerExtensionVersion আর manually দিতে হয় না)

    // ── Split APK by ABI ──────────────────────────────────────
    splits {
        abi {
            isEnable       = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
        }
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "/META-INF/LICENSE*",
                "/META-INF/NOTICE*",
                "/META-INF/*.kotlin_module"
            )
        }
    }
}

dependencies {
    // Core
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.activity:activity-ktx:1.10.0")

    // Compose BOM — 2025.01.00 is Kotlin 2.x compatible
    implementation(platform("androidx.compose:compose-bom:2025.01.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.animation:animation")

    // ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

    // AppCompat
    implementation("androidx.appcompat:appcompat:1.7.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.01.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
