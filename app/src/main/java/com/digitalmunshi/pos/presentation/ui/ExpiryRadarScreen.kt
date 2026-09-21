package com.digitalmunshi.pos.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.digitalmunshi.pos.presentation.theme.*
import com.digitalmunshi.pos.presentation.viewmodel.ExpiryRadarItem
import com.digitalmunshi.pos.presentation.viewmodel.ExpiryRadarViewModel
import com.digitalmunshi.pos.presentation.viewmodel.ExpirySeverity
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpiryRadarScreen(
    viewModel: ExpiryRadarViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val dateFormat = SimpleDateFormat("dd-MMM-yyyy", Locale.US)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Inventory Expiry Radar", fontWeight = FontWeight.Bold) },
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
                .padding(16.dp)
        ) {
            // Summary Dashboard Metrics
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricCard(
                    title = "Expired Batches",
                    value = uiState.expiredCount.toString(),
                    badgeColor = AlertRed,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Critical (≤ 7 Days)",
                    value = uiState.criticalCount.toString(),
                    badgeColor = WarningOrange,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "At-Risk Stock Value",
                    value = "₹${String.format(Locale.US, "%.0f", uiState.totalAtRiskStockValue)}",
                    badgeColor = TealDark,
                    modifier = Modifier.weight(1.2f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Horizon Filter Chips
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Horizon:", fontWeight = FontWeight.SemiBold, color = Slate700)
                listOf(7, 15, 30, 60, 90).forEach { days ->
                    FilterChip(
                        selected = (uiState.thresholdDays == days),
                        onClick = { viewModel.setThresholdDays(days) },
                        label = { Text("$days Days") }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Batches List
            if (uiState.items.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No batches expiring within ${uiState.thresholdDays} days. All clean!", color = Slate400)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.items) { item ->
                        ExpiryBatchCard(item = item, dateFormat = dateFormat)
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricCard(title: String, value: String, badgeColor: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, fontSize = 12.sp, color = Slate700)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = badgeColor)
        }
    }
}

@Composable
private fun ExpiryBatchCard(item: ExpiryRadarItem, dateFormat: SimpleDateFormat) {
    val b = item.batchWithProduct.batch
    val (statusLabel, badgeColor) = when (item.severity) {
        ExpirySeverity.EXPIRED -> Pair("EXPIRED", AlertRed)
        ExpirySeverity.CRITICAL -> Pair("${item.daysRemaining}d Left", AlertRed)
        ExpirySeverity.WARNING -> Pair("${item.daysRemaining}d Left", WarningOrange)
        ExpirySeverity.UPCOMING -> Pair("${item.daysRemaining}d Left", TealDark)
    }

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
                Text(item.batchWithProduct.productName, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    "Batch #${b.batchNo} | Barcode: ${item.batchWithProduct.barcode}",
                    fontSize = 12.sp,
                    color = Slate700
                )
                Text(
                    "Expiry: ${dateFormat.format(Date(b.expiryDate))}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = badgeColor
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Surface(
                    color = badgeColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        statusLabel,
                        color = badgeColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Stock: ${b.stockQty}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Slate700
                )
            }
        }
    }
}
