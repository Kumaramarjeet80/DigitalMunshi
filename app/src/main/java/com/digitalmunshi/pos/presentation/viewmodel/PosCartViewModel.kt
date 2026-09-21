package com.digitalmunshi.pos.presentation.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.digitalmunshi.pos.core.hardware.display.CustomerFacingPresentation
import com.digitalmunshi.pos.core.hardware.escpos.EscPosDriver
import com.digitalmunshi.pos.core.hardware.escpos.StoreReceiptMetadata
import com.digitalmunshi.pos.core.hardware.printer.UsbEscPosPrinter
import com.digitalmunshi.pos.core.qr.DynamicUpiQrGenerator
import com.digitalmunshi.pos.core.security.SessionManager
import com.digitalmunshi.pos.data.local.dao.BatchDao
import com.digitalmunshi.pos.data.local.dao.KhataDao
import com.digitalmunshi.pos.data.local.dao.ProductDao
import com.digitalmunshi.pos.data.local.dao.TransactionDao
import com.digitalmunshi.pos.data.local.entities.KhataCustomerEntity
import com.digitalmunshi.pos.data.local.entities.ProductEntity
import com.digitalmunshi.pos.data.local.entities.TransactionEntity
import com.digitalmunshi.pos.data.local.entities.TransactionItemEntity
import com.digitalmunshi.pos.domain.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

data class PosCartUiState(
    val items: List<CartItem> = emptyList(),
    val totals: CartTotals = CartTotals(0.0, 0.0, 0.0, 0.0, 0, 0.0),
    val selectedCustomer: KhataCustomerEntity? = null,
    val paymentMode: PaymentMode = PaymentMode.CASH,
    val splitCashAmount: Double = 0.0,
    val splitCreditAmount: Double = 0.0,
    val dynamicUpiUri: String? = null,
    val dynamicUpiBitmap: Bitmap? = null,
    val isProcessingCheckout: Boolean = false,
    val errorMessage: String? = null,
    val completedTransaction: TransactionEntity? = null,
    val canViewCostPrices: Boolean = false,
    val merchantVpa: String = "merchant@upi",
    val merchantName: String = "Digital Munshi POS",
    val searchQuery: String = ""
)

