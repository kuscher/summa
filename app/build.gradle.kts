plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.kuscher.summa"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.kuscher.summa"
        minSdk = 31
        targetSdk = 37
        versionCode = 8
        versionName = "1.2.1"
    }

    // Release signing from ~/.config/summa (never committed). Absent -> unsigned release build.
    val keyDir = File(System.getProperty("user.home"), ".config/summa")
    val keyFile = File(keyDir, "keystore.jks")
    val keyPassFile = File(keyDir, "keystore.pass")
    signingConfigs {
        if (keyFile.exists() && keyPassFile.exists()) {
            create("release") {
                storeFile = keyFile
                val pw = keyPassFile.readText().trim()
                storePassword = pw
                keyAlias = "summa"
                keyPassword = pw
            }
        }
    }

    buildTypes {
        // Debug builds use the release key too, so a test install can replace a release (and the
        // other way round) without losing the sheets on the device.
        debug {
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += setOf("META-INF/*.version", "DebugProbesKt.bin", "kotlin-tooling-metadata.json")
    }
}

dependencies {
    implementation(project(":engine"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
}
