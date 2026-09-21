package com.digitalmunshi.pos.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "batches",
    foreignKeys = [
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["product_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["product_id"]),
        Index(value = ["expiry_date"]),
        Index(value = ["product_id", "batch_no"], unique = true)
    ]
)
data class BatchEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "product_id")
    val productId: Long,

    @ColumnInfo(name = "batch_no")
    val batchNo: String,

    @ColumnInfo(name = "mfg_date")
    val mfgDate: Long, // Epoch timestamp millis

    @ColumnInfo(name = "expiry_date")
    val expiryDate: Long, // Epoch timestamp millis for Expiry Radar

    @ColumnInfo(name = "cost_price")
    val costPrice: Double,

    @ColumnInfo(name = "stock_qty")
    val stockQty: Double // Double for fractional stock
)
