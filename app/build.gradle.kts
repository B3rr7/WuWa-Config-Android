import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ktlint)
}

android {
    namespace = "com.wuwaconfig.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.wuwaconfig.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 16
        versionName = "1.1.5"
    }

    androidResources {
        // App ships only res/values (no translations); strip locales bundled
        // by androidx/material/media3/coil to save a few hundred KB.
        localeFilters.add("en")
    }

    val keystoreProps =
        rootProject.file("keystore.properties")
            .let { f ->
                if (!f.exists()) {
                    emptyMap()
                } else {
                    f.readLines().mapNotNull { line ->
                        val trimmed = line.trim()
                        if (trimmed.startsWith("#") || trimmed.isEmpty()) {
                            null
                        } else {
                            val eq = trimmed.indexOf('=')
                            if (eq > 0) {
                                trimmed.substring(0, eq).trim() to trimmed.substring(eq + 1).trim()
                            } else {
                                null
                            }
                        }
                    }.toMap()
                }
            }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file(keystoreProps.getOrElse("storeFile") { "release.jks" })
            storePassword = keystoreProps.getOrElse("storePassword") { System.getenv("STORE_PASSWORD") ?: "" }
            keyAlias = keystoreProps.getOrElse("keyAlias") { System.getenv("KEY_ALIAS") ?: "" }
            keyPassword = keystoreProps.getOrElse("keyPassword") { System.getenv("KEY_PASSWORD") ?: "" }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // AGP 9 uses the lazy Variant API. Rename APK outputs through the public
    // androidComponents API instead of the removed applicationVariants API.
    androidComponents {
        onVariants(selector().all()) { variant ->
            variant.outputs.forEach { output ->
                output.outputFileName.set(
                    output.versionName.map { appVersionName ->
                        if (variant.buildType == "release") {
                            "WuWaConfig-v$appVersionName-release.apk"
                        } else {
                            "WuWaConfig-debug.apk"
                        }
                    },
                )
            }
        }
    }

    packaging {
        resources {
            // Explicit entries — single-string brace expansion is a documented
            // AGP 9 known-issue; identical behavior on AGP 8.
            excludes.add("/META-INF/AL2.0")
            excludes.add("/META-INF/LGPL2.1")
        }
    }

    lint {
        lintConfig = file("lint.xml")
    }
}

dependencies {
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.animation)

    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)

    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.localbroadcastmanager)
    implementation(libs.gson)
    implementation(libs.coil.compose)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.mockito.core)
}
