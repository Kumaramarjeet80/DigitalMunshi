package com.digitalmunshi.pos.presentation.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.digitalmunshi.pos.core.security.SessionManager
import com.digitalmunshi.pos.data.local.entities.KhataCustomerEntity
import com.digitalmunshi.pos.data.local.entities.ProductEntity
import com.digitalmunshi.pos.domain.models.CartItem
import com.digitalmunshi.pos.domain.models.PaymentMode
import com.digitalmunshi.pos.domain.models.UnitType
import com.digitalmunshi.pos.domain.models.UserRole
import com.digitalmunshi.pos.presentation.theme.*
import com.digitalmunshi.pos.presentation.viewmodel.PosCartViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PosScreen(
    viewModel: PosCartViewModel,
    khataCustomers: List<KhataCustomerEntity>,
    onTriggerCameraScan: () -> Unit = {},
    onSwitchUser: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Dialog state controllers
    var showUpiQrModal by remember { mutableStateOf(false) }
    var showCashTenderModal by remember { mutableStateOf(false) }
    var showAddProductModal by remember { mutableStateOf(false) }
    var editingProduct by remember { mutableStateOf<ProductEntity?>(null) }
    var showCustomerSelectDialog by remember { mutableStateOf(false) }
    var showHeldCartsDialog by remember { mutableStateOf(false) }
    var decimalQtyEditItem by remember { mutableStateOf<CartItem?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate50)
    ) {
        // --- 1. TOP SECTION: Compact Search Bar + Action Buttons + Category Chips ---
        Surface(
            color = Color.White,
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                // Store Branding & Role Indicator Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = PrimaryNavy,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Storefront, contentDescription = null, tint = BrandEmerald, modifier = Modifier.size(18.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Digital Munshi POS", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = PrimaryNavy)
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = BrandEmeraldLight,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                "100% OFFLINE",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = BrandEmeraldDark,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    // Active Cashier / Admin Role Pill
                    val session by SessionManager.currentSession.collectAsState()
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (session.role == UserRole.ADMIN) BrandEmerald.copy(alpha = 0.15f) else Slate200,
                        modifier = Modifier.clickable { onSwitchUser() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (session.role == UserRole.ADMIN) Icons.Default.LockOpen else Icons.Default.Lock,
                                contentDescription = null,
                                tint = if (session.role == UserRole.ADMIN) BrandEmeraldDark else Slate700,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                if (session.role == UserRole.ADMIN) "Admin" else "Cashier",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (session.role == UserRole.ADMIN) BrandEmeraldDark else Slate700
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        placeholder = { Text("Search item, barcode or SKU...", fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        trailingIcon = {
                            if (uiState.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryNavy,
                            unfocusedBorderColor = Slate300
                        )
                    )

                    // Camera Scan Trigger Button
                    FilledTonalIconButton(
                        onClick = onTriggerCameraScan,
                        modifier = Modifier.size(46.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan Barcode", tint = PrimaryNavy)
                    }

                    // Add Product Shortcut Button
                    FilledTonalIconButton(
                        onClick = {
                            editingProduct = null
                            showAddProductModal = true
                        },
                        modifier = Modifier.size(46.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "New Product", tint = BrandEmeraldDark)
                    }

                    // Held Carts Button
                    if (uiState.heldCarts.isNotEmpty()) {
                        FilledTonalButton(
                            onClick = { showHeldCartsDialog = true },
                            contentPadding = PaddingValues(horizontal = 10.dp),
                            modifier = Modifier.height(46.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.BookmarkBorder, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("${uiState.heldCarts.size} Held", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Horizontal Category Chips Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    uiState.categories.forEach { category ->
                        FilterChip(
                            selected = (uiState.selectedCategory == category),
                            onClick = { viewModel.setCategoryFilter(category) },
                            label = { Text(category, fontSize = 12.sp) },
                            shape = RoundedCornerShape(8.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PrimaryNavy,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }
        }

        // --- 2. MIDDLE SECTION: Responsive Dual-Pane / Split Grid ---
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // LEFT / MAIN: Product Catalog Grid
            Box(
                modifier = Modifier
                    .weight(1.3f)
                    .fillMaxHeight()
            ) {
                if (uiState.filteredProducts.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Inbox, contentDescription = null, tint = Slate400, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("No matching products found", color = Slate600, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            TextButton(onClick = {
                                editingProduct = null
                                showAddProductModal = true
                            }) {
                                Text("+ Add New Product Now", color = BrandEmeraldDark, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 140.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(uiState.filteredProducts, key = { it.id }) { product ->
                            ProductRetailCard(
                                product = product,
                                canViewCost = uiState.canViewCostPrices,
                                onAddClick = { viewModel.onProductSelected(product) },
                                onEditClick = {
                                    editingProduct = product
                                    showAddProductModal = true
                                }
                            )
                        }
                    }
                }
            }

            // RIGHT / DOCK: Itemized Cart Drawer / Pane
            Card(
                modifier = Modifier
                    .weight(1.0f)
                    .fillMaxHeight(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        // Cart Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Active Cart (${uiState.cartItems.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryNavy
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (uiState.cartItems.isNotEmpty()) {
                                    FilledTonalButton(
                                        onClick = { viewModel.holdCurrentCart() },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.height(30.dp),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text("Hold", fontSize = 11.sp)
                                    }

                                    TextButton(
                                        onClick = { viewModel.clearCart() },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                        modifier = Modifier.height(30.dp)
                                    ) {
                                        Text("Clear", color = ErrorCrimson, fontSize = 11.sp)
                                    }
                                }
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Slate200)

                        // Itemized List
                        if (uiState.cartItems.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = Slate300, modifier = Modifier.size(54.dp))
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("Cart is currently empty", color = Slate400, fontSize = 13.sp)
                                    Text("Tap a card or scan barcode to add", color = Slate400, fontSize = 11.sp)
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                            ) {
                                items(uiState.cartItems, key = { it.cartItemId }) { item ->
                                    CartLineItemRow(
                                        item = item,
                                        onIncrement = { viewModel.updateItemQuantity(item.cartItemId, item.quantity + (if (item.product.unitType.allowsFractional) 0.5 else 1.0)) },
                                        onDecrement = { viewModel.updateItemQuantity(item.cartItemId, item.quantity - (if (item.product.unitType.allowsFractional) 0.5 else 1.0)) },
                                        onQuantityClick = { decimalQtyEditItem = item },
                                        onDelete = { viewModel.removeItem(item.cartItemId) }
                                    )
                                    HorizontalDivider(color = Slate100, thickness = 1.dp)
                                }
                            }
                        }
                    }

                    // Cart Summary Breakdown
                    Column(modifier = Modifier.fillMaxWidth()) {
                        HorizontalDivider(color = Slate200, thickness = 1.dp)
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Subtotal", color = Slate600, fontSize = 12.sp)
                            Text("₹${String.format(Locale.US, "%.2f", uiState.totals.subtotal)}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }

                        if (uiState.totals.taxTotal > 0.0) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Taxes (GST)", color = Slate600, fontSize = 12.sp)
                                Text("₹${String.format(Locale.US, "%.2f", uiState.totals.taxTotal)}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        }

        // --- 3. BOTTOM BAR: Fixed Material 3 Surface with Large Total & Styled Checkout Button ---
        Surface(
            color = Color.White,
            shadowElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left: Payable Grand Total Display
                Column {
                    Text("TOTAL AMOUNT", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate400)
                    Text(
                        "₹${String.format(Locale.US, "%.2f", uiState.totals.grandTotal)}",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = PrimaryNavy
                    )
                }

                // Middle: Payment Mode Selector Chips
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    PaymentMode.values().forEach { mode ->
                        FilterChip(
                            selected = (uiState.paymentMode == mode),
                            onClick = {
                                viewModel.setPaymentMode(mode)
                                if (mode == PaymentMode.KHATA && uiState.selectedCustomer == null) {
                                    showCustomerSelectDialog = true
                                }
                            },
                            label = { Text(mode.name, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(34.dp)
                        )
                    }
                }

                // Right: Action Buttons (Dynamic UPI QR & Complete Checkout)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (uiState.dynamicUpiBitmap != null) {
                        FilledTonalIconButton(
                            onClick = { showUpiQrModal = true },
                            modifier = Modifier.size(44.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.QrCode2, contentDescription = "UPI QR", tint = BrandEmeraldDark)
                        }
                    }

                    // Complete Checkout Button - Weighted / Constrained, NEVER unconstrained!
                    Button(
                        onClick = {
                            if (uiState.paymentMode == PaymentMode.CASH) {
                                showCashTenderModal = true
                            } else {
                                viewModel.processCheckout(context)
                            }
                        },
                        enabled = uiState.cartItems.isNotEmpty() && !uiState.isProcessingCheckout,
                        colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(44.dp)
                    ) {
                        if (uiState.isProcessingCheckout) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp))
                        } else {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Complete Checkout", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }

    // ==================== MODALS & DIALOGS ====================

    // 1. Cash Tender Change Calculator Dialog
    if (showCashTenderModal) {
        CashTenderCalculatorDialog(
            grandTotal = uiState.totals.grandTotal,
            tenderedAmount = uiState.tenderedCashAmount,
            onTenderChanged = { viewModel.setTenderedCash(it) },
            onConfirmCheckout = {
                showCashTenderModal = false
                viewModel.processCheckout(context)
            },
            onDismiss = { showCashTenderModal = false }
        )
    }

    // 2. Dynamic NPCI UPI QR Pop-up Dialog
    if (showUpiQrModal && uiState.dynamicUpiBitmap != null) {
        AlertDialog(
            onDismissRequest = { showUpiQrModal = false },
            title = { Text("NPCI Dynamic UPI QR", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Pay Exactly: ₹${String.format(Locale.US, "%.2f", uiState.totals.grandTotal)}",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp,
                        color = PrimaryNavy
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Image(
                        bitmap = uiState.dynamicUpiBitmap!!.asImageBitmap(),
                        contentDescription = "Dynamic UPI QR",
                        modifier = Modifier.size(240.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Supports BHIM, Google Pay, PhonePe, Paytm", fontSize = 12.sp, color = Slate600)
                }
            },
            confirmButton = {
                Button(onClick = { showUpiQrModal = false }) {
                    Text("Done")
                }
            }
        )
    }

    // 3. Bill Receipt Success Dialog with "Print Thermal" and "Share PDF"
    uiState.completedTransaction?.let { tx ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissSuccess() },
            icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = BrandEmerald, modifier = Modifier.size(36.dp)) },
            title = { Text("Sale Completed Successfully!", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Invoice No: #${tx.invoiceNo}", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text("Total Paid: ₹${String.format(Locale.US, "%.2f", tx.grandTotal)} via ${tx.paymentMode}", color = BrandEmeraldDark, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Printable PDF receipt saved to local offline storage.",
                        fontSize = 12.sp,
                        color = Slate600
                    )
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Share PDF Option
                    uiState.lastGeneratedInvoiceFile?.let { pdfFile ->
                        OutlinedButton(
                            onClick = {
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", pdfFile)
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/pdf"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Invoice Receipt"))
                            },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Share PDF", fontSize = 12.sp)
                        }
                    }

                    // Print / Dismiss Button
                    Button(
                        onClick = { viewModel.dismissSuccess() },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryNavy),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Print / Done", fontSize = 12.sp)
                    }
                }
            }
        )
    }

    // 4. Add / Edit Product Modal (Direct Room SQLCipher DB Persistence)
    if (showAddProductModal) {
        AddEditProductDialog(
            initialProduct = editingProduct,
            onSave = { product ->
                viewModel.saveOrUpdateProduct(product)
                showAddProductModal = false
            },
            onDismiss = { showAddProductModal = false }
        )
    }

    // 5. Decimal Quantity Edit Modal (for fractional scale weights e.g. 1.450 kg)
    decimalQtyEditItem?.let { item ->
        var input by remember { mutableStateOf(item.quantity.toString()) }
        AlertDialog(
            onDismissRequest = { decimalQtyEditItem = null },
            title = { Text("Set Quantity (${item.product.unitType.displayName})") },
            text = {
                Column {
                    Text(item.product.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        label = { Text("Decimal Quantity / Weight") }
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    input.toDoubleOrNull()?.let { qty ->
                        viewModel.updateItemQuantity(item.cartItemId, qty)
                    }
                    decimalQtyEditItem = null
                }) {
                    Text("Apply")
                }
            },
            dismissButton = {
                TextButton(onClick = { decimalQtyEditItem = null }) { Text("Cancel") }
            }
        )
    }

    // 6. Select Khata Customer Dialog
    if (showCustomerSelectDialog) {
        AlertDialog(
            onDismissRequest = { showCustomerSelectDialog = false },
            title = { Text("Select Khata Customer") },
            text = {
                LazyColumn(modifier = Modifier.height(240.dp)) {
                    items(khataCustomers) { customer ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.selectKhataCustomer(customer)
                                    showCustomerSelectDialog = false
                                }
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(customer.name, fontWeight = FontWeight.Bold)
                                Text("Ph: ${customer.phone}", fontSize = 12.sp, color = Slate600)
                            }
                            Text("Due: ₹${customer.currentBalance}", color = ErrorCrimson, fontWeight = FontWeight.Bold)
                        }
                        HorizontalDivider(color = Slate100)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCustomerSelectDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Error Alert
    uiState.errorMessage?.let { error ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissError() },
            title = { Text("Notice", color = ErrorCrimson, fontWeight = FontWeight.Bold) },
            text = { Text(error) },
            confirmButton = {
                Button(onClick = { viewModel.dismissError() }) { Text("OK") }
            }
        )
    }
}

// ==================== SUB-COMPOSABLES ====================

@Composable
fun ProductRetailCard(
    product: ProductEntity,
    canViewCost: Boolean,
    onAddClick: () -> Unit,
    onEditClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onAddClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Tag: Category & Unit
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = Slate100,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        product.category.uppercase(),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate600,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }

                IconButton(
                    onClick = onEditClick,
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Slate400, modifier = Modifier.size(14.dp))
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Product Name
            Text(
                product.name,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = PrimaryNavy
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Retail & Wholesale Rate
            Text(
                "₹${String.format(Locale.US, "%.2f", product.retailPrice)} / ${product.unitType.displayName}",
                fontWeight = FontWeight.Black,
                fontSize = 15.sp,
                color = PrimaryNavy
            )

            if (product.wholesalePrice > 0.0) {
                Text(
                    "Bulk: ₹${product.wholesalePrice} (≥${product.wholesaleMinQty})",
                    fontSize = 10.sp,
                    color = BrandEmeraldDark,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Footer: Stock Pill and Add Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = if (product.stockQty <= product.minStockWarning) ErrorCrimsonLight else Slate100,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        "${String.format(Locale.US, "%.1f", product.stockQty)} ${product.unitType.displayName}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (product.stockQty <= product.minStockWarning) ErrorCrimson else Slate700,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                FilledTonalIconButton(
                    onClick = onAddClick,
                    modifier = Modifier.size(32.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = BrandEmeraldLight,
                        contentColor = BrandEmeraldDark
                    )
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add", modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
fun CartLineItemRow(
    item: CartItem,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onQuantityClick: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(item.product.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = PrimaryNavy)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "₹${String.format(Locale.US, "%.2f", item.appliedUnitPrice)} / ${item.product.unitType.displayName}",
                    fontSize = 11.sp,
                    color = Slate600
                )
                if (item.isWholesaleApplied) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Surface(color = BrandEmeraldLight, shape = RoundedCornerShape(4.dp)) {
                        Text("WHOLESALE", color = BrandEmeraldDark, fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 3.dp))
                    }
                }
            }
        }

        // Steppers
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDecrement, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Remove, contentDescription = "Minus", modifier = Modifier.size(14.dp))
            }

            Surface(
                modifier = Modifier
                    .clickable(onClick = onQuantityClick)
                    .padding(horizontal = 4.dp),
                shape = RoundedCornerShape(4.dp),
                color = Slate100
            ) {
                Text(
                    String.format(Locale.US, "%.2f", item.quantity),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            IconButton(onClick = onIncrement, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Add, contentDescription = "Plus", modifier = Modifier.size(14.dp))
            }

            Spacer(modifier = Modifier.width(6.dp))

            Text(
                "₹${String.format(Locale.US, "%.2f", item.lineTotal)}",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = PrimaryNavy
            )

            IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = ErrorCrimson, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
fun CashTenderCalculatorDialog(
    grandTotal: Double,
    tenderedAmount: Double,
    onTenderChanged: (Double) -> Unit,
    onConfirmCheckout: () -> Unit,
    onDismiss: () -> Unit
) {
    var input by remember { mutableStateOf(if (tenderedAmount > 0.0) tenderedAmount.toString() else "") }
    val tendered = input.toDoubleOrNull() ?: 0.0
    val changeDue = maxOf(0.0, tendered - grandTotal)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cash Tender & Change", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Total Payable: ₹${String.format(Locale.US, "%.2f", grandTotal)}", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = PrimaryNavy)

                OutlinedTextField(
                    value = input,
                    onValueChange = {
                        input = it
                        it.toDoubleOrNull()?.let { amt -> onTenderChanged(amt) }
                    },
                    label = { Text("Cash Received from Customer (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Quick Cash Denomination Chips
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(100.0, 200.0, 500.0).forEach { note ->
                        AssistChip(
                            onClick = {
                                val newAmt = (tendered + note).toString()
                                input = newAmt
                                onTenderChanged(tendered + note)
                            },
                            label = { Text("+₹${note.toInt()}") }
                        )
                    }
                    AssistChip(
                        onClick = {
                            input = grandTotal.toString()
                            onTenderChanged(grandTotal)
                        },
                        label = { Text("Exact") }
                    )
                }

                Surface(
                    color = if (changeDue > 0.0) BrandEmeraldLight else Slate100,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Change to Return to Customer:", fontSize = 12.sp, color = Slate600)
                        Text(
                            "₹${String.format(Locale.US, "%.2f", changeDue)}",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (changeDue > 0.0) BrandEmeraldDark else Slate700
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmCheckout,
                enabled = tendered >= grandTotal || tendered == 0.0,
                colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald)
            ) {
                Text("Finalize & Print Invoice")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun AddEditProductDialog(
    initialProduct: ProductEntity?,
    onSave: (ProductEntity) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialProduct?.name ?: "") }
    var barcode by remember { mutableStateOf(initialProduct?.barcode ?: "") }
    var sku by remember { mutableStateOf(initialProduct?.sku ?: "") }
    var category by remember { mutableStateOf(initialProduct?.category ?: "General") }
    var retailPrice by remember { mutableStateOf(initialProduct?.retailPrice?.toString() ?: "") }
    var wholesalePrice by remember { mutableStateOf(initialProduct?.wholesalePrice?.toString() ?: "0.0") }
    var wholesaleMinQty by remember { mutableStateOf(initialProduct?.wholesaleMinQty?.toString() ?: "5.0") }
    var stockQty by remember { mutableStateOf(initialProduct?.stockQty?.toString() ?: "50.0") }
    var selectedUnit by remember { mutableStateOf(initialProduct?.unitType ?: UnitType.PIECE) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialProduct == null) "Add New Product" else "Edit Product", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Product Name") }, singleLine = true)
                OutlinedTextField(value = barcode, onValueChange = { barcode = it }, label = { Text("Barcode") }, singleLine = true)
                OutlinedTextField(value = sku, onValueChange = { sku = it }, label = { Text("SKU / Code") }, singleLine = true)
                OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("Category") }, singleLine = true)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = retailPrice,
                        onValueChange = { retailPrice = it },
                        label = { Text("Retail Price (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = stockQty,
                        onValueChange = { stockQty = it },
                        label = { Text("Stock Qty") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = wholesalePrice,
                        onValueChange = { wholesalePrice = it },
                        label = { Text("Wholesale Rate (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = wholesaleMinQty,
                        onValueChange = { wholesaleMinQty = it },
                        label = { Text("Min Wholesale Qty") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                // Unit Type Selector
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    UnitType.values().forEach { unit ->
                        FilterChip(
                            selected = (selectedUnit == unit),
                            onClick = { selectedUnit = unit },
                            label = { Text(unit.displayName) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val ret = retailPrice.toDoubleOrNull() ?: 0.0
                    val ws = wholesalePrice.toDoubleOrNull() ?: 0.0
                    val wsQty = wholesaleMinQty.toDoubleOrNull() ?: 5.0
                    val st = stockQty.toDoubleOrNull() ?: 0.0

                    if (name.isNotBlank() && barcode.isNotBlank() && ret > 0.0) {
                        val productToSave = (initialProduct ?: ProductEntity(
                            barcode = barcode.trim(),
                            sku = if (sku.isBlank()) "SKU-${System.currentTimeMillis() % 10000}" else sku.trim(),
                            name = name.trim(),
                            category = category.trim(),
                            unitType = selectedUnit,
                            costPrice = ret * 0.75, // Default snapshot cost
                            retailPrice = ret,
                            wholesalePrice = ws,
                            wholesaleMinQty = wsQty,
                            taxSlab = 0.0,
                            stockQty = st
                        )).copy(
                            name = name.trim(),
                            barcode = barcode.trim(),
                            sku = sku.ifBlank { "SKU-${System.currentTimeMillis() % 10000}" },
                            category = category.trim(),
                            retailPrice = ret,
                            wholesalePrice = ws,
                            wholesaleMinQty = wsQty,
                            stockQty = st,
                            unitType = selectedUnit
                        )

                        onSave(productToSave)
                    }
                }
            ) {
                Text("Save to Database")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
