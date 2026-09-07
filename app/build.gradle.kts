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
        versionCode = 4
        versionName = "0.4.0"

        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    buildTypes {
        getByName("release") {
            // Installable development release. Configure a private release keystore before public distribution.
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
        version = "3.12"
        pip {
            // Flet's mobile wheel index contains Android-tagged binary wheels for packages
            // such as pydantic-core. Dependencies are resolved only while building the APK;
            // nothing is installed from the network on the phone.
            options("--extra-index-url", "https://pypi.flet.dev")

            install("requests==2.32.5")
            install("beautifulsoup4==4.13.5")
            install("python-dateutil==2.9.0.post0")

            // Full Telegram service profile for Android 8+ / ARM64.
            // pydantic 2.11.7 pins pydantic-core 2.33.2, which has an android_24_arm64_v8a wheel.
            install("aiohttp==3.10.10")
            install("pydantic==2.11.7")
            install("pydantic-core==2.33.2")
            install("pydantic-settings==2.10.1")
            install("aiogram==3.20.0.post0")
            install("SQLAlchemy==2.0.52")
            install("aiosqlite>=0.21,<1")
            install("yt-dlp>=2026.8.19")
            install("jinja2>=3.1,<4")
            install("python-multipart>=0.0.20,<1")
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
