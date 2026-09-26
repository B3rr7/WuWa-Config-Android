pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // Already correct: project-local repository declarations are rejected, so
    // every artifact must come from the two blocks below. Keep it that way.
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

// FOLLOW-UP (not automatable here — generating the metadata requires running
// Gradle, which is not permitted in this change set):
// this project installs APKs on user devices and talks to a game API, so
// dependency coordinates should be pinned by hash. Run:
//
//   ./gradlew --write-verification-metadata sha256 help
//
// and commit the generated gradle/verification-metadata.xml. Until that file
// exists, nothing below resolves or pins anything — `RepositoriesMode` only
// controls WHERE artifacts come from, not WHICH bytes. The generated file must
// be refreshed whenever any version in gradle/libs.versions.toml changes.

rootProject.name = "WuWaConfig"
include(":app")
