package com.aioshell.app.core.data.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 基于 Android Keystore 的 AES/GCM 加密器，用于保护 API Key。
 * 主密钥保存在系统 Keystore，不可导出；密文由调用方存放于 DataStore。
 */
@Singleton
class SecurityCrypto @Inject constructor() {

    private companion object {
        const val ALIAS = "aioshell_master_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
        const val TAG_SIZE_BITS = 128
    }

    private val masterKey: SecretKey by lazy(::getOrCreateKey)

    private fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(
            ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    /**
     * 加密明文，返回 base64(iv || ciphertext)。
     */
    fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, masterKey)
        val ciphertext = cipher.doFinal(plain.encodeToByteArray())
        // iv 置于密文前
        val blob = cipher.iv + ciphertext
        return Base64.getEncoder().encodeToString(blob)
    }

    /**
     * 使用上述格式解密，失败时抛出 [SecurityException]。
     */
    fun decrypt(payloadB64: String): String {
        val blob = Base64.getDecoder().decode(payloadB64)
        require(blob.size > IV_SIZE) { "密文格式非法" }
        val iv = blob.copyOfRange(0, IV_SIZE)
        val ciphertext = blob.copyOfRange(IV_SIZE, blob.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, masterKey, GCMParameterSpec(TAG_SIZE_BITS, iv))
        return cipher.doFinal(ciphertext).decodeToString()
    }
}