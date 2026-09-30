// Key material is written with adb/AdbKeyVault.kt: a platform AndroidKeyStore
// AES-256-GCM key, which is what Google steered androidx.security towards when
// it deprecated MasterKey / EncryptedFile.
//
// androidx.security:security-crypto 1.1.0 is still a dependency for ONE reason:
// the Tink StreamingAead blobs written by earlier releases have to stay
// readable, or upgrading the app would rotate every user's ADB identity and
// force a re-accept of the RSA authorization dialog. It is used only by the
// legacy branch of readKeyFile() and is written to exactly one place. Once no
// supported release still writes that format, delete the import, the
// `ktlint`-clean EncryptedFile.Builder helper, the masterKey field, the
// dependency, and the -dontwarn if one is needed.
//
// So the @file:Suppress below covers the *imports* of MasterKey/EncryptedFile
// too (Kotlin reports DEPRECATION on a deprecated import directive, and a
// class-level @Suppress does not reach them). Scoping it to this file means the
// warnings it silences cannot mask newly introduced ones anywhere else.
@file:Suppress("DEPRECATION")

package com.wuwaconfig.app.adb

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import java.io.File
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec

/**
 * Interface for ADB RSA operations. [AdbCrypto] is the production
 * implementation; tests supply a fake that returns deterministic signatures.
 */
interface CryptoAdapter {
    val isReady: Boolean

    fun getAdbFormattedPublicKey(): ByteArray

    fun signToken(token: ByteArray): ByteArray

    fun regenerateKeys(): Result<Unit>
}

// The whole class is covered by the @file:Suppress("DEPRECATION") at the top
// of this file; see the comment there for why the migration is deferred.
class AdbCrypto(private val context: Context) : CryptoAdapter {
    companion object {
        private const val TAG = "AdbCrypto"

        // ASN.1 DigestInfo for SHA-1, prepended to the 20-byte token so that a
        // "NONEwithRSA" signature (PKCS#1 padding only, no extra hashing)
        // matches what adbd expects: RSA_sign(NID_sha1, token, ...).
        private val SHA1_DIGEST_INFO =
            byteArrayOf(
                0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e,
                0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14,
            )
    }

    @Volatile
    private var keyPair: KeyPair? = null

    private val keysLoadedLock = Any()
    private var keysLoaded = false

    private val publicKeyFile: File
        get() = File(context.filesDir, "adbkey.pub")
    private val privateKeyFile: File
        get() = File(context.filesDir, "adbkey")

    private val masterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    /** True once keys are loaded. Safe to read from any thread. */
    override val isReady: Boolean get() = keyPair != null

    /**
     * Loads or generates the RSA key pair. Lazily invoked on first use (which
     * happens on an IO dispatcher during ADB auth) so construction stays cheap
     * and never blocks Application.onCreate.
     */
    private fun ensureKeys() {
        if (keysLoaded) return
        synchronized(keysLoadedLock) {
            if (keysLoaded) return
            loadOrGenerateKeys()
            keysLoaded = true
        }
    }

    /**
     * Pre-load keys off the main thread to avoid first-connection jank.
     *
     * Deliberately swallows every failure. This is launched from
     * `WuWaConfigApp.onCreate` on an app-scoped SupervisorJob, where an uncaught
     * exception reaches the default uncaught handler and kills the process — a
     * Keystore problem (unavailable, locked, hardware fault) would then take down
     * the whole app at startup even though key material is only needed by the ADB
     * backend. Failing here is recoverable: the ADB backend reports the error when
     * the user actually connects.
     */
    fun warmUp() {
        runCatching { ensureKeys() }.onFailure { e ->
            Log.e(TAG, "ADB key warm-up failed; the ADB backend will report it on connect", e)
        }
    }

    private fun buildEncryptedFile(file: File): EncryptedFile {
        return EncryptedFile.Builder(
            context,
            file,
            masterKey,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB,
        ).build()
    }

    /**
     * Result of reading one key file: the plaintext, and whether the bytes on
     * disk were in the legacy Tink format and so need rewriting.
     *
     * The two are not separable by return value alone — a caller that ignored
     * [needsRewrite] would silently leave the file unreadable to any future
     * build that has dropped the Tink reader, so the signal is explicit.
     */
    private data class ReadResult(
        val plaintext: ByteArray,
        val needsRewrite: Boolean,
    )

