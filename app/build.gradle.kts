import java.util.Properties

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
        // CI: the GitHub run number. Local builds: VERSION_CODE in gradle.properties (never lower
        // than the newest release, so an install over it is an update, not a "downgrade").
        val code = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()
            ?: (project.findProperty("VERSION_CODE") as String?)?.toIntOrNull() ?: 28
        versionCode = code
        versionName = "1.0.$code"
        // Phones only (ARM) — keeps the APK small; ML Kit ships big native libraries.
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
            // CI only: the test emulator is x86_64
            if (project.hasProperty("withX86")) abiFilters += "x86_64"
        }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // No key in the repository. Releases are signed by CI (ci/sign_and_variants.sh) with the
    // private key from GitHub Secrets (key rotation). For a local signed build, put
    // JUJU_STORE_FILE / JUJU_STORE_PASSWORD / JUJU_KEY_ALIAS / JUJU_KEY_PASSWORD in
    // local.properties or the environment; otherwise the release APK is left UNSIGNED.
    val localProps = Properties().apply {
        val f = rootProject.file("local.properties"); if (f.exists()) f.inputStream().use { load(it) }
    }
    fun secret(name: String): String? = System.getenv(name) ?: localProps.getProperty(name)
    val storePath = secret("JUJU_STORE_FILE")
    signingConfigs {
        if (storePath != null) create("jujukeys") {
            storeFile = file(storePath)
            storePassword = secret("JUJU_STORE_PASSWORD")
            keyAlias = secret("JUJU_KEY_ALIAS")
            keyPassword = secret("JUJU_KEY_PASSWORD")
        }
    }

    buildTypes {
        debug {
            // Android's own debug key (never the release key)
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("jujukeys")   // null → unsigned (CI signs)
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
