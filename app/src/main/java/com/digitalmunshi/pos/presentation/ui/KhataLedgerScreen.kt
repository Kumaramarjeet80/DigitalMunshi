package com.digitalmunshi.pos.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.digitalmunshi.pos.data.local.entities.KhataCustomerEntity
import com.digitalmunshi.pos.presentation.theme.*
import com.digitalmunshi.pos.presentation.viewmodel.KhataViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KhataLedgerScreen(
    viewModel: KhataViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showAddCustomerDialog by remember { mutableStateOf(false) }
    var settlementCustomer by remember { mutableStateOf<KhataCustomerEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Khata & Debt Ledgers", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddCustomerDialog = true }) {
                        Icon(Icons.Default.PersonAdd, contentDescription = "Add Customer")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Navy900,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Slate100)
                .padding(16.dp)
        ) {
            // Summary Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Total Outstanding Debt Owed", fontSize = 13.sp, color = Slate700)
                        Text(
                            "₹${String.format(Locale.US, "%.2f", uiState.totalOutstandingDebt)}",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = AlertRed
                        )
                    }
                    Text(
                        "${uiState.customersWithDues.size} Borrowers",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Slate700
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "Customer Debt Register",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Slate900
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (uiState.allCustomers.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No Khata customers registered yet.", color = Slate400)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.allCustomers) { customer ->
                        CustomerKhataRow(
                            customer = customer,
                            onSendReminder = {
                                val reminder = viewModel.generateReminder(context, customer)
                                // Launch WhatsApp or SMS intent offline
                                runCatching { context.startActivity(reminder.whatsAppIntent) }
                                    .onFailure {
                                        context.startActivity(reminder.smsIntent)
                                    }
                            },
                            onRecordSettlement = { settlementCustomer = customer }
                        )
                    }
                }
            }
        }
    }

    // Modal: Record Settlement Payment
    settlementCustomer?.let { customer ->
        var paymentInput by remember { mutableStateOf(customer.currentBalance.toString()) }
        AlertDialog(
            onDismissRequest = { settlementCustomer = null },
            title = { Text("Record Repayment: ${customer.name}") },
            text = {
                Column {
                    Text("Current Due: ₹${customer.currentBalance}", color = AlertRed, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = paymentInput,
                        onValueChange = { paymentInput = it },
                        label = { Text("Payment Received (₹)") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val amt = paymentInput.toDoubleOrNull() ?: 0.0
                    if (amt > 0) {
                        viewModel.recordSettlement(customer.id, amt)
                    }
                    settlementCustomer = null
                }) {
                    Text("Record Payment")
                }
            },
            dismissButton = {
                TextButton(onClick = { settlementCustomer = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Modal: Add Customer
    if (showAddCustomerDialog) {
        var name by remember { mutableStateOf("") }
        var phone by remember { mutableStateOf("") }
        var limit by remember { mutableStateOf("5000") }

        AlertDialog(
            onDismissRequest = { showAddCustomerDialog = false },
            title = { Text("Add Khata Customer") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Customer Name") })
                    OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone Number") })
                    OutlinedTextField(value = limit, onValueChange = { limit = it }, label = { Text("Max Credit Limit (₹)") })
                }
            },
            confirmButton = {
                Button(onClick = {
                    val limitDbl = limit.toDoubleOrNull() ?: 5000.0
                    if (name.isNotBlank() && phone.isNotBlank()) {
                        viewModel.addNewCustomer(name, phone, limitDbl)
                    }
                    showAddCustomerDialog = false
                }) {
                    Text("Save Customer")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddCustomerDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun CustomerKhataRow(
    customer: KhataCustomerEntity,
    onSendReminder: () -> Unit,
    onRecordSettlement: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(customer.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("Ph: ${customer.phone}", fontSize = 12.sp, color = Slate700)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    "Credit Limit: ₹${customer.maxCreditLimit}",
                    fontSize = 11.sp,
                    color = Slate400
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "₹${String.format(Locale.US, "%.2f", customer.currentBalance)}",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                    color = if (customer.currentBalance > 0) AlertRed else SuccessGreen
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (customer.currentBalance > 0) {
                        FilledTonalIconButton(onClick = onSendReminder, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Send, contentDescription = "Send Reminder", modifier = Modifier.size(16.dp))
                        }
                    }
                    FilledTonalButton(
                        onClick = onRecordSettlement,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Pay", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
