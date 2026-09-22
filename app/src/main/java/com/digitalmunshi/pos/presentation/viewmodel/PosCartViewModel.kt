package com.digitalmunshi.pos.presentation.viewmodel

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.digitalmunshi.pos.core.hardware.display.CustomerFacingPresentation
import com.digitalmunshi.pos.core.hardware.escpos.EscPosDriver
import com.digitalmunshi.pos.core.hardware.escpos.StoreReceiptMetadata
import com.digitalmunshi.pos.core.hardware.printer.InvoicePdfGenerator
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
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

data class PosUiState(
    val productList: List<ProductEntity> = emptyList(),
    val filteredProducts: List<ProductEntity> = emptyList(),
    val categories: List<String> = listOf("All"),
    val selectedCategory: String = "All",
    val searchQuery: String = "",
    val cartItems: List<CartItem> = emptyList(),
    val totals: CartTotals = CartTotals(0.0, 0.0, 0.0, 0.0, 0, 0.0),
    val selectedCustomer: KhataCustomerEntity? = null,
    val paymentMode: PaymentMode = PaymentMode.CASH,
    val tenderedCashAmount: Double = 0.0,
    val changeDueAmount: Double = 0.0,
    val splitCashAmount: Double = 0.0,
    val splitCreditAmount: Double = 0.0,
    val dynamicUpiUri: String? = null,
    val dynamicUpiBitmap: Bitmap? = null,
    val isProcessingCheckout: Boolean = false,
    val errorMessage: String? = null,
    val completedTransaction: TransactionEntity? = null,
    val lastGeneratedInvoiceFile: File? = null,
    val heldCarts: Map<String, List<CartItem>> = emptyMap(),
    val canViewCostPrices: Boolean = false,
    val merchantVpa: String = "merchant@upi",
    val merchantName: String = "Digital Munshi Store"
)

