plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ktlint)
}

android {
    namespace = "com.wuwaconfig.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.wuwaconfig.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 14
        versionName = "1.1.3"
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

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    applicationVariants.configureEach {
        val vName = name
        val vVersion = versionName
        outputs.configureEach {
            val apkName = if (vName == "release") "WuWaConfig-v$vVersion-release.apk" else "WuWaConfig-debug.apk"
            (this as com.android.build.gradle.internal.api.ApkVariantOutputImpl).outputFileName = apkName
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
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
    implementation(libs.androidx.compose.ui.tooling.preview)
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
