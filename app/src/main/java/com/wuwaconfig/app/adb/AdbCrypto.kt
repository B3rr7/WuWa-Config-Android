package com.wuwaconfig.app.adb

import android.content.Context
import android.util.Base64
import java.io.File
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec

class AdbCrypto(private val context: Context) {
    private var keyPair: KeyPair? = null
    private val publicKeyFile: File
        get() = File(context.filesDir, "adbkey.pub")
    private val privateKeyFile: File
        get() = File(context.filesDir, "adbkey")

    init {
        loadOrGenerateKeys()
    }

    private fun loadOrGenerateKeys() {
        if (privateKeyFile.exists() && publicKeyFile.exists()) {
            try {
                val privateBytes = privateKeyFile.readBytes()
                val publicBytes = publicKeyFile.readBytes()
                val keyFactory = KeyFactory.getInstance("RSA")
                val privateKey = keyFactory.generatePrivate(PKCS8EncodedKeySpec(privateBytes))
                val publicKey = keyFactory.generatePublic(X509EncodedKeySpec(publicBytes))
                keyPair = KeyPair(publicKey, privateKey)
                return
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        generateNewKeys()
    }

    private fun generateNewKeys() {
        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(2048)
        keyPair = generator.generateKeyPair()

        privateKeyFile.parentFile?.mkdirs()
        privateKeyFile.writeBytes(keyPair!!.private.encoded)
        publicKeyFile.writeBytes(keyPair!!.public.encoded)
    }

    fun getPublicKey(): PublicKey = keyPair!!.public
    fun getPrivateKey(): PrivateKey = keyPair!!.private

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

    private fun writeUint32(stream: java.io.ByteArrayOutputStream, v: Int) {
        stream.write((v shr 24) and 0xFF)
        stream.write((v shr 16) and 0xFF)
        stream.write((v shr 8) and 0xFF)
        stream.write(v and 0xFF)
    }

    private fun writeMpInt(stream: java.io.ByteArrayOutputStream, raw: ByteArray) {
        var data = raw
        if (data.size > 1 && data[0] == 0.toByte()) data = data.copyOfRange(1, data.size)
        writeUint32(stream, data.size)
        stream.write(data)
    }

    fun signToken(token: ByteArray): ByteArray {
        val signature = Signature.getInstance("SHA1withRSA")
        signature.initSign(keyPair!!.private)
        signature.update(token)
        return signature.sign()
    }

    fun getPublicKeyMd5(): String {
        val md5 = MessageDigest.getInstance("MD5")
        md5.update(keyPair!!.public.encoded)
        return md5.digest().joinToString(":") { "%02X".format(it) }
    }
}
