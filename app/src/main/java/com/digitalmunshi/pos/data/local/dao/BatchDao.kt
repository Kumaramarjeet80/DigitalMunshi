package com.digitalmunshi.pos.data.local.dao

import androidx.room.*
import com.digitalmunshi.pos.data.local.entities.BatchEntity
import kotlinx.coroutines.flow.Flow

data class ExpiringBatchWithProduct(
    @Embedded val batch: BatchEntity,
    @ColumnInfo(name = "product_name") val productName: String,
    @ColumnInfo(name = "product_barcode") val barcode: String,
    @ColumnInfo(name = "product_category") val category: String
)

@Dao
interface BatchDao {

    @Query("SELECT * FROM batches WHERE id = :id LIMIT 1")
    suspend fun getBatchById(id: Long): BatchEntity?

    @Query("SELECT * FROM batches WHERE product_id = :productId AND stock_qty > 0 ORDER BY expiry_date ASC")
    suspend fun getAvailableBatchesFIFO(productId: Long): List<BatchEntity>

    @Query("SELECT * FROM batches WHERE product_id = :productId ORDER BY expiry_date ASC")
    fun getBatchesForProductFlow(productId: Long): Flow<List<BatchEntity>>

    /**
     * Expiry Radar query: retrieves all batches expiring between now and [thresholdTimestamp]
     * where remaining stock > 0, joining product information.
     */
    @Query("""
        SELECT b.*, p.name AS product_name, p.barcode AS product_barcode, p.category AS product_category
        FROM batches b
        INNER JOIN products p ON b.product_id = p.id
        WHERE b.expiry_date <= :thresholdTimestamp AND b.stock_qty > 0
        ORDER BY b.expiry_date ASC
    """)
    fun getExpiringBatchesRadarFlow(thresholdTimestamp: Long): Flow<List<ExpiringBatchWithProduct>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBatch(batch: BatchEntity): Long

    @Update
    suspend fun updateBatch(batch: BatchEntity)

    @Query("UPDATE batches SET stock_qty = stock_qty - :deltaQty WHERE id = :batchId AND stock_qty >= :deltaQty")
    suspend fun decrementBatchStock(batchId: Long, deltaQty: Double): Int

    @Query("UPDATE batches SET stock_qty = stock_qty + :deltaQty WHERE id = :batchId")
    suspend fun incrementBatchStock(batchId: Long, deltaQty: Double): Int

    @Delete
    suspend fun deleteBatch(batch: BatchEntity)
}
