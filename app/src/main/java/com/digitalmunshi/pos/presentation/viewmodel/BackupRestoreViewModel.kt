package com.digitalmunshi.pos.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.digitalmunshi.pos.data.backup.BackupProfile
import com.digitalmunshi.pos.data.backup.SecureBackupManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream

data class BackupUiState(
    val isOperating: Boolean = false,
    val progressMessage: String? = null,
    val successMessage: String? = null,
    val errorMessage: String? = null,
    val backupProfile: BackupProfile = BackupProfile.LEAN
)

class BackupRestoreViewModel(
    private val backupManager: SecureBackupManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    fun setProfile(profile: BackupProfile) {
        _uiState.update { it.copy(backupProfile = profile) }
    }

    /**
     * Executes WhatsApp-style AES-256-GCM encrypted backup into SAF OutputStream.
     */
    fun performBackup(userPin: String, outputStream: OutputStream) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isOperating = true,
                    progressMessage = "Compressing & encrypting with AES-256-GCM...",
                    successMessage = null,
                    errorMessage = null
                )
            }

            val result = backupManager.createEncryptedBackup(
                profile = _uiState.value.backupProfile,
                userPin = userPin,
                outputStream = outputStream
            )

            result.fold(
                onSuccess = { bytesWritten ->
                    _uiState.update {
                        it.copy(
                            isOperating = false,
                            progressMessage = null,
                            successMessage = "Encrypted backup created successfully! (${bytesWritten / 1024} KB)"
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isOperating = false,
                            progressMessage = null,
                            errorMessage = error.message ?: "Backup failed."
                        )
                    }
                }
            )
        }
    }

    /**
     * Executes encrypted restore with automated PRAGMA integrity check and atomic rollback.
     */
    fun performRestore(userPin: String, inputStream: InputStream) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isOperating = true,
                    progressMessage = "Verifying PIN, decrypting archive & validating database...",
                    successMessage = null,
                    errorMessage = null
                )
            }

            val result = backupManager.restoreEncryptedBackup(
                inputStream = inputStream,
                userPin = userPin
            )

            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isOperating = false,
                            progressMessage = null,
                            successMessage = "Database integrity verified! Restore completed successfully."
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isOperating = false,
                            progressMessage = null,
                            errorMessage = "Restore Failed: ${error.message}. Original database preserved intact."
                        )
                    }
                }
            )
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(successMessage = null, errorMessage = null, progressMessage = null) }
    }
}
