package com.wuwaconfig.app.ui

/**
 * Presentation of the per-file modification counts on the Profile screen.
 *
 * Three states that must never be conflated, because two of them are
 * indistinguishable to a user:
 *
 *  - **The read failed** (`null`). The device hash file could not be read.
 *    Modelling this as an empty map made the whole MODIFICATIONS block vanish,
 *    so "the game has not modified anything" and "the app could not find out"
 *    looked the same — and a block that appears and disappears reads as a
 *    rendering glitch rather than as missing data.
 *  - **The read succeeded but the file has no `ModifyCount=` line.** The game
 *    has not touched that INI. Rendering this as `0` made an untouched file
 *    look like a measured zero, which is worse than a blank because it is
 *    confident.
 *  - **A real count.**
 */
internal fun modifyCountLabel(
    counts: Map<String, Int>?,
    fileName: String,
): String =
    when {
        counts == null -> "unavailable"
        else -> counts[fileName]?.toString() ?: "—"
    }
