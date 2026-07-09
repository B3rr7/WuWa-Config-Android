package com.wuwaconfig.app.backend

import java.io.File
import java.security.MessageDigest

fun shQuote(value: String): String = "'${value.replace("'", "'\"'\"'")}'"

fun computeMd5(file: File): String {
    val digest = MessageDigest.getInstance("MD5").digest(file.readBytes())
    return digest.joinToString("") { "%02x".format(it) }
}

const val PUSH_RETRY_COUNT = 2

const val MAX_ARG_STRLEN = 4096
private const val PRINTF_OVERHEAD = 15 // "printf '%s' " + " >> "
private const val QUOTE_OVERHEAD = 2 // two single quotes around value

fun maxPushChunkSize(encodedPath: String): Int {
    val pathQuoted = shQuote(encodedPath)
    val overhead = PRINTF_OVERHEAD + pathQuoted.length + QUOTE_OVERHEAD
    return (MAX_ARG_STRLEN - overhead).coerceIn(256, MAX_ARG_STRLEN)
}
