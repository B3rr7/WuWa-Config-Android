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
    }

    private var keyPair: KeyPair? = null
    private val publicKeyFile: File
        get() = File(context.filesDir, "adbkey.pub")
    private val privateKeyFile: File
        get() = File(context.filesDir, "adbkey")

    private val masterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    init {
        loadOrGenerateKeys()
    }

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
        } catch (_: Exception) {
            null
        }
    }

    private fun writeEncryptedBytes(
        file: File,
        bytes: ByteArray,
    ) {
        file.parentFile?.mkdirs()
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
        Log.d(TAG, "Signing ${token.size}B token with SHA1withRSA")
        val signature = Signature.getInstance("SHA1withRSA")
        signature.initSign(keyPair!!.private)
        signature.update(token)
        val sig = signature.sign()
        Log.d(TAG, "Signature: ${sig.size}B")
        return sig
    }

    fun regenerateKeys() {
        Log.d(TAG, "Regenerating RSA keys")
        generateNewKeys()
    }
}
