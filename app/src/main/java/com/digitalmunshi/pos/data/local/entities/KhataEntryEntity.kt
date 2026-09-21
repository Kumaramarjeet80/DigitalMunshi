package com.digitalmunshi.pos.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.digitalmunshi.pos.domain.models.KhataEntryType

@Entity(
    tableName = "khata_entries",
    foreignKeys = [
        ForeignKey(
            entity = KhataCustomerEntity::class,
            parentColumns = ["id"],
            childColumns = ["customer_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["transaction_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["customer_id"]),
        Index(value = ["transaction_id"]),
        Index(value = ["timestamp"])
    ]
)
data class KhataEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "customer_id")
    val customerId: Long,

    @ColumnInfo(name = "transaction_id")
    val transactionId: Long?,

    @ColumnInfo(name = "type")
    val type: KhataEntryType, // DEBIT (credit sale) or CREDIT_PAYMENT (settlement)

    @ColumnInfo(name = "amount")
    val amount: Double,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "notes")
    val notes: String? = null
)
