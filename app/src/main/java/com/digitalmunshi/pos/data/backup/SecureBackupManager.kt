package com.digitalmunshi.pos.data.backup

import android.content.Context
import com.digitalmunshi.pos.core.security.EncryptionManager
import com.digitalmunshi.pos.data.local.database.DigitalMunshiDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.sqlcipher.database.SQLiteDatabase
import java.io.*
import java.nio.ByteBuffer
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.spec.GCMParameterSpec

enum class BackupProfile(val code: Byte) {
    LEAN(0x01), // Database + App Preferences
    FULL(0x02)  // Database + Invoices + Product Images + Preferences
}

class InvalidBackupException(message: String) : Exception(message)
class DecryptionFailedException(message: String) : Exception(message)
class DatabaseIntegrityException(message: String) : Exception(message)

class SecureBackupManager(private val context: Context) {

    companion object {
        private val MAGIC_HEADER = byteArrayOf(0x44, 0x4D, 0x42, 0x31) // "DMB1" (Digital Munshi Backup v1)
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val AES_GCM_ALGORITHM = "AES/GCM/NoPadding"
    }

    /**
     * Creates an encrypted local backup archive saved directly to a user-selected SAF OutputStream.
     */
    suspend fun createEncryptedBackup(
        profile: BackupProfile,
        userPin: String,
        outputStream: OutputStream
    ): Result<Long> = withContext(Dispatchers.IO) {
        require(userPin.length >= 4) { "Backup PIN must be at least 4 digits." }

        try {
            // 1. Generate cryptographic salt and IV
            val salt = EncryptionManager.generateRandomSalt(16)
            val iv = EncryptionManager.generateGcmIv()
            val secretKey = EncryptionManager.deriveKeyFromPin(userPin.toCharArray(), salt)

            // 2. Initialize AES-GCM Cipher
            val cipher = Cipher.getInstance(AES_GCM_ALGORITHM)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))

            // 3. Write unencrypted header: MAGIC (4B) + PROFILE (1B) + SALT (16B) + IV (12B)
            outputStream.write(MAGIC_HEADER)
            outputStream.write(byteArrayOf(profile.code))
            outputStream.write(salt)
            outputStream.write(iv)

            // 4. Stream encrypted ZIP payload
            val cipherOut = CipherOutputStream(outputStream, cipher)
            val zipOut = ZipOutputStream(BufferedOutputStream(cipherOut))

            // Pack Database
            val dbFile = DigitalMunshiDatabase.getDatabaseFile(context)
            if (dbFile.exists()) {
                addFileToZip(zipOut, dbFile, "database/digital_munshi_encrypted.db")
            }

            // Pack Preferences/Settings
            val prefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
            if (prefsDir.exists() && prefsDir.isDirectory) {
                prefsDir.listFiles()?.forEach { prefFile ->
                    if (prefFile.isFile) {
                        addFileToZip(zipOut, prefFile, "shared_prefs/${prefFile.name}")
                    }
                }
            }

            // Pack Invoices & Images if Full profile
            if (profile == BackupProfile.FULL) {
                val invoicesDir = File(context.filesDir, "invoices")
                if (invoicesDir.exists() && invoicesDir.isDirectory) {
                    invoicesDir.listFiles()?.forEach { file ->
                        if (file.isFile) addFileToZip(zipOut, file, "invoices/${file.name}")
                    }
                }

                val imagesDir = File(context.filesDir, "product_images")
                if (imagesDir.exists() && imagesDir.isDirectory) {
                    imagesDir.listFiles()?.forEach { file ->
                        if (file.isFile) addFileToZip(zipOut, file, "product_images/${file.name}")
                    }
                }
            }

            zipOut.finish()
            cipherOut.flush()
            cipherOut.close()

            Result.success(dbFile.length())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Restores an encrypted local backup with atomic staging and rollback protection:
     * 1. Validates magic header
     * 2. Authenticates PIN via AES-GCM (fails immediately with AEADBadTagException if wrong PIN)
     * 3. Unpacks to isolated staging folder
     * 4. Validates database with SQLCipher PRAGMA integrity_check
     * 5. Backs up live DB to rollback directory before atomic swap
     * 6. Automatically rolls back if swap or opening fails!
     */
    suspend fun restoreEncryptedBackup(
        inputStream: InputStream,
        userPin: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val stagingDir = File(context.cacheDir, "restore_staging_${System.currentTimeMillis()}")
        val rollbackDir = File(context.filesDir, "db_rollback_safety")

        try {
            // 1. Validate Magic Header
            val header = ByteArray(MAGIC_HEADER.size)
            if (inputStream.read(header) != MAGIC_HEADER.size || !header.contentEquals(MAGIC_HEADER)) {
                return@withContext Result.failure(InvalidBackupException("Invalid backup file format or signature."))
            }

            // 2. Read Profile, Salt (16B), IV (12B)
            val profileCode = inputStream.read()
            val salt = ByteArray(16)
            if (inputStream.read(salt) != 16) {
                return@withContext Result.failure(InvalidBackupException("Corrupt backup file: incomplete salt."))
            }

            val iv = ByteArray(12)
            if (inputStream.read(iv) != 12) {
                return@withContext Result.failure(InvalidBackupException("Corrupt backup file: incomplete IV."))
            }

            // 3. Derive key and initialize GCM cipher
            val secretKey = EncryptionManager.deriveKeyFromPin(userPin.toCharArray(), salt)
            val cipher = Cipher.getInstance(AES_GCM_ALGORITHM)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))