class PosCartViewModel(
    private val productDao: ProductDao,
    private val batchDao: BatchDao,
    private val transactionDao: TransactionDao,
    private val khataDao: KhataDao,
    private val usbPrinter: UsbEscPosPrinter? = null,
    private var customerPresentation: CustomerFacingPresentation? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(PosUiState())
    val uiState: StateFlow<PosUiState> = _uiState.asStateFlow()

    private val escPosDriver = EscPosDriver()
    private val invoiceDateFormat = SimpleDateFormat("yyMMddHHmmss", Locale.US)

    init {
        // 1. Observe products directly from Room SQLCipher DB Flow
        viewModelScope.launch {
            productDao.getAllProductsFlow().collect { allProducts ->
                val distinctCategories = listOf("All") + allProducts.map { it.category }.distinct().sorted()
                _uiState.update { current ->
                    current.copy(
                        productList = allProducts,
                        categories = distinctCategories,
                        filteredProducts = filterProducts(allProducts, current.searchQuery, current.selectedCategory)
                    )
                }
            }
        }

        // 2. Observe active session permissions (Admin vs Cashier)
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

    // --- Search & Filter Logic ---
    fun setSearchQuery(query: String) {
        _uiState.update { current ->
            current.copy(
                searchQuery = query,
                filteredProducts = filterProducts(current.productList, query, current.selectedCategory)
            )
        }
    }

    fun setCategoryFilter(category: String) {
        _uiState.update { current ->
            current.copy(
                selectedCategory = category,
                filteredProducts = filterProducts(current.productList, current.searchQuery, category)
            )
        }
    }

    private fun filterProducts(products: List<ProductEntity>, query: String, category: String): List<ProductEntity> {
        return products.filter { product ->
            val matchesCategory = (category == "All" || product.category.equals(category, ignoreCase = true))
            val matchesQuery = query.isBlank() ||
                    product.name.contains(query, ignoreCase = true) ||
                    product.barcode.contains(query, ignoreCase = true) ||
                    product.sku.contains(query, ignoreCase = true)
            matchesCategory && matchesQuery
        }
    }

    // --- Real-Time Scanner Event Handler ---
    fun onBarcodeScanned(barcode: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val product = productDao.getProductByBarcode(barcode.trim())
            if (product != null) {
                addProductToCart(product)
            } else {
                _uiState.update { it.copy(errorMessage = "Barcode '$barcode' not found.") }
            }
        }
    }

    fun onProductSelected(product: ProductEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            addProductToCart(product)
        }
    }

    private suspend fun addProductToCart(product: ProductEntity) {
        val currentItems = _uiState.value.cartItems.toMutableList()
        val existingIndex = currentItems.indexOfFirst { it.product.id == product.id }

        val availableBatches = batchDao.getAvailableBatchesFIFO(product.id)
        val selectedBatch = availableBatches.firstOrNull()

        if (existingIndex >= 0) {
            val existing = currentItems[existingIndex]
            val delta = if (product.unitType.allowsFractional) 0.5 else 1.0
            currentItems[existingIndex] = existing.copy(quantity = existing.quantity + delta)
        } else {
            val defaultQty = 1.0
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

    // --- Cart Mutations with Decimal Weights & Dynamic Tier Switching ---
    fun updateItemQuantity(cartItemId: String, newQuantity: Double) {
        if (newQuantity <= 0.0) {
            removeItem(cartItemId)
            return
        }

        val updatedItems = _uiState.value.cartItems.map { item ->
            if (item.cartItemId == cartItemId) {
                // Dynamic tier switching recalculates automatically via CartItem property
                item.copy(quantity = newQuantity)
            } else {
                item
            }
        }

        recalculateCart(updatedItems)
    }

    fun applyWeighingScaleWeight(cartItemId: String, weightKg: Double) {
        if (weightKg > 0.0) {
            updateItemQuantity(cartItemId, weightKg)
        }
    }

    fun removeItem(cartItemId: String) {
        val updatedItems = _uiState.value.cartItems.filterNot { it.cartItemId == cartItemId }
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
                lastGeneratedInvoiceFile = null,
                tenderedCashAmount = 0.0,
                changeDueAmount = 0.0,
                errorMessage = null
            )
        }
        customerPresentation?.reset()
    }

    // --- Hold / Park Cart Feature ---
    fun holdCurrentCart(cartName: String = "Order #${System.currentTimeMillis() % 10000}") {
        val currentItems = _uiState.value.cartItems
        if (currentItems.isEmpty()) return

        val newHeld = _uiState.value.heldCarts.toMutableMap()
        newHeld[cartName] = currentItems

        _uiState.update { it.copy(heldCarts = newHeld) }
        clearCart()
    }

    fun recallHeldCart(cartName: String) {
        val heldItems = _uiState.value.heldCarts[cartName] ?: return
        val newHeld = _uiState.value.heldCarts.toMutableMap()
        newHeld.remove(cartName)

        _uiState.update { it.copy(heldCarts = newHeld) }
        recalculateCart(heldItems)
    }

    // --- Payment & Tender Management ---
    fun setPaymentMode(mode: PaymentMode) {
        _uiState.update { it.copy(paymentMode = mode) }
        refreshDynamicUpiQr()
    }

    fun selectKhataCustomer(customer: KhataCustomerEntity?) {
        _uiState.update { it.copy(selectedCustomer = customer) }
    }

    fun setTenderedCash(tendered: Double) {
        val grandTotal = _uiState.value.totals.grandTotal
        val change = maxOf(0.0, tendered - grandTotal)
        _uiState.update {
            it.copy(
                tenderedCashAmount = tendered,
                changeDueAmount = change
            )
        }
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

    fun dismissSuccess() {
        _uiState.update { it.copy(completedTransaction = null, lastGeneratedInvoiceFile = null) }
    }

    // --- Immediate Room SQLCipher Product Persistence ---
    fun saveOrUpdateProduct(product: ProductEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (product.id == 0L) {
                    productDao.insertProduct(product)
                } else {
                    productDao.updateProduct(product)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Failed to save product: ${e.message}") }
            }
        }
    }

    fun deleteProduct(productId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            productDao.softDeleteProduct(productId)
        }
    }

    // --- Live Recalculation Engine ---
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

        val tendered = _uiState.value.tenderedCashAmount
        val change = if (tendered > 0.0) maxOf(0.0, tendered - grandTotal) else 0.0

        _uiState.update {
            it.copy(
                cartItems = items,
                totals = totals,
                changeDueAmount = change
            )
        }

        refreshDynamicUpiQr()
        syncCustomerDisplay()
    }

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
                transactionNote = "POS Bill"
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
            items = state.cartItems,
            totals = state.totals,
            upiUri = state.dynamicUpiUri
        )
    }

    // --- Atomic Checkout with Offline PDF Receipt Generation ---
    fun processCheckout(context: Context) {
        val state = _uiState.value
        if (state.cartItems.isEmpty()) {
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

                val itemEntities = state.cartItems.map { item ->
                    TransactionItemEntity(
                        transactionId = 0L,
                        productId = item.product.id,
                        batchId = item.selectedBatch?.id,
                        quantity = item.quantity,
                        unitPrice = item.appliedUnitPrice,
                        costPriceSnapshot = item.costPriceSnapshot,
                        taxAmount = item.taxAmount,
                        lineTotal = item.lineTotal
                    )
                }

                val creditAmount = if (state.paymentMode == PaymentMode.SPLIT) state.splitCreditAmount else state.totals.grandTotal

                // Atomic Room Transaction (Database Execution)
                val txId = withContext(Dispatchers.IO) {
                    transactionDao.processCheckout(
                        transaction = transaction,
                        items = itemEntities,
                        creditPortionAmount = creditAmount
                    )
                }

                val completedTx = transaction.copy(id = txId)

                // Generate Offline Printable PDF Receipt immediately in local app storage
                val generatedPdf = withContext(Dispatchers.IO) {
                    InvoicePdfGenerator.generateInvoicePdf(
                        context = context,
                        store = StoreReceiptMetadata(),
                        transaction = completedTx,
                        items = state.cartItems,
                        totals = state.totals,
                        upiQrBitmap = state.dynamicUpiBitmap
                    )
                }

                // Send to hardware thermal printer if USB printer connected
                withContext(Dispatchers.IO) {
                    printThermalReceipt(completedTx, state.cartItems, state.totals, state.dynamicUpiBitmap)
                }

                // Update customer presentation
                customerPresentation?.showOrderCompleted(completedTx.invoiceNo, completedTx.grandTotal)

                _uiState.update {
                    it.copy(
                        isProcessingCheckout = false,
                        completedTransaction = completedTx,
                        lastGeneratedInvoiceFile = generatedPdf,
                        cartItems = emptyList(),
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

    private suspend fun printThermalReceipt(
        tx: TransactionEntity,
        items: List<CartItem>,
        totals: CartTotals,
        upiBitmap: Bitmap?
    ) {
        val printer = usbPrinter ?: return
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
        } catch (_: Exception) {}
    }
}