class PosCartViewModel(
    private val productDao: ProductDao,
    private val batchDao: BatchDao,
    private val transactionDao: TransactionDao,
    private val khataDao: KhataDao,
    private val usbPrinter: UsbEscPosPrinter? = null,
    private var customerPresentation: CustomerFacingPresentation? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(PosCartUiState())
    val uiState: StateFlow<PosCartUiState> = _uiState.asStateFlow()

    private val escPosDriver = EscPosDriver()
    private val invoiceDateFormat = SimpleDateFormat("yyMMddHHmmss", Locale.US)

    init {
        // Observe active session permissions
        viewModelScope.launch {
            SessionManager.currentSession.collect { session ->
                _uiState.update { it.copy(canViewCostPrices = SessionManager.canViewCostPrice()) }
            }
        }
    }

    fun attachCustomerDisplay(presentation: CustomerFacingPresentation) {
        this.customerPresentation = presentation
        syncCustomerDisplay()
    }

    /**
     * Real-time Scanner Event Handler:
     * Accepts barcode from Camera feed or physical USB/Bluetooth barcode guns.
     */
    fun onBarcodeScanned(barcode: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val product = productDao.getProductByBarcode(barcode.trim())
            if (product != null) {
                addProductToCart(product)
            } else {
                _uiState.update { it.copy(errorMessage = "Product with barcode '$barcode' not found.") }
            }
        }
    }

    fun onProductSelected(product: ProductEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            addProductToCart(product)
        }
    }

    private suspend fun addProductToCart(product: ProductEntity) {
        val currentItems = _uiState.value.items.toMutableList()
        val existingIndex = currentItems.indexOfFirst { it.product.id == product.id }

        // Find available batch FIFO
        val availableBatches = batchDao.getAvailableBatchesFIFO(product.id)
        val selectedBatch = availableBatches.firstOrNull()

        if (existingIndex >= 0) {
            // Increment existing item quantity by 1.0 (or 0.250 kg for weight items)
            val existing = currentItems[existingIndex]
            val delta = if (product.unitType.allowsFractional) 0.5 else 1.0
            val updated = existing.copy(quantity = existing.quantity + delta)
            currentItems[existingIndex] = updated
        } else {
            val defaultQty = if (product.unitType.allowsFractional) 1.0 else 1.0
            currentItems.add(
                CartItem(
                    product = product,
                    selectedBatch = selectedBatch,
                    quantity = defaultQty
                )
            )
        }

        recalculateCart(currentItems)
    }

    /**
     * Updates item quantity with support for fractional weights (e.g. 1.450 kg).
     * Automatically triggers Dynamic Tier Switching if quantity >= wholesaleMinQty!
     */
    fun updateItemQuantity(cartItemId: String, newQuantity: Double) {
        if (newQuantity <= 0.0) {
            removeItem(cartItemId)
            return
        }

        val updatedItems = _uiState.value.items.map { item ->
            if (item.cartItemId == cartItemId) {
                // Dynamic tier switching happens automatically via CartItem property
                item.copy(quantity = newQuantity)
            } else {
                item
            }
        }

        recalculateCart(updatedItems)
    }

    /**
     * Directly injects fractional weight reading from USB-Serial weighing scale (e.g. 1.450 kg).
     */
    fun applyWeighingScaleWeight(cartItemId: String, weightKg: Double) {
        if (weightKg > 0.0) {
            updateItemQuantity(cartItemId, weightKg)
        }
    }

    fun removeItem(cartItemId: String) {
        val updatedItems = _uiState.value.items.filterNot { it.cartItemId == cartItemId }
        recalculateCart(updatedItems)
    }

    fun clearCart() {
        recalculateCart(emptyList())
        _uiState.update {
            it.copy(
                selectedCustomer = null,
                dynamicUpiUri = null,
                dynamicUpiBitmap = null,
                completedTransaction = null,
                errorMessage = null
            )
        }
        customerPresentation?.reset()
    }

    fun setPaymentMode(mode: PaymentMode) {
        _uiState.update { it.copy(paymentMode = mode) }
        refreshDynamicUpiQr()
    }

    fun selectKhataCustomer(customer: KhataCustomerEntity?) {
        _uiState.update { it.copy(selectedCustomer = customer) }
    }

    fun setSplitAmounts(cashPortion: Double, creditPortion: Double) {
        _uiState.update {
            it.copy(
                splitCashAmount = cashPortion,
                splitCreditAmount = creditPortion
            )
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun recalculateCart(items: List<CartItem>) {
        var subtotal = 0.0
        var taxTotal = 0.0
        var totalWeight = 0.0

        for (item in items) {
            subtotal += item.lineSubtotal
            taxTotal += item.taxAmount
            if (item.product.unitType == UnitType.KG) {
                totalWeight += item.quantity
            } else if (item.product.unitType == UnitType.GRAM) {
                totalWeight += (item.quantity / 1000.0)
            }
        }

        val grandTotal = subtotal + taxTotal
        val totals = CartTotals(
            subtotal = subtotal,
            taxTotal = taxTotal,
            discountTotal = 0.0,
            grandTotal = grandTotal,
            itemCount = items.size,
            totalWeightKg = totalWeight
        )

        _uiState.update {
            it.copy(
                items = items,
                totals = totals
            )
        }

        refreshDynamicUpiQr()
        syncCustomerDisplay()
    }

    /**
     * Generates a compliant NPCI Dynamic UPI QR code populated with the exact cart total.
     */
    private fun refreshDynamicUpiQr() {
        val state = _uiState.value
        if (state.totals.grandTotal > 0.0 && (state.paymentMode == PaymentMode.UPI || state.paymentMode == PaymentMode.SPLIT)) {
            val amountToPay = if (state.paymentMode == PaymentMode.SPLIT) state.splitCashAmount else state.totals.grandTotal
            val refId = "INV${System.currentTimeMillis()}"
            val uri = DynamicUpiQrGenerator.buildUpiUri(
                payeeVpa = state.merchantVpa,
                payeeName = state.merchantName,
                amount = amountToPay,
                transactionRef = refId,
                transactionNote = "POS Billing"
            )
            val bitmap = DynamicUpiQrGenerator.generateQrBitmap(uri, 512, 512)

            _uiState.update {
                it.copy(
                    dynamicUpiUri = uri,
                    dynamicUpiBitmap = bitmap
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    dynamicUpiUri = null,
                    dynamicUpiBitmap = null
                )
            }
        }
    }

    private fun syncCustomerDisplay() {
        val state = _uiState.value
        customerPresentation?.updateCart(
            items = state.items,
            totals = state.totals,
            upiUri = state.dynamicUpiUri
        )
    }

    /**
     * Executes atomic Room @Transaction checkout:
     * - Validates inventory and Khata credit limits
     * - Decrements batch & product stock
     * - Writes invoice & Khata ledger
     * - Triggers ESC/POS receipt printing & cash drawer kick pulse
     */
    fun processCheckout() {
        val state = _uiState.value
        if (state.items.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Cart is empty.") }
            return
        }

        if ((state.paymentMode == PaymentMode.KHATA || state.paymentMode == PaymentMode.SPLIT) && state.selectedCustomer == null) {
            _uiState.update { it.copy(errorMessage = "Please select a Khata customer for credit billing.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isProcessingCheckout = true, errorMessage = null) }

            try {
                val invoiceNo = "DM-${invoiceDateFormat.format(Date())}"
                val cashierId = SessionManager.currentCashierId

                val transaction = TransactionEntity(
                    invoiceNo = invoiceNo,
                    timestamp = System.currentTimeMillis(),
                    subtotal = state.totals.subtotal,
                    taxTotal = state.totals.taxTotal,
                    discountTotal = state.totals.discountTotal,
                    grandTotal = state.totals.grandTotal,
                    paymentMode = state.paymentMode,
                    cashierId = cashierId,
                    customerId = state.selectedCustomer?.id
                )

                val itemEntities = state.items.map { item ->
                    TransactionItemEntity(
                        transactionId = 0L, // Populated atomically inside Dao
                        productId = item.product.id,
                        batchId = item.selectedBatch?.id,
                        quantity = item.quantity,
                        unitPrice = item.appliedUnitPrice,
                        costPriceSnapshot = item.costPriceSnapshot,
                        taxAmount = item.taxAmount,
                        lineTotal = item.lineTotal
                    )
                }

                // ATOMIC ROOM EXECUTION:
                val creditAmount = if (state.paymentMode == PaymentMode.SPLIT) state.splitCreditAmount else state.totals.grandTotal
                val txId = withContext(Dispatchers.IO) {
                    transactionDao.processCheckout(
                        transaction = transaction,
                        items = itemEntities,
                        creditPortionAmount = creditAmount
                    )
                }

                val completedTx = transaction.copy(id = txId)

                // Trigger Hardware Printing & Drawer Kick asynchronously
                printReceiptAndKickDrawer(completedTx, state.items, state.totals, state.dynamicUpiBitmap)

                // Notify Customer Display
                customerPresentation?.showOrderCompleted(completedTx.invoiceNo, completedTx.grandTotal)

                _uiState.update {
                    it.copy(
                        isProcessingCheckout = false,
                        completedTransaction = completedTx,
                        items = emptyList(),
                        totals = CartTotals(0.0, 0.0, 0.0, 0.0, 0, 0.0),
                        dynamicUpiUri = null,
                        dynamicUpiBitmap = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isProcessingCheckout = false,
                        errorMessage = e.message ?: "Transaction failed."
                    )
                }
            }
        }
    }

    private suspend fun printReceiptAndKickDrawer(
        tx: TransactionEntity,
        items: List<CartItem>,
        totals: CartTotals,
        upiBitmap: Bitmap?
    ) = withContext(Dispatchers.IO) {
        val printer = usbPrinter ?: return@withContext
        try {
            val receiptBytes = escPosDriver.buildReceiptBytes(
                store = StoreReceiptMetadata(),
                invoiceNo = tx.invoiceNo,
                cashierName = tx.cashierId,
                items = items,
                totals = totals,
                paymentMode = tx.paymentMode,
                upiQrBitmap = upiBitmap,
                triggerCashDrawer = (tx.paymentMode == PaymentMode.CASH)
            )
            printer.printBytes(receiptBytes)
        } catch (_: Exception) {
            // Hardware printing failure does not invalidate database commit
        }
    }
}
