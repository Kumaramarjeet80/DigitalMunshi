package com.digitalmunshi.pos.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.digitalmunshi.pos.data.backup.BackupProfile
import com.digitalmunshi.pos.presentation.theme.*
import com.digitalmunshi.pos.presentation.viewmodel.BackupRestoreViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreScreen(
    viewModel: BackupRestoreViewModel,
    onRequestCreateBackup: (BackupProfile, String) -> Unit,
    onRequestRestoreBackup: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    var userPin by remember { mutableStateOf("") }
    var selectedProfile by remember { mutableStateOf(BackupProfile.LEAN) }
    var showPinDialogForRestore by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Encrypted Local Backup", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Navy900,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Slate100)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Security Guarantee Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Navy900),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = TealAccent, modifier = Modifier.size(36.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("WhatsApp-Style AES-256-GCM Encryption", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(
                            "100% offline local archive. Sealed with 100,000 PBKDF2 rounds. Never touches any server or cloud.",
                            color = Slate400,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // Create Backup Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("CREATE ENCRYPTED BACKUP", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Navy900)
                    Spacer(modifier = Modifier.height(10.dp))

                    Text("Select Backup Profile:", fontSize = 13.sp, color = Slate700)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = (selectedProfile == BackupProfile.LEAN),
                            onClick = { selectedProfile = BackupProfile.LEAN },
                            label = { Text("Lean (DB + Settings)") }
                        )
                        FilterChip(
                            selected = (selectedProfile == BackupProfile.FULL),
                            onClick = { selectedProfile = BackupProfile.FULL },
                            label = { Text("Full (+ Invoices & Images)") }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = userPin,
                        onValueChange = { if (it.length <= 16) userPin = it },
                        label = { Text("Enter Custom Encryption PIN") },
                        placeholder = { Text("Minimum 4 digits") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            if (userPin.length >= 4) {
                                onRequestCreateBackup(selectedProfile, userPin)
                            }
                        },
                        enabled = userPin.length >= 4 && !uiState.isOperating,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Navy900)
                    ) {
                        Text("Export Encrypted Backup (.dmb)")
                    }
                }
            }

            // Restore Backup Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("RESTORE FROM BACKUP", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Navy900)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Restoring verifies the cryptographic GCM tag, extracts to a staging sandbox, runs SQLite PRAGMA integrity_check, and provides automatic rollback protection if corrupted.",
                        fontSize = 12.sp,
                        color = Slate700
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = { showPinDialogForRestore = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.isOperating
                    ) {
                        Text("Select .dmb Archive & Restore")
                    }
                }
            }

            // Status feedback
            if (uiState.isOperating) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Navy900)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(uiState.progressMessage ?: "Processing...", fontSize = 13.sp, color = Slate700)
                }
            }

            uiState.successMessage?.let { success ->
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFDCFCE7))) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(success, color = Color(0xFF166534), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            uiState.errorMessage?.let { error ->
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFEE2E2))) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = AlertRed)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(error, color = Color(0xFF991B1B), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }

    // PIN Dialog for Restore
    if (showPinDialogForRestore) {
        var restorePinInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPinDialogForRestore = false },
            title = { Text("Enter Backup Decryption PIN") },
            text = {
                Column {
                    Text("Enter the exact PIN used when creating this backup:", fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = restorePinInput,
                        onValueChange = { restorePinInput = it },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (restorePinInput.length >= 4) {
                        showPinDialogForRestore = false
                        onRequestRestoreBackup(restorePinInput)
                    }
                }) {
                    Text("Proceed to Select File")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPinDialogForRestore = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
