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

class AdbCrypto(private val context: Context) {
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
    val isReady: Boolean get() = keyPair != null

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

    /** Pre-load keys off the main thread to avoid first-connection jank. */
    fun warmUp() = ensureKeys()

    private fun buildEncryptedFile(file: File): EncryptedFile {
        return EncryptedFile.Builder(
            context,
            file,
            masterKey,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB,
        ).build()
    }

    private fun readEncryptedBytes(file: File): ByteArray? {
        return try {
            buildEncryptedFile(file).openFileInput().use { it.readBytes() }
        } catch (_: java.io.FileNotFoundException) {
            null
        } catch (e: Exception) {
            // A transient Keystore hiccup here would silently rotate the ADB
            // identity and force re-authorization — surface it at least.
            Log.w(TAG, "Failed to read encrypted ${file.name}: ${e.message}")
            null
        }
    }

    private fun writeEncryptedBytes(
        file: File,
        bytes: ByteArray,
    ) {
        file.parentFile?.mkdirs()
        // EncryptedFile.openFileOutput() throws if the target already exists
        // (security-crypto 1.1.0), so replace it explicitly. Without this, key
        // regeneration and the plaintext->encrypted migration always throw,
        // which previously crashed Application.onCreate.
        if (file.exists() && !file.delete()) {
            throw java.io.IOException("Cannot replace existing key file ${file.name}")
        }
        buildEncryptedFile(file).openFileOutput().use { it.write(bytes) }
    }

    private fun loadOrGenerateKeys() {
        val pkFile = privateKeyFile
        val pubFile = publicKeyFile

        val privateBytes = readEncryptedBytes(pkFile)
        val publicBytes = readEncryptedBytes(pubFile)
        if (privateBytes != null && publicBytes != null) {
            try {
                val keyFactory = KeyFactory.getInstance("RSA")
                val privateKey = keyFactory.generatePrivate(PKCS8EncodedKeySpec(privateBytes))
                val publicKey = keyFactory.generatePublic(X509EncodedKeySpec(publicBytes))
                keyPair = KeyPair(publicKey, privateKey)
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
                val removedPk = pkFile.delete()
                val removedPub = pubFile.delete()
                if (!removedPk || !removedPub) {
                    Log.w(TAG, "Plaintext key cleanup incomplete (pk=$removedPk pub=$removedPub)")
                }
                Log.d(TAG, "Migrated ADB keys from plaintext to encrypted storage")
                return
            } catch (e: Exception) {
                Log.e(TAG, "Failed to migrate existing keys, generating new ones", e)
            }
        }

        generateNewKeys()
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

    fun getAdbFormattedPublicKey(): ByteArray {
        ensureKeys()
        val rsaPubKey = keyPair!!.public as java.security.interfaces.RSAPublicKey
        val bos = java.io.ByteArrayOutputStream()
        val algo = "ssh-rsa".toByteArray(Charsets.UTF_8)
        writeUint32(bos, algo.size)
        bos.write(algo)
        writeMpInt(bos, rsaPubKey.publicExponent.toByteArray())
        writeMpInt(bos, rsaPubKey.modulus.toByteArray())
        val b64 = Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
        return "$b64 wuwaconfig@android\u0000".toByteArray()
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

    fun signToken(token: ByteArray): ByteArray {
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

    fun regenerateKeys(): Result<Unit> {
        Log.d(TAG, "Regenerating RSA keys")
        return runCatching {
            synchronized(keysLoadedLock) {
                generateNewKeys()
                keysLoaded = true
            }
        }
    }
}
