plugins {
    alias(libs.plugins.android.application)
}

val javaToolchainVersion = libs.versions.javaToolchain.get()

android {
    namespace = "com.emberr.app"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.emberr"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = libs.versions.appVersion.get()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            ndk {
                abiFilters += listOf("arm64-v8a", "armeabi-v7a")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(javaToolchainVersion)
        targetCompatibility = JavaVersion.toVersion(javaToolchainVersion)
    }

    androidResources {
        noCompress += listOf("so", "mdl", "fst", "conf", "int", "dubm", "ie", "mat", "stats", "gguf")
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(javaToolchainVersion.toInt()))
    }
}

dependencies {
    implementation(project(":shared"))
}
