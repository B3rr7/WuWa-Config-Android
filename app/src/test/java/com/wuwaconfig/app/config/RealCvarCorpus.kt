package com.wuwaconfig.app.config

import org.junit.Assert.fail
import java.io.File

/**
 * Locates the real `libUE4_cvars.txt` corpus for the corpus-scale tests.
 *
 * The classpath copy under `src/test/resources/cvars/` is a deliberate 10-line
 * stub, so any test that asserts against thousands of entries has to read the
 * asset straight off disk — which means resolving a path relative to the
 * working directory, and Gradle, the IDE and a bare `gradle test` invocation do
 * not agree on what that is (`app/` vs the repo root, sometimes neither).
 *
 * This walks UP from the working directory looking for the asset instead of
 * hardcoding two candidates, so a runner rooted anywhere inside the checkout
 * resolves. If it still cannot find it, that is a hard failure with the search
 * path printed — never a silent pass over an empty corpus, which would let
 * every assertion below it trivially succeed.
 */
internal fun realCvarCorpusFile(): File {
    val relative = "app/src/main/assets/cvars/libUE4_cvars.txt"
    var dir: File? = File(".").absoluteFile
    val tried = mutableListOf<String>()
    while (dir != null) {
        val direct = File(dir, relative)
        tried += direct.path
        if (direct.isFile) return direct
        // Also accept a runner already rooted inside the module dir.
        val fromModule = File(dir, "src/main/assets/cvars/libUE4_cvars.txt")
        tried += fromModule.path
        if (fromModule.isFile) return fromModule
        dir = dir.parentFile
    }
    fail(
        "libUE4_cvars.txt not found walking up from user.dir=${System.getProperty("user.dir")}. " +
            "Tried:\n  " + tried.joinToString("\n  "),
    )
    error("unreachable")
}

/** Active (non-`;`-commented, non-blank) entries of the real corpus, lowercased. */
internal fun realActiveCvarNames(): Set<String> =
    realCvarCorpusFile()
        .readLines()
        .map { it.trim().lowercase() }
        .filter { it.isActiveCvarName() }
        .toSet()

/** Active entries of the real corpus, original casing preserved. */
internal fun realActiveCorpusLines(): List<String> =
    realCvarCorpusFile()
        .readLines()
        .map { it.trim() }
        .filter { it.isActiveCvarName() }

/** Active `r.`-prefixed keys of the real corpus, original casing preserved. */
internal fun realRenderCvarKeys(): List<String> =
    realCvarCorpusFile()
        .readLines()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith(";") && !it.startsWith("#") }
        .filter { it.startsWith("r.") }
        .distinct()
