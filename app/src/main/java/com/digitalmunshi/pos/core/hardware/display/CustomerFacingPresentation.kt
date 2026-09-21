package com.digitalmunshi.pos.core.hardware.display

import android.app.Presentation
import android.content.Context
import android.os.Bundle
import android.view.Display
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.digitalmunshi.pos.core.qr.DynamicUpiQrGenerator
import com.digitalmunshi.pos.domain.models.CartItem
import com.digitalmunshi.pos.domain.models.CartTotals
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

data class CustomerDisplayState(
    val items: List<CartItem> = emptyList(),
    val totals: CartTotals = CartTotals(0.0, 0.0, 0.0, 0.0, 0, 0.0),
    val storeName: String = "Digital Munshi POS",
    val upiQrContent: String? = null,
    val isCompleted: Boolean = false,
    val completedInvoiceNo: String? = null
)

class CustomerFacingPresentation(
    outerContext: Context,
    display: Display
) : Presentation(outerContext, display) {

    private val _displayState = MutableStateFlow(CustomerDisplayState())
    val displayState: StateFlow<CustomerDisplayState> = _displayState.asStateFlow()

    fun updateCart(items: List<CartItem>, totals: CartTotals, upiUri: String? = null) {
        _displayState.value = _displayState.value.copy(
            items = items,
            totals = totals,
            upiQrContent = upiUri,
            isCompleted = false,
            completedInvoiceNo = null
        )
    }

    fun showOrderCompleted(invoiceNo: String, grandTotal: Double) {
        _displayState.value = _displayState.value.copy(
            isCompleted = true,
            completedInvoiceNo = invoiceNo
        )
    }

    fun reset() {
        _displayState.value = CustomerDisplayState()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val composeView = ComposeView(context).apply {
            setContent {
                MaterialTheme {
                    val state by displayState.collectAsState()
                    CustomerDisplayScreen(state = state)
                }
            }
        }

        setContentView(composeView)
    }
}

@Composable
fun CustomerDisplayScreen(state: CustomerDisplayState) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF0F172A) // Sleek slate dark theme for customer-facing display
    ) {
        if (state.isCompleted) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.padding(32.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                ) {
                    Column(
                        modifier = Modifier.padding(48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Payment Received!",
                            color = Color(0xFF00D4B2),
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Invoice #${state.completedInvoiceNo ?: ""}",
                            color = Color.White,
                            fontSize = 22.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Thank you for shopping with us.",
                            color = Color.LightGray,
                            fontSize = 18.sp
                        )
                    }
                }
            }
        } else if (state.items.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = state.storeName,
                        color = Color.White,
                        fontSize = 42.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Welcome! Ready for your next order.",
                        color = Color(0xFF94A3B8),
                        fontSize = 20.sp
                    )
                }
            }
        } else {
            // Active Cart Layout: Left side line items, Right side Totals & Dynamic UPI QR
            Row(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                // Items Table
                Column(modifier = Modifier.weight(1.4f).fillMaxHeight()) {
                    Text(
                        text = "Current Order (${state.items.size} items)",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(state.items) { item ->
                            CustomerCartRow(item = item)
                            Divider(color = Color(0xFF334155), thickness = 0.5.dp)
                        }
                    }
                }

                Spacer(modifier = Modifier.width(24.dp))

                // Totals & Live UPI QR
                Column(
                    modifier = Modifier
                        .weight(1.0f)
                        .fillMaxHeight()
                        .background(Color(0xFF1E293B), shape = MaterialTheme.shapes.medium)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "BILL SUMMARY",
                            color = Color(0xFF94A3B8),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        SummaryRow("Subtotal", "₹${String.format(Locale.US, "%.2f", state.totals.subtotal)}")
                        if (state.totals.taxTotal > 0.0) {
                            SummaryRow("Taxes (GST)", "₹${String.format(Locale.US, "%.2f", state.totals.taxTotal)}")
                        }
                        if (state.totals.discountTotal > 0.0) {
                            SummaryRow("Discount", "-₹${String.format(Locale.US, "%.2f", state.totals.discountTotal)}", Color(0xFF00D4B2))
                        }

                        Divider(color = Color(0xFF475569), modifier = Modifier.padding(vertical = 12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Total Payable", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "₹${String.format(Locale.US, "%.2f", state.totals.grandTotal)}",
                                color = Color(0xFF00D4B2),
                                fontSize = 28.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }

                    // Dynamic UPI QR code right on the secondary customer screen!
                    state.upiQrContent?.let { qrUri ->
                        val qrBitmap = DynamicUpiQrGenerator.generateQrBitmap(qrUri, 280, 280)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(top = 16.dp)
                        ) {
                            Text("Scan & Pay with any UPI App", color = Color.LightGray, fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            androidx.compose.foundation.Image(
                                bitmap = qrBitmap.asImageBitmap(),
                                contentDescription = "Dynamic UPI QR Code",
                                modifier = Modifier.size(160.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomerCartRow(item: CartItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(item.product.name, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            Text(
                "${String.format(Locale.US, "%.3f", item.quantity)} ${item.product.unitType.displayName} @ ₹${item.appliedUnitPrice}",
                color = Color(0xFF94A3B8),
                fontSize = 14.sp
            )
        }
        Text(
            "₹${String.format(Locale.US, "%.2f", item.lineTotal)}",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SummaryRow(label: String, value: String, valueColor: Color = Color.White) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color(0xFF94A3B8), fontSize = 16.sp)
        Text(value, color = valueColor, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}
