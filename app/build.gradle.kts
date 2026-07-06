plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jlleitschuh.gradle.ktlint")
}

android {
    namespace = "com.wuwaconfig.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.wuwaconfig.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 7
        versionName = "1.0.6"
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
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
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
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.02.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.animation:animation")

    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")

    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")

    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
}
