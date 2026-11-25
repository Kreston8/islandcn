plugins {
    id("com.android.application")
}

// CI passes -PversionCode / -PversionName so every release build is unique.
val appVersionCode = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 1
val appVersionName = (project.findProperty("versionName") as String?) ?: "1.0.0"

android {
    namespace = "com.kreston8.islandcn"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.kreston8.islandcn"
        minSdk = 26
        targetSdk = 34
        versionCode = appVersionCode
        versionName = appVersionName
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            // Sideload build: signed with the debug key so it installs without a keystore in CI.
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
