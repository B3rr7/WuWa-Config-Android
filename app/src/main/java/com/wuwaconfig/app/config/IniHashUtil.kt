package com.wuwaconfig.app.config

private val iniSectionRegex = Regex("^\\[[A-Za-z0-9_\\-]+\\.ini\\]$", RegexOption.IGNORE_CASE)

fun extractHash(
    hashContent: String,
    fileName: String,
): String? {
    var inSection = false
    for (line in hashContent.lines()) {
        val t = line.trim()
        if (t.equals("[$fileName]", ignoreCase = true)) {
            inSection = true
            continue
        }
        if (inSection && t.matches(iniSectionRegex)) break
        if (inSection && t.startsWith("Hash=")) return t.removePrefix("Hash=").trim()
    }
    return null
}
