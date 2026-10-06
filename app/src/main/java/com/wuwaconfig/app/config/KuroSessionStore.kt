package com.wuwaconfig.app.config

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.wuwaconfig.app.util.writeAtomic
import java.io.File
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Maps [KuroSession] to and from JSON, as pure functions so the exact field set can be
 * round-trip tested without Android. Hand-mapped rather than Gson-bound on purpose: the fields
 * are small and flat, and explicit reads keep an absent key reading as a Kotlin default
 * instead of a silent `0`/`false` (the same failure [TuningProfile] guards against).
 */
object KuroSessionCodec {
    fun encode(session: KuroSession): String {
        val root = JsonObject()
        root.addProperty("xToken", session.xToken)
        root.addProperty("cuid", session.cuid)
        root.addProperty("username", session.username)
        session.email?.let { root.addProperty("email", it) }
        session.expiresAtEpochSec?.let { root.addProperty("expiresAtEpochSec", it) }
        session.chosenPlayer?.let { root.add("chosenPlayer", encodePlayer(it)) }
        return root.toString()
    }

    fun decode(json: String): KuroSession? {
        val root =
            runCatching { JsonParser.parseString(json) }
                .getOrNull()
                ?.takeIf(JsonElement::isJsonObject)
                ?.asJsonObject
                ?: return null
        val xToken = root.str("xToken") ?: return null
        return KuroSession(
            xToken = xToken,
            cuid = root.str("cuid").orEmpty(),
            username = root.str("username").orEmpty(),
            chosenPlayer = root.json("chosenPlayer")?.let { decodePlayer(it) },
            email = root.str("email"),
            expiresAtEpochSec = root.longOrNull("expiresAtEpochSec"),
        )
    }

    private fun encodePlayer(p: ChosenPlayer): JsonObject {
        val o = JsonObject()
        o.addProperty("playerId", p.playerId)
        p.playerName?.let { o.addProperty("playerName", it) }
        o.addProperty("serverId", p.serverId)
        p.serverName?.let { o.addProperty("serverName", it) }
        p.level?.let { o.addProperty("level", it) }
        return o
    }

    private fun decodePlayer(o: JsonObject): ChosenPlayer? {
        val playerId = o.longOrNull("playerId") ?: return null
        return ChosenPlayer(
            playerId = playerId,
            playerName = o.str("playerName"),
            serverId = o.str("serverId").orEmpty(),
            serverName = o.str("serverName"),
            level = o.intOrNull("level"),
        )
    }

    private fun JsonObject.str(name: String): String? = get(name)?.takeIf { it.isJsonPrimitive }?.asString

    private fun JsonObject.longOrNull(name: String): Long? = get(name)?.takeIf { it.isJsonPrimitive }?.run { runCatching { asLong }.getOrNull() }

    private fun JsonObject.intOrNull(name: String): Int? = get(name)?.takeIf { it.isJsonPrimitive }?.run { runCatching { asInt }.getOrNull() }

    private fun JsonObject.json(name: String): JsonObject? = get(name)?.takeIf { it.isJsonObject }?.asJsonObject
}

/**
 * Persists the [KuroSession] in [Context.getFilesDir] under authenticated encryption.
 *
 * The bearer [KuroSession.xToken] grants read access to the user's game profile, so it is not
 * written in the clear: the blob is AES-256-GCM under a hardware-bound key in
 * [android.security.keystore.AndroidKeyStore]. A root-level read of the app's data directory
 * therefore yields an undecryptable file. This mirrors [com.wuwaconfig.app.adb.AdbKeyVault]
 * (same cipher, same self-describing layout) but under its own alias, so destroying one does
 * not wipe the other.
 *
 * The blob is base64-wrapped before it is written so the store can reuse the app's
 * [writeAtomic] helper (fsync'd, non-destructive) instead of duplicating the atomic-write code
 * for binary data.
 */
object KuroSessionStore {
    private const val FILE_NAME = "kuro_session.bin"
    private const val KEY_ALIAS = "wuwaconfig.kuro.session"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
    private const val VERSION: Byte = 1
    private val MAGIC = "WWKS".toByteArray(Charsets.US_ASCII)
    private const val HEADER_BYTES = 4 + 1 + 12
    private val lock = Any()

    /** The current session, or `null` when none is stored or the blob no longer decrypts. */
    fun load(ctx: Context): KuroSession? =
        synchronized(lock) {
            val file = File(ctx.filesDir, FILE_NAME)
            if (!file.exists()) return@synchronized null
            val encoded = runCatching { file.readText() }.getOrNull() ?: return@synchronized null
            val blob =
                runCatching { Base64.getDecoder().decode(encoded) }
                    .getOrNull()
                    ?: return@synchronized null
            val plain =
                runCatching {
                    require(isVaultFormat(blob))
                    decrypt(blob)
                }.getOrNull()
                    ?: return@synchronized null
            runCatching { KuroSessionCodec.decode(String(plain, Charsets.UTF_8)) }.getOrNull()
        }

    /** Overwrites the stored session. */
    fun save(
        ctx: Context,
        session: KuroSession,
    ) {
        synchronized(lock) {
            val blob = encrypt(KuroSessionCodec.encode(session).toByteArray(Charsets.UTF_8))
            File(ctx.filesDir, FILE_NAME).writeAtomic(Base64.getEncoder().encodeToString(blob))
        }
    }

    /** Deletes the stored session (logs the user out). */
    fun clear(ctx: Context) {
        synchronized(lock) {
            File(ctx.filesDir, FILE_NAME).delete()
        }
    }

    // -- crypto (mirrors AdbKeyVault's layout under a dedicated alias) --------

    private fun isVaultFormat(blob: ByteArray): Boolean =
        blob.size > HEADER_BYTES &&
            MAGIC.indices.all { blob[it] == MAGIC[it] } &&
            blob[MAGIC.size] == VERSION

    private fun encrypt(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, orCreateKey())
        val body = cipher.doFinal(plaintext)
        return MAGIC + VERSION + cipher.iv + body
    }

    private fun decrypt(blob: ByteArray): ByteArray {
        val iv = blob.copyOfRange(MAGIC.size + 1, HEADER_BYTES)
        val body = blob.copyOfRange(HEADER_BYTES, blob.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, orCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(body)
    }

    private fun orCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }
}
