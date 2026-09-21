package com.digitalmunshi.pos.data.local.dao

import androidx.room.*
import com.digitalmunshi.pos.data.local.entities.DayCloseReportEntity
import kotlinx.coroutines.flow.Flow

data class DailyFinancialSummary(
    @ColumnInfo(name = "total_cash") val totalCash: Double?,
    @ColumnInfo(name = "total_upi") val totalUpi: Double?,
    @ColumnInfo(name = "total_credit") val totalCredit: Double?
)

@Dao
interface DayCloseDao {

    @Query("SELECT * FROM day_close_reports WHERE date = :date LIMIT 1")
    suspend fun getReportByDate(date: String): DayCloseReportEntity?

    @Query("SELECT * FROM day_close_reports ORDER BY timestamp DESC")
    fun getAllReportsFlow(): Flow<List<DayCloseReportEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateReport(report: DayCloseReportEntity): Long

    /**
     * Compute aggregated sales totals directly from transactions for a given date epoch range
     */
    @Query("""
        SELECT 
            SUM(CASE WHEN payment_mode = 'CASH' THEN grand_total ELSE 0.0 END) AS total_cash,
            SUM(CASE WHEN payment_mode = 'UPI' THEN grand_total ELSE 0.0 END) AS total_upi,
            SUM(CASE WHEN payment_mode = 'KHATA' THEN grand_total ELSE 0.0 END) AS total_credit
        FROM transactions 
        WHERE timestamp >= :startTimestamp AND timestamp <= :endTimestamp AND status = 'COMPLETED'
    """)
    suspend fun calculateDailyTotals(startTimestamp: Long, endTimestamp: Long): DailyFinancialSummary
}
