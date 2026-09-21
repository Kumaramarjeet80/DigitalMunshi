package com.digitalmunshi.pos.presentation.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.digitalmunshi.pos.core.security.SessionManager
import com.digitalmunshi.pos.data.local.entities.KhataCustomerEntity
import com.digitalmunshi.pos.data.local.entities.ProductEntity
import com.digitalmunshi.pos.domain.models.CartItem
import com.digitalmunshi.pos.domain.models.PaymentMode
import com.digitalmunshi.pos.domain.models.UserRole
import com.digitalmunshi.pos.presentation.theme.*
import com.digitalmunshi.pos.presentation.viewmodel.PosCartUiState
import com.digitalmunshi.pos.presentation.viewmodel.PosCartViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PosScreen(
    viewModel: PosCartViewModel,
    availableProducts: List<ProductEntity>,
    khataCustomers: List<KhataCustomerEntity>,
    onOpenExpiryRadar: () -> Unit,
    onOpenKhata: () -> Unit,
    onOpenBackup: () -> Unit,
    onSwitchUser: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var showUpiModal by remember { mutableStateOf(false) }
    var showDecimalQuantityDialogFor by remember { mutableStateOf<CartItem?>(null) }
    var showCustomerSelectDialog by remember { mutableStateOf(false) }

    val filteredProducts = remember(searchQuery, availableProducts) {
        if (searchQuery.isBlank()) availableProducts
        else availableProducts.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
            it.barcode.contains(searchQuery, ignoreCase = true) ||
            it.category.contains(searchQuery, ignoreCase = true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Digital Munshi POS", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text(
                            "100% Offline Air-Gapped Billing",
                            style = MaterialTheme.typography.bodySmall,
                            color = Slate400
                        )
                    }
                },
                actions = {
                    // Active User Role Badge
                    AssistChip(
                        onClick = onSwitchUser,
                        label = {
                            Text(
                                if (SessionManager.currentRole == UserRole.ADMIN) "ADMIN (Unlocked)" else "CASHIER (Locked)",
                                fontWeight = FontWeight.Bold
                            )
                        },
                        leadingIcon = {
                            Icon(
                                if (SessionManager.currentRole == UserRole.ADMIN) Icons.Default.AdminPanelSettings else Icons.Default.Lock,
                                contentDescription = null,
                                tint = if (SessionManager.currentRole == UserRole.ADMIN) SuccessGreen else WarningOrange
                            )
                        }
                    )

                    IconButton(onClick = onOpenExpiryRadar) {
                        Icon(Icons.Default.HourglassBottom, contentDescription = "Expiry Radar", tint = WarningOrange)
                    }
                    IconButton(onClick = onOpenKhata) {
                        Icon(Icons.Default.AccountBalanceWallet, contentDescription = "Khata Ledgers")
                    }
                    IconButton(onClick = onOpenBackup) {
                        Icon(Icons.Default.Security, contentDescription = "Local Encrypted Backup")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Navy900,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        }
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Slate100)
        ) {
            // LEFT PANEL: Product Catalog & Quick Search Bar
            Column(
                modifier = Modifier
                    .weight(1.2f)
                    .fillMaxHeight()
                    .padding(12.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Scan barcode or search name / SKU...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Catalog (${filteredProducts.size} items)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Slate700
                )

                Spacer(modifier = Modifier.height(8.dp))

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredProducts) { product ->
                        ProductCatalogCard(
                            product = product,
                            canViewCost = uiState.canViewCostPrices,
                            onClick = { viewModel.onProductSelected(product) }
                        )
                    }
                }
            }

            // RIGHT PANEL: Active Cart & Billing Engine
            Card(
                modifier = Modifier
                    .weight(1.0f)
                    .fillMaxHeight()
                    .padding(end = 12.dp, top = 12.dp, bottom = 12.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
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
                                "Cart (${uiState.items.size} items)",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Navy900
                            )
                            if (uiState.items.isNotEmpty()) {
                                TextButton(onClick = { viewModel.clearCart() }) {
                                    Text("Clear All", color = AlertRed)
                                }
                            }
                        }

                        Divider(modifier = Modifier.padding(vertical = 8.dp), color = Slate200)

                        // Cart Line Items List
                        if (uiState.items.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = Slate400, modifier = Modifier.size(64.dp))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Cart is currently empty", color = Slate400)
                                    Text("Scan items or select from catalog", color = Slate400, fontSize = 12.sp)
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                            ) {
                                items(uiState.items, key = { it.cartItemId }) { item ->
                                    CartItemRow(
                                        item = item,
                                        onIncrement = { viewModel.updateItemQuantity(item.cartItemId, item.quantity + 1.0) },
                                        onDecrement = { viewModel.updateItemQuantity(item.cartItemId, item.quantity - 1.0) },
                                        onCustomQuantityClick = { showDecimalQuantityDialogFor = item },
                                        onDelete = { viewModel.removeItem(item.cartItemId) }
                                    )
                                    Divider(color = Slate200, thickness = 0.5.dp)
                                }
                            }
                        }
                    }

                    // BOTTOM CHECKOUT & TOTALS CONTROLS
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Divider(color = Slate200, thickness = 1.dp)
                        Spacer(modifier = Modifier.height(8.dp))

                        // Payment Mode Selector
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            PaymentMode.values().forEach { mode ->
                                FilterChip(
                                    selected = (uiState.paymentMode == mode),
                                    onClick = { viewModel.setPaymentMode(mode) },
                                    label = { Text(mode.name, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        // Khata Customer Selector if KHATA or SPLIT selected
                        if (uiState.paymentMode == PaymentMode.KHATA || uiState.paymentMode == PaymentMode.SPLIT) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showCustomerSelectDialog = true },
                                shape = RoundedCornerShape(8.dp),
                                color = Slate100
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("Khata Customer:", style = MaterialTheme.typography.bodySmall, color = Slate700)
                                        Text(
                                            uiState.selectedCustomer?.let { "${it.name} (Bal: ₹${it.currentBalance} / Max: ₹${it.maxCreditLimit})" }
                                                ?: "Tap to select customer...",
                                            fontWeight = FontWeight.Bold,
                                            color = if (uiState.selectedCustomer == null) AlertRed else Navy900
                                        )
                                    }
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Totals Summary
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Subtotal", color = Slate700)
                            Text("₹${String.format(Locale.US, "%.2f", uiState.totals.subtotal)}", fontWeight = FontWeight.SemiBold)
                        }
                        if (uiState.totals.taxTotal > 0.0) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Taxes (GST)", color = Slate700)
                                Text("₹${String.format(Locale.US, "%.2f", uiState.totals.taxTotal)}", fontWeight = FontWeight.SemiBold)
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Grand Total", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "₹${String.format(Locale.US, "%.2f", uiState.totals.grandTotal)}",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = Navy900
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Dynamic UPI QR Modal Button
                            if (uiState.dynamicUpiBitmap != null) {
                                OutlinedButton(
                                    onClick = { showUpiModal = true },
                                    modifier = Modifier.weight(0.4f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.QrCode, contentDescription = null)
                                }
                            }

                            // Checkout Button
                            Button(
                                onClick = { viewModel.processCheckout() },
                                modifier = Modifier.weight(1f),
                                enabled = uiState.items.isNotEmpty() && !uiState.isProcessingCheckout,
                                colors = ButtonDefaults.buttonColors(containerColor = Navy900),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                if (uiState.isProcessingCheckout) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                                } else {
                                    Text("CHECKOUT (Print & Kick)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal: Dynamic UPI QR Code Dialog
    if (showUpiModal && uiState.dynamicUpiBitmap != null) {
        AlertDialog(
            onDismissRequest = { showUpiModal = false },
            title = { Text("NPCI Dynamic UPI QR", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Exact Bill Amount: ₹${String.format(Locale.US, "%.2f", uiState.totals.grandTotal)}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Navy900
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Image(
                        bitmap = uiState.dynamicUpiBitmap!!.asImageBitmap(),
                        contentDescription = "Dynamic UPI QR",
                        modifier = Modifier.size(240.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Scan with Google Pay, PhonePe, Paytm, BHIM", fontSize = 12.sp, color = Slate700)
                }
            },
            confirmButton = {
                Button(onClick = { showUpiModal = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Modal: Decimal Quantity Input (e.g. 1.450 kg)
    showDecimalQuantityDialogFor?.let { item ->
        var qtyInput by remember { mutableStateOf(item.quantity.toString()) }
        AlertDialog(
            onDismissRequest = { showDecimalQuantityDialogFor = null },
            title = { Text("Set Quantity (${item.product.unitType.displayName})") },
            text = {
                Column {
                    Text(item.product.name, fontWeight = FontWeight.Bold)
                    Text("Enter decimal weight/quantity (e.g. 1.450):", fontSize = 13.sp, color = Slate700)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = qtyInput,
                        onValueChange = { qtyInput = it },
                        singleLine = true
                    )
                    if (item.product.wholesalePrice > 0.0) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Tip: Wholesale rate (₹${item.product.wholesalePrice}) applies automatically at ${item.product.wholesaleMinQty} ${item.product.unitType.displayName}!",
                            fontSize = 12.sp,
                            color = TealDark
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val parsed = qtyInput.toDoubleOrNull()
                    if (parsed != null && parsed > 0.0) {
                        viewModel.updateItemQuantity(item.cartItemId, parsed)
                    }
                    showDecimalQuantityDialogFor = null
                }) {
                    Text("Apply")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDecimalQuantityDialogFor = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Modal: Select Khata Customer
    if (showCustomerSelectDialog) {
        AlertDialog(
            onDismissRequest = { showCustomerSelectDialog = false },
            title = { Text("Select Khata Customer") },
            text = {
                LazyColumn(modifier = Modifier.height(260.dp)) {
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
                                Text("Ph: ${customer.phone}", fontSize = 12.sp, color = Slate700)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Bal: ₹${customer.currentBalance}", color = AlertRed, fontWeight = FontWeight.SemiBold)
                                Text("Limit: ₹${customer.maxCreditLimit}", fontSize = 11.sp, color = Slate400)
                            }
                        }
                        Divider(color = Slate200)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCustomerSelectDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Error Snackbar / Dialog
    uiState.errorMessage?.let { error ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissError() },
            title = { Text("Notice", color = AlertRed) },
            text = { Text(error) },
            confirmButton = {
                Button(onClick = { viewModel.dismissError() }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
fun ProductCatalogCard(
    product: ProductEntity,
    canViewCost: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                product.name,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "SKU: ${product.sku} | ${product.category}",
                fontSize = 11.sp,
                color = Slate400
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "₹${String.format(Locale.US, "%.2f", product.retailPrice)} / ${product.unitType.displayName}",
                        fontWeight = FontWeight.ExtraBold,
                        color = Navy900,
                        fontSize = 14.sp
                    )
                    if (product.wholesalePrice > 0.0) {
                        Text(
                            "Bulk: ₹${product.wholesalePrice} (≥${product.wholesaleMinQty})",
                            fontSize = 10.sp,
                            color = TealDark,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Text(
                    "${String.format(Locale.US, "%.1f", product.stockQty)} ${product.unitType.displayName}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (product.stockQty <= product.minStockWarning) AlertRed else Slate700
                )
            }

            // Cashier Lock: Cost price is only shown if admin is logged in!
            if (canViewCost) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Cost: ₹${product.costPrice}",
                    fontSize = 10.sp,
                    color = Slate400
                )
            }
        }
    }
}

@Composable
fun CartItemRow(
    item: CartItem,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onCustomQuantityClick: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(item.product.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "₹${String.format(Locale.US, "%.2f", item.appliedUnitPrice)} / ${item.product.unitType.displayName}",
                    fontSize = 12.sp,
                    color = Slate700
                )
                // Dynamic Wholesale Tier Indicator Badge
                if (item.isWholesaleApplied) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = Color(0xFFE6FFFA),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            "WHOLESALE TIER",
                            color = TealDark,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }

        // Fractional Quantity Controls
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDecrement, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Remove, contentDescription = "Decrement", modifier = Modifier.size(16.dp))
            }

            Surface(
                modifier = Modifier
                    .clickable(onClick = onCustomQuantityClick)
                    .padding(horizontal = 6.dp),
                shape = RoundedCornerShape(6.dp),
                color = Slate100
            ) {
                Text(
                    text = String.format(Locale.US, "%.3f", item.quantity),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            IconButton(onClick = onIncrement, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Add, contentDescription = "Increment", modifier = Modifier.size(16.dp))
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                "₹${String.format(Locale.US, "%.2f", item.lineTotal)}",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 14.sp,
                color = Navy900
            )

            IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = AlertRed, modifier = Modifier.size(16.dp))
            }
        }
    }
}
