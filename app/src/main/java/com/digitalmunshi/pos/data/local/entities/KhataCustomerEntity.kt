package com.digitalmunshi.pos.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "khata_customers",
    indices = [
        Index(value = ["phone"], unique = true),
        Index(value = ["name"])
    ]
)
data class KhataCustomerEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "phone")
    val phone: String,

    @ColumnInfo(name = "max_credit_limit")
    val maxCreditLimit: Double,

    @ColumnInfo(name = "current_balance")
    val currentBalance: Double = 0.0, // Positive indicates debt owed by customer

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
