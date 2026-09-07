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
        // Python 3.12 is the compatibility baseline for hosted bot repositories.
        version = "3.12"
        pip {
            // Core network / parsing pack.
            install("requests==2.32.5")
            install("beautifulsoup4==4.13.5")
            install("python-dateutil==2.9.0.post0")

            // Local PaaS bot runtime pack. All dependencies are resolved at APK build time.
            // Chaquopy's Android repository currently provides aiohttp 3.10.10 for Python 3.12/ARM64,
            // so the runtime is pinned to the newest Android wheel which is actually buildable.
            install("aiohttp==3.10.10")
            install("aiogram==3.31.0")
            install("SQLAlchemy==2.0.52")
            install("aiosqlite>=0.21,<1")
            install("pydantic-settings>=2.10,<3")
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