    private fun readKeyFile(file: File): ReadResult? {
        // New format first: it is a plain file read plus a Keystore decrypt,
        // and it must not be attempted through EncryptedFile at all, because
        // Tink would report a perfectly good blob as undecryptable.
        val raw =
            try {
                file.readBytes()
            } catch (_: java.io.FileNotFoundException) {
                // Genuinely absent -> caller is allowed to generate a new key.
                return null
            }

        if (AdbKeyVault.isVaultFormat(raw)) {
            return ReadResult(AdbKeyVault.decrypt(raw), needsRewrite = false)
        }

        // Legacy Tink StreamingAead. Reading it needs androidx.security, which is
        // deprecated; the dependency exists only to get here. Everything that
        // follows this comment is the migration path and can be deleted once no
        // installed build still writes that format.
        val legacy =
            try {
                buildEncryptedFile(file).openFileInput().use { it.readBytes() }
            } catch (e: android.security.keystore.KeyPermanentlyInvalidatedException) {
                // The master key itself was invalidated. Regenerating would silently rotate
                // the user's authorized ADB identity, so fail with something actionable
                // instead of a bare "auth rejected" much later.
                throw IllegalStateException(
                    "ADB key material was permanently invalidated. Clear app storage or use the " +
                        "Shizuku/Root backend. (${e.message})",
                    e,
                )
            } catch (e: Exception) {
                // The ciphertext exists but cannot be decrypted — "No matching key found
                // for the ciphertext in the stream". That happens when the Tink keyset
                // outlives the Keystore master key (app data cleared, keystore entry
                // recreated, restore onto a new device).
                //
                // Returning null IS the "generate a new identity" signal, and here that is
                // the ONLY correct outcome: a key we cannot read is a key we can never
                // authenticate with, so the user is already forced to re-authorize.
                // Throwing instead crashed the app at startup, which is strictly worse
                // than rotating a key that was already worthless.
                Log.w(
                    TAG,
                    "Encrypted ${file.name} is undecryptable (${e.message}); " +
                        "the old ADB identity is unusable, generating a new one",
                )
                return null
            }
        return ReadResult(legacy, needsRewrite = true)
    }

    private fun readEncryptedBytes(file: File): ByteArray? = readKeyFile(file)?.plaintext

    private fun writeEncryptedBytes(
        file: File,
        bytes: ByteArray,
    ) {
        file.parentFile?.mkdirs()
        // Write to a sibling temp and rename(2) into place, so the target is never
        // absent even if the process dies mid-write. The previous shape deleted
        // the old (valid) key first, leaving a window with NO key on disk; if the
        // write then threw, the next launch would rotate the ADB identity.
        val tmp = File(file.parentFile, "${file.name}.new")
        if (tmp.exists() && !tmp.delete()) {
            throw java.io.IOException("Cannot replace stale temp key file ${tmp.name}")
        }
        tmp.writeBytes(AdbKeyVault.encrypt(bytes))
        if (!tmp.renameTo(file)) {
            tmp.delete()
            throw java.io.IOException("Cannot commit key file ${file.name}")
        }
    }

    private fun loadOrGenerateKeys() {
        val pkFile = privateKeyFile
        val pubFile = publicKeyFile

        val privateRead = readKeyFile(pkFile)
        val publicRead = readKeyFile(pubFile)
        if (privateRead != null && publicRead != null) {
            try {
                val keyFactory = KeyFactory.getInstance("RSA")
                val privateKey = keyFactory.generatePrivate(PKCS8EncodedKeySpec(privateRead.plaintext))
                val publicKey = keyFactory.generatePublic(X509EncodedKeySpec(publicRead.plaintext))
                keyPair = KeyPair(publicKey, privateKey)
                migrateIfNeeded(privateRead, publicRead, pkFile, pubFile)
                return
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load encrypted keys", e)
            }
        }

        // Migration: read existing plaintext keys, re-save encrypted
        if (pkFile.exists() && pubFile.exists()) {
            try {
                val ptPrivate = pkFile.readBytes()
                val ptPublic = pubFile.readBytes()
                val keyFactory = KeyFactory.getInstance("RSA")
                val privateKey = keyFactory.generatePrivate(PKCS8EncodedKeySpec(ptPrivate))
                val publicKey = keyFactory.generatePublic(X509EncodedKeySpec(ptPublic))
                keyPair = KeyPair(publicKey, privateKey)
                writeEncryptedBytes(pkFile, ptPrivate)
                writeEncryptedBytes(pubFile, ptPublic)
                // Migration complete — the plaintext originals are private-key
                // material and must not linger in filesDir.
                //
                // Do NOT delete pkFile/pubFile here. writeEncryptedBytes() already
                // deletes the plaintext target before writing the encrypted bytes,
                // so the files on disk are now the ENCRYPTED key material. A trailing
                // delete() therefore removed the fresh encrypted private key, and the
                // next cold start saw no key at all, called generateNewKeys(), and
                // rotated the ADB identity — forcing the user to re-accept the RSA
                // authorization dialog on every launch.
                Log.d(TAG, "Migrated ADB keys from plaintext to encrypted storage")
                return
            } catch (e: Exception) {
                Log.e(TAG, "Failed to migrate existing keys, generating new ones", e)
            }
        }

        generateNewKeys()
    }

