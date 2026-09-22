package com.digitalmunshi.pos.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
    var showPinText by remember { mutableStateOf(false) }
    var selectedProfile by remember { mutableStateOf(BackupProfile.LEAN) }
    var showPinDialogForRestore by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Encrypted Local Vault",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color.White
                        )
                        Text(
                            "Offline AES-256-GCM Backup & Restore",
                            fontSize = 12.sp,
                            color = Slate400
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PrimaryNavy,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Slate100)
        ) {
            val isWideScreen = maxWidth >= 720.dp
            val scrollState = rememberScrollState()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Security Guarantee Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = PrimaryNavy),
                    shape = RoundedCornerShape(14.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = BrandEmerald.copy(alpha = 0.2f),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = BrandEmerald,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Air-Gapped Military-Grade Security",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "Encrypted locally using AES-256-GCM with 100,000 PBKDF2 rounds. Zero internet permission, zero cloud transmission.",
                                color = Slate300,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                // Operating Status Indicator
                if (uiState.isOperating) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = BrandEmerald,
                                strokeWidth = 3.dp
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Text(
                                uiState.progressMessage ?: "Processing encrypted archive...",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = Slate800
                            )
                        }
                    }
                }

                // Success Feedback Banner
                uiState.successMessage?.let { success ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = BrandEmeraldLight),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = BrandEmeraldDark)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                success,
                                color = Color(0xFF065F46),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { viewModel.clearMessages() },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = BrandEmeraldDark, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                // Error Feedback Banner
                uiState.errorMessage?.let { error ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = ErrorCrimsonLight),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = ErrorCrimson)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                error,
                                color = Color(0xFF991B1B),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { viewModel.clearMessages() },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = ErrorCrimson, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                // Responsive Panel Layout: Side-by-side on wide screens, stacked on compact screens
                if (isWideScreen) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CreateBackupCard(
                            modifier = Modifier.weight(1f),
                            selectedProfile = selectedProfile,
                            onProfileSelected = { selectedProfile = it },
                            userPin = userPin,
                            onUserPinChange = { if (it.length <= 16) userPin = it },
                            showPinText = showPinText,
                            onToggleShowPin = { showPinText = !showPinText },
                            isOperating = uiState.isOperating,
                            onCreateBackup = {
                                onRequestCreateBackup(selectedProfile, userPin)
                            }
                        )

                        RestoreBackupCard(
                            modifier = Modifier.weight(1f),
                            isOperating = uiState.isOperating,
                            onInitiateRestore = { showPinDialogForRestore = true }
                        )
                    }
                } else {
                    CreateBackupCard(
                        modifier = Modifier.fillMaxWidth(),
                        selectedProfile = selectedProfile,
                        onProfileSelected = { selectedProfile = it },
                        userPin = userPin,
                        onUserPinChange = { if (it.length <= 16) userPin = it },
                        showPinText = showPinText,
                        onToggleShowPin = { showPinText = !showPinText },
                        isOperating = uiState.isOperating,
                        onCreateBackup = {
                            onRequestCreateBackup(selectedProfile, userPin)
                        }
                    )

                    RestoreBackupCard(
                        modifier = Modifier.fillMaxWidth(),
                        isOperating = uiState.isOperating,
                        onInitiateRestore = { showPinDialogForRestore = true }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Modal: PIN Confirmation for Decryption & Restore
    if (showPinDialogForRestore) {
        var restorePinInput by remember { mutableStateOf("") }
        var showRestorePin by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showPinDialogForRestore = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = PrimaryNavy, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Decryption PIN Required", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Enter the 4+ digit encryption PIN used to seal this backup file:",
                        fontSize = 13.sp,
                        color = Slate700
                    )
                    OutlinedTextField(
                        value = restorePinInput,
                        onValueChange = { restorePinInput = it },
                        singleLine = true,
                        placeholder = { Text("Enter PIN (min 4 digits)") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = if (showRestorePin) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showRestorePin = !showRestorePin }) {
                                Icon(
                                    if (showRestorePin) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle PIN Visibility"
                                )
                            }
                        }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (restorePinInput.length >= 4) {
                            showPinDialogForRestore = false
                            onRequestRestoreBackup(restorePinInput)
                        }
                    },
                    enabled = restorePinInput.length >= 4,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryNavy)
                ) {
                    Text("Select Archive File")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showPinDialogForRestore = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun CreateBackupCard(
    modifier: Modifier = Modifier,
    selectedProfile: BackupProfile,
    onProfileSelected: (BackupProfile) -> Unit,
    userPin: String,
    onUserPinChange: (String) -> Unit,
    showPinText: Boolean,
    onToggleShowPin: () -> Unit,
    isOperating: Boolean,
    onCreateBackup: () -> Unit
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = PrimaryNavy.copy(alpha = 0.1f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Download, contentDescription = null, tint = PrimaryNavy, modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text("CREATE ENCRYPTED BACKUP", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = PrimaryNavy)
                    Text("Export sealed .dmb archive via SAF", fontSize = 11.sp, color = Slate500)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text("Select Archive Scope:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Slate700)
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = (selectedProfile == BackupProfile.LEAN),
                    onClick = { onProfileSelected(BackupProfile.LEAN) },
                    label = { Text("Lean (DB + Settings)", fontSize = 12.sp) },
                    leadingIcon = if (selectedProfile == BackupProfile.LEAN) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null
                )
                FilterChip(
                    selected = (selectedProfile == BackupProfile.FULL),
                    onClick = { onProfileSelected(BackupProfile.FULL) },
                    label = { Text("Full (+ Bills & Media)", fontSize = 12.sp) },
                    leadingIcon = if (selectedProfile == BackupProfile.FULL) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text("Set Encryption PIN:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Slate700)
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = userPin,
                onValueChange = onUserPinChange,
                label = { Text("Custom Recovery PIN") },
                placeholder = { Text("Minimum 4 digits") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                visualTransformation = if (showPinText) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = onToggleShowPin) {
                        Icon(
                            if (showPinText) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Toggle PIN Visibility"
                        )
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onCreateBackup,
                enabled = userPin.length >= 4 && !isOperating,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald)
            ) {
                Icon(Icons.Default.SaveAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Export Backup (.dmb)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun RestoreBackupCard(
    modifier: Modifier = Modifier,
    isOperating: Boolean,
    onInitiateRestore: () -> Unit
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = PrimarySlate.copy(alpha = 0.1f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Restore, contentDescription = null, tint = PrimarySlate, modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text("RESTORE FROM BACKUP", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = PrimaryNavy)
                    Text("Verify & restore from .dmb file", fontSize = 11.sp, color = Slate500)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Surface(
                color = Slate50,
                shape = RoundedCornerShape(10.dp),
                border = ButtonDefaults.outlinedButtonBorder
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "Atomic Verification Process:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = Slate800
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "1. Validates AES-256-GCM authentication tag.\n" +
                        "2. Decrypts to an isolated staging sandbox.\n" +
                        "3. Executes SQLite PRAGMA integrity_check.\n" +
                        "4. Automatic rollback preserves current data if corrupt.",
                        fontSize = 11.sp,
                        color = Slate600,
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = onInitiateRestore,
                enabled = !isOperating,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryNavy)
            ) {
                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Select .dmb File to Restore", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}
