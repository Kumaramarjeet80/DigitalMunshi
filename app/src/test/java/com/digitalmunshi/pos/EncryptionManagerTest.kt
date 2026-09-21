package com.digitalmunshi.pos

import com.digitalmunshi.pos.core.security.EncryptionManager
import org.junit.Assert.*
import org.junit.Test
import javax.crypto.AEADBadTagException

class EncryptionManagerTest {

    @Test
    fun `AES-256-GCM encryption and decryption round-trip succeeds with correct PIN`() {
        val userPin = "4829"
        val salt = EncryptionManager.generateRandomSalt(16)
        val iv = EncryptionManager.generateGcmIv()

        val secretKey = EncryptionManager.deriveKeyFromPin(userPin.toCharArray(), salt)

        val originalPlaintext = "Digital Munshi Encrypted SQLite Backup Payload".toByteArray(Charsets.UTF_8)

        val ciphertext = EncryptionManager.encryptAesGcm(originalPlaintext, secretKey, iv)
        assertFalse("Ciphertext must not match original plaintext", ciphertext.contentEquals(originalPlaintext))

        val decryptedBytes = EncryptionManager.decryptAesGcm(ciphertext, secretKey, iv)
        val decryptedText = String(decryptedBytes, Charsets.UTF_8)

        assertEquals("Decrypted text must match original", "Digital Munshi Encrypted SQLite Backup Payload", decryptedText)
    }

    @Test(expected = AEADBadTagException::class)
    fun `AES-256-GCM decryption fails with AEADBadTagException when wrong PIN is supplied`() {
        val correctPin = "9999"
        val wrongPin = "1111"
        val salt = EncryptionManager.generateRandomSalt(16)
        val iv = EncryptionManager.generateGcmIv()

        val correctKey = EncryptionManager.deriveKeyFromPin(correctPin.toCharArray(), salt)
        val wrongKey = EncryptionManager.deriveKeyFromPin(wrongPin.toCharArray(), salt)

        val plaintext = "Sensitive Financial Transactions".toByteArray(Charsets.UTF_8)
        val ciphertext = EncryptionManager.encryptAesGcm(plaintext, correctKey, iv)

        // Attempting to decrypt with wrong key MUST throw AEADBadTagException
        EncryptionManager.decryptAesGcm(ciphertext, wrongKey, iv)
    }

    @Test
    fun `PIN verification logic verifies matching hashes`() {
        val pin = "1234"
        val salt = EncryptionManager.generateRandomSalt(16)
        val hash = EncryptionManager.hashPin(pin, salt)

        assertTrue(EncryptionManager.verifyPin(pin, salt, hash))
        assertFalse(EncryptionManager.verifyPin("0000", salt, hash))
    }
}
