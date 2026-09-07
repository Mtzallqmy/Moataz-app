plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.chaquo.python")
}

android {
    namespace = "com.moataz.edge"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.moataz.edge"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.0"

        // Personal edge-node build: 64-bit ARM phones only.
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildFeatures {
        compose = true
    }

    buildTypes {
        getByName("release") {
            // Installable development release. Replace with a private release key before public distribution.
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

chaquopy {
    defaultConfig {
        version = "3.13"
        pip {
            // Bundled offline runtime pack: no pip/install step is needed on the phone.
            install("requests==2.32.5")
            install("beautifulsoup4==4.13.5")
            install("feedparser==6.0.12")
            install("python-dateutil==2.9.0.post0")
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.04.01")
    implementation(composeBom)

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
