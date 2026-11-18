plugins {
    id("com.android.application")
}

val releaseKeystorePath = providers.environmentVariable("NOCLEARALL_KEYSTORE_PATH").orNull
val releaseKeystorePassword =
    providers.environmentVariable("NOCLEARALL_KEYSTORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("NOCLEARALL_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("NOCLEARALL_KEY_PASSWORD").orNull
val hasReleaseSigning =
    listOf(
        releaseKeystorePath,
        releaseKeystorePassword,
        releaseKeyAlias,
        releaseKeyPassword,
    ).all { !it.isNullOrBlank() }

android {
    namespace = "dev.noclearall"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.noclearall"
        minSdk = 35
        targetSdk = 37
        versionCode = 2
        versionName = "1.0.1"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }

        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )

            // Local builds remain directly installable without private repository files. CI and
            // official releases supply the dedicated signing configuration through secrets.
            signingConfig =
                if (hasReleaseSigning) {
                    signingConfigs.create("release") {
                        storeFile = file(requireNotNull(releaseKeystorePath))
                        storePassword = releaseKeystorePassword
                        keyAlias = releaseKeyAlias
                        keyPassword = releaseKeyPassword
                    }
                } else {
                    signingConfigs.getByName("debug")
                }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        buildConfig = false
    }

    packaging {
        resources {
            // Modern libxposed discovers modules through these resources. Keep only them from the
            // Java-resource namespace; Android resources and the manifest are packaged separately.
            merges += "META-INF/xposed/*"
            excludes += "**"
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
    }
}

dependencies {
    // Provided by LSPosed at runtime. It must never be bundled into the module APK.
    compileOnly("io.github.libxposed:api:102.0.0")
}
