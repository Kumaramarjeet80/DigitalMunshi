package com.digitalmunshi.pos.presentation.ui

import android.content.Context
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Bundle
import android.view.Display
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.digitalmunshi.pos.DigitalMunshiApp
import com.digitalmunshi.pos.core.hardware.display.CustomerFacingPresentation
import com.digitalmunshi.pos.core.hardware.printer.UsbEscPosPrinter
import com.digitalmunshi.pos.core.hardware.scanner.HardwareScannerInterceptor
import com.digitalmunshi.pos.core.security.EncryptionManager
import com.digitalmunshi.pos.core.security.SessionManager
import com.digitalmunshi.pos.data.backup.BackupProfile
import com.digitalmunshi.pos.data.backup.SecureBackupManager
import com.digitalmunshi.pos.domain.models.UserRole
import com.digitalmunshi.pos.presentation.theme.DigitalMunshiTheme
import com.digitalmunshi.pos.presentation.viewmodel.BackupRestoreViewModel
import com.digitalmunshi.pos.presentation.viewmodel.ExpiryRadarViewModel
import com.digitalmunshi.pos.presentation.viewmodel.KhataViewModel
import com.digitalmunshi.pos.presentation.viewmodel.PosCartViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class CurrentScreen {
    POS,
    EXPIRY_RADAR,
    KHATA,
    BACKUP
}

class MainActivity : ComponentActivity() {

    private lateinit var hardwareScanner: HardwareScannerInterceptor
    private var customerPresentation: CustomerFacingPresentation? = null
    private lateinit var posViewModel: PosCartViewModel
    private lateinit var expiryViewModel: ExpiryRadarViewModel
    private lateinit var khataViewModel: KhataViewModel
    private lateinit var backupViewModel: BackupRestoreViewModel

    private var pendingBackupPin: String? = null
    private var pendingRestorePin: String? = null

    // SAF Launchers for Encrypted Local Backups
    private val createBackupLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri: Uri? ->
        uri?.let { destUri ->
            val pin = pendingBackupPin ?: return@let
            contentResolver.openOutputStream(destUri)?.use { outStream ->
                backupViewModel.performBackup(pin, outStream)
            }
        }
    }

    private val restoreBackupLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { srcUri ->
            val pin = pendingRestorePin ?: return@let
            contentResolver.openInputStream(srcUri)?.use { inStream ->
                backupViewModel.performRestore(pin, inStream)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as DigitalMunshiApp
        val db = app.database
        val usbPrinter = UsbEscPosPrinter(this)
        val backupManager = SecureBackupManager(this)

        // Initialize ViewModels
        posViewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return PosCartViewModel(
                    productDao = db.productDao(),
                    batchDao = db.batchDao(),
                    transactionDao = db.transactionDao(),
                    khataDao = db.khataDao(),
                    usbPrinter = usbPrinter
                ) as T
            }
        })[PosCartViewModel::class.java]

        expiryViewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return ExpiryRadarViewModel(db.batchDao()) as T
            }
        })[ExpiryRadarViewModel::class.java]

        khataViewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return KhataViewModel(db.khataDao(), db.transactionDao()) as T
            }
        })[KhataViewModel::class.java]

        backupViewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return BackupRestoreViewModel(backupManager) as T
            }
        })[BackupRestoreViewModel::class.java]

        // Connect USB thermal printer in background if plugged in
        app.applicationScope.launch {
            usbPrinter.findAndConnectPrinter()
        }

        // Initialize Hardware Barcode Gun Interceptor
        hardwareScanner = HardwareScannerInterceptor { barcode ->
            posViewModel.onBarcodeScanned(barcode)
        }

        // Initialize Dual-Display Customer-Facing Screen (Presentation API)
        setupCustomerPresentation()

        setContent {
            DigitalMunshiTheme {
                var currentScreen by remember { mutableStateOf(CurrentScreen.POS) }
                var showPinDialog by remember { mutableStateOf(false) }

                val products by db.productDao().getAllProductsFlow().collectAsState(initial = emptyList())
                val customers by db.khataDao().getAllCustomersFlow().collectAsState(initial = emptyList())

                when (currentScreen) {
                    CurrentScreen.POS -> {
                        PosScreen(
                            viewModel = posViewModel,
                            availableProducts = products,
                            khataCustomers = customers,
                            onOpenExpiryRadar = { currentScreen = CurrentScreen.EXPIRY_RADAR },
                            onOpenKhata = { currentScreen = CurrentScreen.KHATA },
                            onOpenBackup = { currentScreen = CurrentScreen.BACKUP },
                            onSwitchUser = { showPinDialog = true }
                        )
                    }
                    CurrentScreen.EXPIRY_RADAR -> {
                        ExpiryRadarScreen(
                            viewModel = expiryViewModel,
                            onNavigateBack = { currentScreen = CurrentScreen.POS }
                        )
                    }
                    CurrentScreen.KHATA -> {
                        KhataLedgerScreen(
                            viewModel = khataViewModel,
                            onNavigateBack = { currentScreen = CurrentScreen.POS }
                        )
                    }
                    CurrentScreen.BACKUP -> {
                        BackupRestoreScreen(
                            viewModel = backupViewModel,
                            onRequestCreateBackup = { profile, pin ->
                                pendingBackupPin = pin
                                backupViewModel.setProfile(profile)
                                createBackupLauncher.launch("DigitalMunshi_Backup_${System.currentTimeMillis()}.dmb")
                            },
                            onRequestRestoreBackup = { pin ->
                                pendingRestorePin = pin
                                restoreBackupLauncher.launch(arrayOf("*/*"))
                            },
                            onNavigateBack = { currentScreen = CurrentScreen.POS }
                        )
                    }
                }

                // PIN Dialog for Role Switch (Admin vs Cashier)
                if (showPinDialog) {
                    AdminPinDialog(
                        onDismiss = { showPinDialog = false },
                        onVerifyPin = { enteredPin ->
                            app.applicationScope.launch {
                                val adminUser = db.userDao().getUsersByRole(UserRole.ADMIN).firstOrNull()
                                if (adminUser != null) {
                                    val salt = EncryptionManager.run { adminUser.salt.hexToByteArray() }
                                    if (EncryptionManager.verifyPin(enteredPin, salt, adminUser.pinHash)) {
                                        SessionManager.login(adminUser)
                                        withContext(Dispatchers.Main) {
                                            showPinDialog = false
                                        }
                                    }
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    /**
     * Intercepts physical USB and Bluetooth HID barcode guns directly at the Activity window.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (hardwareScanner.dispatchKeyEvent(event)) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    /**
     * Dual-Display Engine:
     * Detects external HDMI, USB-C, or secondary built-in customer screens (Sunmi, Clover, Posiflex)
     * and mounts the reactive Compose CustomerFacingPresentation.
     */
    private fun setupCustomerPresentation() {
        val displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val presentationDisplays = displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)

        if (presentationDisplays.isNotEmpty()) {
            val secondaryDisplay = presentationDisplays[0]
            val presentation = CustomerFacingPresentation(this, secondaryDisplay)
            presentation.show()
            this.customerPresentation = presentation
            posViewModel.attachCustomerDisplay(presentation)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        customerPresentation?.dismiss()
    }
}

@Composable
fun AdminPinDialog(
    onDismiss: () -> Unit,
    onVerifyPin: (String) -> Unit
) {
    var pin by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter Admin PIN to Unlock") },
        text = {
            Column {
                Text("Cashier lock active. Enter Admin PIN (Default: 1234):", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 8) pin = it },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(onClick = { onVerifyPin(pin) }) {
                Text("Unlock")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
