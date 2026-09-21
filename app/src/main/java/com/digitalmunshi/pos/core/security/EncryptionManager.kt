package com.digitalmunshi.pos.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object EncryptionManager {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val MASTER_KEY_ALIAS = "digital_munshi_master_key"
    private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val AES_GCM_ALGORITHM = "AES/GCM/NoPadding"
    private const val GCM_IV_LENGTH_BYTES = 12
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val PBKDF2_ITERATIONS = 100_000
    private const val KEY_LENGTH_BITS = 256

    private val secureRandom = SecureRandom()

    private fun ensureKeystoreMasterKey() {
        runCatching {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (!keyStore.containsAlias(MASTER_KEY_ALIAS)) {
                val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
                val keyGenSpec = KeyGenParameterSpec.Builder(
                    MASTER_KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setUserAuthenticationRequired(false)
                    .setRandomizedEncryptionRequired(true)
                    .build()

                keyGenerator.init(keyGenSpec)
                keyGenerator.generateKey()
            }
        }
    }

    /**
     * Derives a deterministic 256-bit passphrase for SQLCipher, securely rooted in
     * the hardware-backed Android Keystore master key.
     */
    fun getOrCreateDatabasePassphrase(context: Context): ByteArray {
        ensureKeystoreMasterKey()
        val prefs = context.getSharedPreferences("secure_db_seed", Context.MODE_PRIVATE)
        var encryptedSeedHex = prefs.getString("encrypted_db_seed", null)
        var ivHex = prefs.getString("db_seed_iv", null)

        if (encryptedSeedHex == null || ivHex == null) {
            val rawSeed = ByteArray(32).apply { secureRandom.nextBytes(this) }
            val (encrypted, iv) = encryptWithKeystore(rawSeed)
            prefs.edit()
                .putString("encrypted_db_seed", encrypted.toHexString())
                .putString("db_seed_iv", iv.toHexString())
                .apply()
            return rawSeed
        } else {
            val encrypted = encryptedSeedHex.hexToByteArray()
            val iv = ivHex.hexToByteArray()
            return decryptWithKeystore(encrypted, iv)
        }
    }

    private fun encryptWithKeystore(plainBytes: ByteArray): Pair<ByteArray, ByteArray> {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val secretKey = keyStore.getKey(MASTER_KEY_ALIAS, null) as SecretKey
        val cipher = Cipher.getInstance(AES_GCM_ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plainBytes)
        return Pair(encrypted, iv)
    }

    private fun decryptWithKeystore(encryptedBytes: ByteArray, iv: ByteArray): ByteArray {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val secretKey = keyStore.getKey(MASTER_KEY_ALIAS, null) as SecretKey
        val cipher = Cipher.getInstance(AES_GCM_ALGORITHM)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        return cipher.doFinal(encryptedBytes)
    }

    /**
     * Derives a 256-bit AES secret key from a user PIN and random salt using PBKDF2 (100,000 rounds).
     */
    fun deriveKeyFromPin(pin: CharArray, salt: ByteArray): SecretKey {
        val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        val spec = PBEKeySpec(pin, salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        val tmp = factory.generateSecret(spec)
        return SecretKeySpec(tmp.encoded, "AES")
    }

    /**
     * Generates a cryptographically strong random salt.
     */
    fun generateRandomSalt(lengthBytes: Int = 16): ByteArray {
        val salt = ByteArray(lengthBytes)
        secureRandom.nextBytes(salt)
        return salt
    }

    /**
     * Generates a cryptographically strong random 12-byte IV for AES-GCM.
     */
    fun generateGcmIv(): ByteArray {
        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        secureRandom.nextBytes(iv)
        return iv
    }

    /**
     * Hashes a user PIN for offline role authentication (e.g. Admin/Cashier login).
     */
    fun hashPin(pin: String, salt: ByteArray): String {
        val derivedKey = deriveKeyFromPin(pin.toCharArray(), salt)
        return derivedKey.encoded.toHexString()
    }

    fun verifyPin(pin: String, salt: ByteArray, expectedHash: String): Boolean {
        val calculatedHash = hashPin(pin, salt)
        return calculatedHash.equals(expectedHash, ignoreCase = true)
    }

    /**
     * Raw AES-256-GCM encryption with derived SecretKey.
     */
    fun encryptAesGcm(plainData: ByteArray, secretKey: SecretKey, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(AES_GCM_ALGORITHM)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)
        return cipher.doFinal(plainData)
    }

    /**
     * Raw AES-256-GCM decryption with derived SecretKey.
     * Throws AEADBadTagException if wrong PIN or corrupted payload!
     */
    fun decryptAesGcm(encryptedData: ByteArray, secretKey: SecretKey, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(AES_GCM_ALGORITHM)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        return cipher.doFinal(encryptedData)
    }

    fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }

    fun String.hexToByteArray(): ByteArray {
        val len = length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(this[i], 16) shl 4) + Character.digit(this[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }
}
