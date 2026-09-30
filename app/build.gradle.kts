import org.gradle.api.GradleException
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ktlint)
    // Required by androidx.appfunctions:appfunctions-compiler, which generates
    // the AppFunctionService subclass and the assets schema XML at compile time.
    alias(libs.plugins.ksp)
    // Generates the @Serializable serializers the type-safe navigation route
    // keys need. See the libs.plugins.serialization note.
    alias(libs.plugins.serialization)
}

// Keys that must ALL be present for a release build to be signable.
val RELEASE_SIGNING_KEYS = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
val RELEASE_SIGNING_ENV_VARS = listOf("STORE_FILE", "STORE_PASSWORD", "KEY_ALIAS", "KEY_PASSWORD")

val RELEASE_SIGNING_HELP =
    """
    |Release signing material is missing — refusing to build a release APK.
    |
    |Provide it one of two ways:
    |  1. Create <root>/keystore.properties (gitignored, never commit it) with all four keys:
    |       storeFile=<path to your .jks>
    |       storePassword=...
    |       keyAlias=...
    |       keyPassword=...
    |  2. Export the environment variables instead:
    |       STORE_FILE, STORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD
    |
    |There is no bundled fallback keystore on purpose: silently signing with a
    |key checked into the repo directory would produce a release APK that
    |UpdateManager's certificate check accepts and offers to every user.
    """.trimMargin()

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

// Exactly two supported mechanisms, in priority order:
//   1. <root>/keystore.properties with ALL FOUR keys:
//        storeFile, storePassword, keyAlias, keyPassword
//   2. Environment variables (all four):
//        STORE_FILE, STORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD
//
// There is deliberately NO fallback keystore. Previously an absent
// keystore.properties silently fell back to the root release.jks, so a copy of
// the repo directory could produce an APK that passes UpdateManager's
// certificate check and gets offered to every user — the "release needs
// signing" failure mode never fired.
val hasReleaseSigningMaterial =
    RELEASE_SIGNING_KEYS.all { key -> keystoreProps.containsKey(key) } ||
        RELEASE_SIGNING_ENV_VARS.all { envKey -> !System.getenv(envKey).isNullOrBlank() }

val releaseStorePath = keystoreProps["storeFile"] ?: System.getenv("STORE_FILE")
val releaseStorePassword = keystoreProps["storePassword"] ?: System.getenv("STORE_PASSWORD")
val releaseKeyAlias = keystoreProps["keyAlias"] ?: System.getenv("KEY_ALIAS")
val releaseKeyPassword = keystoreProps["keyPassword"] ?: System.getenv("KEY_PASSWORD")

android {
    namespace = "com.wuwaconfig.app"
    // 37 is a hard floor, not a preference: the AppFunctions platform API
    // (android.app.appfunctions) is only in android-37's android.jar, and the
    // androidx.appfunctions compiler needs it on the compile classpath to
    // generate the service + schema XML. targetSdk stays 36.
    compileSdk = 37

    defaultConfig {
        // Screenshots are allowed by default for every variant. FLAG_SECURE also
        // blocks `adb shell screencap` and blanks the recents-task thumbnail, which
        // is a poor trade for an app whose value is showing the user their config:
        // this one is a plain utility screen, not a banking or messaging surface.
        // Opt back in with `./gradlew assembleRelease -PsecureScreenshots=true`.
        buildConfigField(
            "boolean",
            "SECURE_SCREENSHOTS",
            if ((project.findProperty("secureScreenshots") as String?) == "true") "true" else "false",
        )

        applicationId = "com.wuwaconfig.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 16
        versionName = "1.1.5"
    }

    androidResources {
        // App ships only res/values (no translations); strip locales bundled
        // by androidx/material/media3/coil to save a few hundred KB.
        localeFilters.add("en")
    }

    signingConfigs {
        create("release") {
            // Left null when no material is available — the gate below turns
            // that into a build error, but only for release builds.
            if (releaseStorePath != null) storeFile = rootProject.file(releaseStorePath)
            storePassword = releaseStorePassword
            keyAlias = releaseKeyAlias
            keyPassword = releaseKeyPassword
        }
    }

    buildTypes {
        debug {
            // A locally-built debug APK must not silently replace the installed
            // release: a distinct applicationId gets its own filesDir and
            // SharedPreferences, and a debug-signed build is (correctly)
            // refused by UpdateManager's cert check against a release install,
            // and vice versa.
            applicationIdSuffix = ".debug"
        }
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

    testOptions {
        // android.jar methods are stubs that throw in unit tests; AdbClient
        // (Log.d) and friends need default no-op returns instead.
        unitTests.isReturnDefaultValues = true
    }
}

// Fail loudly on a release build with no signing material — but ONLY when a
// release variant is actually part of THIS build, so assembleDebug,
// testDebugUnitTest, ktlintCheck and IDE sync are all unaffected.
// whenReady() merely registers a listener (it does not force the graph to be
// computed), and the graph is empty for tooling/model builds, so registering
// this at configuration time is free.
//
// The explicit org.gradle.api.Action<...TaskExecutionGraph> type is required: the
// Kotlin DSL otherwise resolves the lambda against Gradle's Groovy Closure
// overload and fails to compile with a Closure/Function type mismatch. In Gradle 9
// Action<T> is a `fun interface` whose SAM is a RECEIVER lambda (`T.() -> Unit`),
// hence `this.allTasks` rather than a named `graph` parameter. The interface moved
// from org.gradle.execution.plan to org.gradle.api.execution in Gradle 9.
gradle.taskGraph.whenReady(
    org.gradle.api.Action<org.gradle.api.execution.TaskExecutionGraph> {
        val releaseRequested = allTasks.any { it.name.contains("Release", ignoreCase = true) }
        if (releaseRequested && !hasReleaseSigningMaterial) {
            throw GradleException(RELEASE_SIGNING_HELP)
        }
    },
)

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
    // Nav3. -runtime supplies NavKey/entryProvider/rememberNavBackStack, -ui
    // supplies NavDisplay. This replaced navigation-compose 2.9.8.
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)

    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)

    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.security.crypto)
    implementation(libs.gson)
    implementation(libs.coil.compose)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)

    // AppFunctions: system-agent callable surface. `appfunctions` is the
    // runtime/back-compat shim (it re-exports the platform API down to API 36);
    // `appfunctions-compiler` is the KSP processor that emits
    // BaseWuWaAppFunctionService's concrete subclass + the assets schema XML.
    // The processor must run on the same KSP version as the Kotlin plugin.
    implementation(libs.androidx.appfunctions)
    ksp(libs.androidx.appfunctions.compiler)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.mockito.core)
}

