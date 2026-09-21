package com.digitalmunshi.pos.data.local.dao

import androidx.room.*
import com.digitalmunshi.pos.data.local.entities.ProductEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {

    @Query("SELECT * FROM products WHERE id = :id LIMIT 1")
    suspend fun getProductById(id: Long): ProductEntity?

    @Query("SELECT * FROM products WHERE barcode = :barcode AND is_active = 1 LIMIT 1")
    suspend fun getProductByBarcode(barcode: String): ProductEntity?

    @Query("SELECT * FROM products WHERE sku = :sku AND is_active = 1 LIMIT 1")
    suspend fun getProductBySku(sku: String): ProductEntity?

    @Query("""
        SELECT * FROM products 
        WHERE is_active = 1 AND (
            name LIKE '%' || :query || '%' OR 
            barcode LIKE '%' || :query || '%' OR 
            category LIKE '%' || :query || '%'
        )
        ORDER BY name ASC
    """)
    fun searchProductsFlow(query: String): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE is_active = 1 ORDER BY name ASC")
    fun getAllProductsFlow(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE stock_qty <= min_stock_warning AND is_active = 1 ORDER BY stock_qty ASC")
    fun getLowStockProductsFlow(): Flow<List<ProductEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertProduct(product: ProductEntity): Long

    @Update
    suspend fun updateProduct(product: ProductEntity)

    @Query("UPDATE products SET stock_qty = stock_qty - :deltaQty, updated_at = :timestamp WHERE id = :productId")
    suspend fun decrementStock(productId: Long, deltaQty: Double, timestamp: Long = System.currentTimeMillis()): Int

    @Query("UPDATE products SET stock_qty = stock_qty + :deltaQty, updated_at = :timestamp WHERE id = :productId")
    suspend fun incrementStock(productId: Long, deltaQty: Double, timestamp: Long = System.currentTimeMillis()): Int

    @Query("UPDATE products SET is_active = 0 WHERE id = :productId")
    suspend fun softDeleteProduct(productId: Long)
}
