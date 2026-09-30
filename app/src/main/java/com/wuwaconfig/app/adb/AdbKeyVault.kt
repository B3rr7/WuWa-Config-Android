package com.wuwaconfig.app.adb

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Authenticated encryption for the on-disk ADB key material, backed by a
 * hardware-bound key in the platform `AndroidKeyStore`.
 *
 * This replaces `androidx.security:security-crypto`'s `MasterKey` +
 * `EncryptedFile`, which is deprecated in favour of exactly this API.
 *
 * ## On-disk format
 *
 * ```
 * magic  : 4 bytes  "WWAK"
 * version: 1 byte   currently 1
 * iv     : 12 bytes  GCM nonce, fresh per encryption
 * body   : n bytes   AES-256-GCM ciphertext, including the 16-byte tag
 * ```
 *
 * The magic is what makes migration possible. The previous format was Tink
 * StreamingAead (AES256_GCM_HKDF_4KB) written by `EncryptedFile`, which has no
 * recognisable header, so "is this the new format?" is answered by the first
 * four bytes rather than by an out-of-band flag that could get out of sync with
 * the bytes on disk.
 *
 * ## Why the blob is self-describing
 *
 * A randomised IV is stored in the clear next to the ciphertext. That is not a
 * weakness: GCM only requires the nonce to be unique per key, not secret.
 * Generating a fresh one per write is what makes the nonce safe to store, and it
 * means re-encrypting identical plaintext produces different bytes — so a
 * diff of the key file reveals nothing about whether the key changed.
 */
internal object AdbKeyVault {
    private const val KEY_ALIAS = "wuwaconfig.adbkey.vault"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    private val MAGIC = "WWAK".toByteArray(Charsets.US_ASCII)
    private const val VERSION: Byte = 1
    private const val GCM_TAG_BITS = 128
    private const val HEADER_BYTES = 4 + 1 + 12

    /**
     * True when [blob] is in this format. Anything else is assumed to be the
     * legacy Tink stream and routed to the migration path.
     */
    fun isVaultFormat(blob: ByteArray): Boolean =
        blob.size > HEADER_BYTES &&
            MAGIC.indices.all { blob[it] == MAGIC[it] } &&
            blob[MAGIC.size] == VERSION

    fun encrypt(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, orCreateKey())
        val body = cipher.doFinal(plaintext)
        return MAGIC + VERSION + cipher.iv + body
    }

    /** Decrypts a blob produced by [encrypt]. Throws if it is not in this format. */
    fun decrypt(blob: ByteArray): ByteArray {
        require(isVaultFormat(blob)) { "not an AdbKeyVault blob" }
        val iv = blob.copyOfRange(MAGIC.size + 1, HEADER_BYTES)
        val body = blob.copyOfRange(HEADER_BYTES, blob.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, orCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(body)
    }

    /**
     * Deletes the wrapping key. Every blob encrypted under it becomes
     * undecryptable, which the caller treats exactly like losing the file.
     */
    fun destroy() {
        runCatching {
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(KEY_ALIAS)
        }
    }

    /**
     * The wrapping key, created on first use.
     *
     * `setRandomizedEncryptionRequired` is left at its default of true: it makes
     * the Keystore refuse to encrypt without a caller-supplied IV, which is the
     * guarantee [encrypt] relies on when it stores the nonce in the clear.
     *
     * No user-authentication requirement is set. The ADB backend reads and
     * writes keys from background coroutines, and binding this key to a device
     * unlock would mean an ADB session that silently dies every time the screen
     * locks.
     */
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