// The KSP processor options cannot be passed through the `ksp(...)` dependency
// block: under Gradle 9 the trailing lambda resolves to a Groovy Closure
// overload, so `arg` is not in scope. Configuring the extension directly is the
// supported route and avoids the same Action<T> SAM trap described above.
// aggregateAppFunctions merges the schema of every @AppFunctionServiceEntryPoint
// into one inventory so the system discovers the app's functions as a unit.
extensions.configure<com.google.devtools.ksp.gradle.KspExtension>("ksp") {
    arg("appfunctions:aggregateAppFunctions", "true")
}

// ---------------------------------------------------------------------------
// ktlint over Kotlin sources
//
// The ktlint Gradle plugin registers one task per KotlinSourceSet. Under AGP 9
// there is no Kotlin Gradle plugin — Kotlin comes from AGP's built-in support
// — so the plugin never sees a source set and `ktlintCheck` degenerates to
// ktlintKotlinScriptCheck. `./gradlew :app:tasks --all` lists only that, which
// means the 86 .kt files, i.e. all of the application code, have never been
// style-checked by this build.
//
// These tasks run the ktlint CLI directly on the Android source directories.
// The CLI version is read from the plugin's own extension rather than written
// out here, so there is still exactly one place the version is decided.
// ---------------------------------------------------------------------------

val ktlintCli by configurations.creating

// Read at the project scope, not inside dependencies {} — that block's receiver
// is the DependencyHandler, where the project extensions are not visible.
val ktlintCliVersion = the<org.jlleitschuh.gradle.ktlint.KtlintExtension>().version.get()

dependencies {
    // Not the plugin's `ktlint` configuration: that one resolves the rule engine
    // but omits the CLI's runtime logging dependency (kotlin-logging-jvm), so
    // running the CLI off it dies with NoClassDefFoundError: KLogger. Resolving
    // ktlint-cli itself here pulls the complete runtime graph.
    ktlintCli("com.pinterest.ktlint:ktlint-cli:$ktlintCliVersion")
}

val ktlintSourceDirs = listOf("src/main/java", "src/test/java")

fun ktlintCliArgs() =
    buildList {
        addAll(ktlintSourceDirs)
        // Stated explicitly: relative to workingDir, so it is the repo root's
        // file and not whatever ~/.editorconfig the machine happens to carry.
        add("--editorconfig=../.editorconfig")
    }

val ktlintCheckSources =
    tasks.register<JavaExec>("ktlintCheckSources") {
        group = "verification"
        description = "Runs ktlint over the Kotlin source sets (AGP 9 hides them from the ktlint plugin)."
        classpath = ktlintCli
        mainClass.set("com.pinterest.ktlint.Main")
        workingDir = projectDir
        args = ktlintCliArgs()
    }

val ktlintFormatSources =
    tasks.register<JavaExec>("ktlintFormatSources") {
        group = "formatting"
        description = "Auto-formats the Kotlin source sets with ktlint."
        classpath = ktlintCli
        mainClass.set("com.pinterest.ktlint.Main")
        workingDir = projectDir
        args = ktlintCliArgs() + "-F"
    }

// Fold both into the lifecycle task names CI and AGENTS.md already document, so
// `./gradlew ktlintCheck` stops being a no-op that reports success.
tasks.matching { it.name == "ktlintCheck" }.configureEach { dependsOn(ktlintCheckSources) }
tasks.matching { it.name == "ktlintFormat" }.configureEach { dependsOn(ktlintFormatSources) }
