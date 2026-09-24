plugins {
    alias(libs.plugins.android.application)
}

val javaToolchainVersion = libs.versions.javaToolchain.get()
val appVersion = libs.versions.appVersion.get()
val releaseKeystorePath = providers.environmentVariable("EMBERR_RELEASE_KEYSTORE_FILE").orNull

fun versionCodeFor(version: String): Int {
    val (major, minor, patch) = version.split('.').map { it.toInt() }
    require(minor < 100 && patch < 100) { "appVersion $version needs minor and patch below 100 to fit versionCode" }
    return major * 10_000 + minor * 100 + patch
}

android {
    namespace = "com.emberr.app"
    compileSdk = libs.versions.compileSdk.get().toInt()

    signingConfigs {
        if (releaseKeystorePath != null) {
            create("release") {
                storeFile = file(releaseKeystorePath)
                storePassword = providers.environmentVariable("EMBERR_RELEASE_KEYSTORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("EMBERR_RELEASE_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("EMBERR_RELEASE_KEY_PASSWORD").get()
            }
        }
    }

    defaultConfig {
        applicationId = "com.emberr"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = versionCodeFor(appVersion)
        versionName = appVersion
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            if (releaseKeystorePath != null) {
                signingConfig = signingConfigs.getByName("release")
            }
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
