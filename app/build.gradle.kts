plugins {
    alias(libs.plugins.android.application)
}

// Release signing comes from the environment (CI secrets) so no key material lives in the repo.
// Without it, release builds fall back to the debug key: fine for CI proof, never for Play.
val releaseKeystore: String? = System.getenv("ANDROID_KEYSTORE_PATH")

android {
    namespace = "com.backyardbrains.bybremote"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.backyardbrains.bybremote"
        minSdk = 23
        targetSdk = 36
        // CI release lanes pass -PversionCode=<100000 + run number>; local builds use 1.
        versionCode = (project.findProperty("versionCode") as String?)?.toInt() ?: 1
        versionName = "2.0.0"
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = if (releaseKeystore != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
    }
}

dependencies {
    implementation(libs.androidx.activity)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core)

    testImplementation(libs.junit)
}
