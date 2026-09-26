package com.wuwaconfig.app.util

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.os.PersistableBundle

/**
 * Copies [text] to the clipboard marked SENSITIVE.
 *
 * Without ClipDescription.EXTRA_IS_SENSITIVE the clip is eligible for the system
 * clipboard history and the keyboard's paste preview. Google Keyboard ships with
 * clipboard history ON and syncs it to the signed-in Google account, so an
 * unmarked copy of a log line (which can contain shell commands, the phone's
 * wireless-ADB host:port, and paths under Android/data/<game>/) is replicated OFF
 * the device with no further consent. The flag is honoured from API 33 for the
 * overlay and is the correct signal on every version.
 */
fun ClipboardManager.copySensitive(
    label: String,
    text: String,
) {
    val clip = ClipData.newPlainText(label, text)
    clip.description.extras =
        PersistableBundle().apply {
            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
        }
    setPrimaryClip(clip)
}
