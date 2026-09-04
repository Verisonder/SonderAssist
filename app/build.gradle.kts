plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.verisonder.sonderassist"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.verisonder.sonderassist"
        // 28 because GLOBAL_ACTION_LOCK_SCREEN arrived in 28, and locking the screen is
        // the entire point of the app. Below that there is nothing to ship.
        minSdk = 28
        targetSdk = 35
        versionCode = 3
        versionName = "1.1"
    }

    signingConfigs {
        create("release") {
            // Supplied by CI from repository secrets. Absent locally, which is why the
            // release build type falls back to the debug key below rather than failing.
            //
            // Every value is trimmed. A secret pasted from a table or a terminal picks up
            // a leading tab or a trailing newline without showing anything, and the
            // failure lands at packageRelease as "No key with alias ' sonderassist'" —
            // which reads like the alias is wrong rather than like it has a tab on it.
            // That cost a release. Nothing here can legitimately contain outer
            // whitespace, so trimming has no downside.
            val storeFilePath = System.getenv("RELEASE_STORE_FILE")?.trim()
            if (!storeFilePath.isNullOrEmpty()) {
                storeFile = file(storeFilePath)
                storePassword = System.getenv("RELEASE_STORE_PASSWORD")?.trim()
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")?.trim()
                // PKCS12 keystores ignore a separate key password: it always equals the
                // store password. Setting them differently fails at packageRelease with
                // "Given final block not properly padded", which reads like a code bug.
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")?.trim()
                    ?: System.getenv("RELEASE_STORE_PASSWORD")?.trim()
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (System.getenv("RELEASE_STORE_FILE") != null) {
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

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
            all {
                // The detector tests replay recorded traces from src/test/resources, so
                // the test has to know where it is standing.
                it.workingDir = project.projectDir
                it.testLogging {
                    events("passed", "failed", "skipped")
                }
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
