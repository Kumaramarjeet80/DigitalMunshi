package com.digitalmunshi.pos.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.digitalmunshi.pos.domain.models.PaymentMode
import com.digitalmunshi.pos.domain.models.TransactionStatus

@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["invoice_no"], unique = true),
        Index(value = ["timestamp"]),
        Index(value = ["cashier_id"]),
        Index(value = ["payment_mode"]),
        Index(value = ["status"])
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "invoice_no")
    val invoiceNo: String,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "subtotal")
    val subtotal: Double,

    @ColumnInfo(name = "tax_total")
    val taxTotal: Double,

    @ColumnInfo(name = "discount_total")
    val discountTotal: Double = 0.0,

    @ColumnInfo(name = "grand_total")
    val grandTotal: Double,

    @ColumnInfo(name = "payment_mode")
    val paymentMode: PaymentMode,

    @ColumnInfo(name = "cashier_id")
    val cashierId: String,

    @ColumnInfo(name = "status")
    val status: TransactionStatus = TransactionStatus.COMPLETED,

    @ColumnInfo(name = "customer_id")
    val customerId: Long? = null, // Set if KHATA or SPLIT customer

    @ColumnInfo(name = "notes")
    val notes: String? = null
)
