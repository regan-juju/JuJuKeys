plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.reganbarua.jujukeys"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.reganbarua.jujukeys"
        minSdk = 24
        targetSdk = 34
        versionCode = (System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1)
        versionName = "1.0.${System.getenv("GITHUB_RUN_NUMBER") ?: "0"}"
        // Phones only (ARM) — keeps the APK small; ML Kit ships big native libraries.
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
            // CI only: the test emulator is x86_64
            if (project.hasProperty("withX86")) abiFilters += "x86_64"
        }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // A fixed signing key kept in the repo, so every new APK installs as an update
    // over the previous one (no uninstall needed).
    signingConfigs {
        create("jujukeys") {
            storeFile = rootProject.file("keystore/jujukeys.jks")
            storePassword = "jujukeys"
            keyAlias = "jujukeys"
            keyPassword = "jujukeys"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("jujukeys")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("jujukeys")
        }
    }

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
    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    // Installs Compose's ahead-of-time compile profiles on sideloaded APKs → much smoother
    implementation("androidx.profileinstaller:profileinstaller:1.3.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.6")
    implementation("androidx.savedstate:savedstate-ktx:1.2.1")

    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Offline translation (Google ML Kit — Google Translate's on-device models)
    implementation("com.google.mlkit:translate:17.0.3")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