    /**
     * Rewrites key files that were read from the legacy Tink format into the
     * AndroidKeyStore format, preserving the exact key bytes.
     *
     * This is the step that lets androidx.security eventually be dropped: once a
     * build has rewritten its files, no later build needs to read Tink again.
     *
     * Failures are logged and swallowed rather than propagated. The key pair in
     * memory is already correct, and the files on disk are still valid Tink —
     * so the worst case is that migration is retried on the next launch, which
     * is strictly better than throwing here and leaving the caller to
     * regenerate and silently rotate the user's ADB identity.
     */
    private fun migrateIfNeeded(
        privateRead: ReadResult,
        publicRead: ReadResult,
        pkFile: File,
        pubFile: File,
    ) {
        if (!privateRead.needsRewrite && !publicRead.needsRewrite) return
        try {
            // Each file is rewritten independently: if only one was legacy, the
            // other is already current and rewriting it again is harmless but
            // pointless, so skip it.
            if (privateRead.needsRewrite) writeEncryptedBytes(pkFile, privateRead.plaintext)
            if (publicRead.needsRewrite) writeEncryptedBytes(pubFile, publicRead.plaintext)
            Log.d(TAG, "Migrated ADB keys from EncryptedFile/Tink to AndroidKeyStore AES-GCM")
        } catch (e: Exception) {
            Log.w(TAG, "ADB key format migration failed; will retry next launch", e)
        }
    }

    private fun generateNewKeys() {
        Log.d(TAG, "Generating new 2048-bit RSA key pair")
        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(2048)
        keyPair = generator.generateKeyPair()

        writeEncryptedBytes(privateKeyFile, keyPair!!.private.encoded)
        writeEncryptedBytes(publicKeyFile, keyPair!!.public.encoded)
        Log.d(TAG, "Keys saved encrypted via EncryptedFile")
    }

    override fun getAdbFormattedPublicKey(): ByteArray {
        ensureKeys()
        val rsaPubKey = keyPair!!.public as java.security.interfaces.RSAPublicKey
        val bos = java.io.ByteArrayOutputStream()
        val algo = "ssh-rsa".toByteArray(Charsets.UTF_8)
        writeUint32(bos, algo.size)
        bos.write(algo)
        writeMpInt(bos, rsaPubKey.publicExponent.toByteArray())
        writeMpInt(bos, rsaPubKey.modulus.toByteArray())
        val b64 = Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
        return "$b64 wuwaconfig@android ".toByteArray()
    }

    private fun writeUint32(
        stream: java.io.ByteArrayOutputStream,
        v: Int,
    ) {
        stream.write((v shr 24) and 0xFF)
        stream.write((v shr 16) and 0xFF)
        stream.write((v shr 8) and 0xFF)
        stream.write(v and 0xFF)
    }

    private fun writeMpInt(
        stream: java.io.ByteArrayOutputStream,
        raw: ByteArray,
    ) {
        var data = raw
        if (data.size > 1 && data[0] == 0.toByte()) data = data.copyOfRange(1, data.size)
        writeUint32(stream, data.size)
        stream.write(data)
    }

    override fun signToken(token: ByteArray): ByteArray {
        ensureKeys()
        Log.d(TAG, "Signing ${token.size}B token with NONEwithRSA (pre-hashed SHA1)")
        val signature = Signature.getInstance("NONEwithRSA")
        signature.initSign(keyPair!!.private)
        signature.update(SHA1_DIGEST_INFO)
        signature.update(token)
        val sig = signature.sign()
        Log.d(TAG, "Signature: ${sig.size}B")
        return sig
    }

    override fun regenerateKeys(): Result<Unit> {
        Log.d(TAG, "Regenerating RSA keys")
        return runCatching {
            synchronized(keysLoadedLock) {
                generateNewKeys()
                keysLoaded = true
            }
        }
    }
}
