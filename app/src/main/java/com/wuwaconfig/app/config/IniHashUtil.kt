package com.wuwaconfig.app.config

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
        if (inSection && t.matches(HashMonitor.HASH_SECTION_REGEX)) break
        if (inSection && t.startsWith("Hash=")) return t.removePrefix("Hash=").trim()
    }
    return null
}
