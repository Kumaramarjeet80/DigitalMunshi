package com.digitalmunshi.pos.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "day_close_reports",
    indices = [
        Index(value = ["date"], unique = true)
    ]
)
data class DayCloseReportEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "date")
    val date: String, // YYYY-MM-DD

    @ColumnInfo(name = "opening_cash")
    val openingCash: Double,

    @ColumnInfo(name = "total_cash_sales")
    val totalCashSales: Double,

    @ColumnInfo(name = "total_upi_sales")
    val totalUpiSales: Double,

    @ColumnInfo(name = "total_credit_issued")
    val totalCreditIssued: Double, // Khata credit granted today

    @ColumnInfo(name = "petty_expenses")
    val pettyExpenses: Double,

    @ColumnInfo(name = "closing_cash_expected")
    val closingCashExpected: Double, // openingCash + totalCashSales - pettyExpenses

    @ColumnInfo(name = "closing_cash_actual")
    val closingCashActual: Double, // Actual counted cash in drawer

    @ColumnInfo(name = "cash_discrepancy")
    val cashDiscrepancy: Double, // closingCashActual - closingCashExpected

    @ColumnInfo(name = "closed_by_cashier_id")
    val closedByCashierId: String,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "notes")
    val notes: String? = null
)
