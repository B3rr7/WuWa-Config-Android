# Contributing

Thanks for helping out! Please read the [Code of Conduct](CODE_OF_CONDUCT.md)
first.

**Requirements:** JDK 17, Android SDK with **platform 37** installed
(a hard floor — `android.app.appfunctions` exists only in android-37).
Gradle is provided by the wrapper.

**Verify before opening a PR** (same order CI uses):

```bash
./gradlew ktlintCheck        # style gate
./gradlew lint               # Android Lint
./gradlew testDebugUnitTest  # unit tests
./gradlew assembleDebug      # debug APK
```

**Code style:** Run `./gradlew ktlintFormat` to auto-format.

**Branches:** Use `fix/<slug>` or `feat/<slug>`. Open PRs targeting `main`.

**Releasing:** `assembleRelease` needs signing material — see the note in the
README's *Building from source* section.

**Security:** See [SECURITY.md](SECURITY.md) to report a vulnerability privately.