            // 4. Decrypt and unpack into staging directory
            stagingDir.mkdirs()
            val cipherIn = CipherInputStream(inputStream, cipher)
            val zipIn = ZipInputStream(BufferedInputStream(cipherIn))

            var entry: ZipEntry? = zipIn.nextEntry
            while (entry != null) {
                val targetFile = File(stagingDir, entry.name)
                // Prevent Zip Slip vulnerability
                if (!targetFile.canonicalPath.startsWith(stagingDir.canonicalPath)) {
                    throw SecurityException("Zip Slip directory traversal detected!")
                }

                if (entry.isDirectory) {
                    targetFile.mkdirs()
                } else {
                    targetFile.parentFile?.mkdirs()
                    FileOutputStream(targetFile).use { out ->
                        zipIn.copyTo(out)
                    }
                }
                zipIn.closeEntry()
                entry = zipIn.nextEntry
            }

            val stagedDb = File(stagingDir, "database/digital_munshi_encrypted.db")
            if (!stagedDb.exists()) {
                throw InvalidBackupException("Backup does not contain a valid database file.")
            }

            // 5. Integrity Check: verify staged database can be opened with SQLCipher
            val passphrase = EncryptionManager.getOrCreateDatabasePassphrase(context)
            verifyDatabaseIntegrity(stagedDb, passphrase)

            // 6. Safe Atomic Swap with Rollback Protection
            DigitalMunshiDatabase.closeDatabase()

            val liveDb = DigitalMunshiDatabase.getDatabaseFile(context)
            rollbackDir.mkdirs()
            if (liveDb.exists()) {
                liveDb.copyTo(File(rollbackDir, liveDb.name), overwrite = true)
                // Copy WAL and SHM if present
                val walFile = File(liveDb.parent, "${liveDb.name}-wal")
                val shmFile = File(liveDb.parent, "${liveDb.name}-shm")
                if (walFile.exists()) walFile.copyTo(File(rollbackDir, walFile.name), overwrite = true)
                if (shmFile.exists()) shmFile.copyTo(File(rollbackDir, shmFile.name), overwrite = true)
            }

            try {
                // Delete existing WAL and SHM
                File(liveDb.parent, "${liveDb.name}-wal").delete()
                File(liveDb.parent, "${liveDb.name}-shm").delete()

                // Replace live database with staged database
                stagedDb.copyTo(liveDb, overwrite = true)

                // Restore preferences if present
                val stagedPrefs = File(stagingDir, "shared_prefs")
                if (stagedPrefs.exists() && stagedPrefs.isDirectory) {
                    val livePrefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
                    stagedPrefs.listFiles()?.forEach { pref ->
                        pref.copyTo(File(livePrefsDir, pref.name), overwrite = true)
                    }
                }

                // Verify live database opens cleanly
                DigitalMunshiDatabase.getInstance(context, passphrase)
                rollbackDir.deleteRecursively()
            } catch (swapError: Exception) {
                // AUTOMATIC ROLLBACK IF SWAP FAILS
                rollbackLiveDatabase(liveDb, rollbackDir)
                throw swapError
            }

            Result.success(Unit)
        } catch (aead: AEADBadTagException) {
            stagingDir.deleteRecursively()
            Result.failure(DecryptionFailedException("Incorrect Backup PIN or corrupted archive."))
        } catch (e: Exception) {
            stagingDir.deleteRecursively()
            Result.failure(e)
        } finally {
            stagingDir.deleteRecursively()
        }
    }

    private fun verifyDatabaseIntegrity(dbFile: File, passphrase: ByteArray) {
        var db: SQLiteDatabase? = null
        try {
            SQLiteDatabase.loadLibs(context)
            val passChars = passphrase.map { it.toInt().toChar() }.toCharArray()
            db = SQLiteDatabase.openDatabase(
                dbFile.absolutePath,
                passChars,
                null,
                SQLiteDatabase.OPEN_READONLY
            )
            val cursor = db?.rawQuery("PRAGMA integrity_check;", null)
                ?: throw DatabaseIntegrityException("Failed to open database.")
            var isOk = false
            if (cursor.moveToFirst()) {
                val result = cursor.getString(0)
                isOk = result.equals("ok", ignoreCase = true)
            }
            cursor.close()
            if (!isOk) {
                throw DatabaseIntegrityException("Staged database failed PRAGMA integrity_check.")
            }
        } finally {
            db?.close()
        }
    }

    private fun rollbackLiveDatabase(liveDb: File, rollbackDir: File) {
        try {
            val backedUpDb = File(rollbackDir, liveDb.name)
            if (backedUpDb.exists()) {
                backedUpDb.copyTo(liveDb, overwrite = true)
            }
            val wal = File(rollbackDir, "${liveDb.name}-wal")
            if (wal.exists()) wal.copyTo(File(liveDb.parent, wal.name), overwrite = true)
            val shm = File(rollbackDir, "${liveDb.name}-shm")
            if (shm.exists()) shm.copyTo(File(liveDb.parent, shm.name), overwrite = true)
        } catch (_: Exception) {}
    }

    private fun addFileToZip(zipOut: ZipOutputStream, file: File, zipPath: String) {
        val entry = ZipEntry(zipPath)
        entry.time = file.lastModified()
        entry.size = file.length()
        zipOut.putNextEntry(entry)
        FileInputStream(file).use { input ->
            input.copyTo(zipOut)
        }
        zipOut.closeEntry()
    }
}
