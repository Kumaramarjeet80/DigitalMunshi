package com.digitalmunshi.pos.data.local.dao

import androidx.room.*
import com.digitalmunshi.pos.data.local.entities.SupplierEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SupplierDao {

    @Query("SELECT * FROM suppliers WHERE id = :id LIMIT 1")
    suspend fun getSupplierById(id: Long): SupplierEntity?

    @Query("SELECT * FROM suppliers ORDER BY name ASC")
    fun getAllSuppliersFlow(): Flow<List<SupplierEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSupplier(supplier: SupplierEntity): Long

    @Update
    suspend fun updateSupplier(supplier: SupplierEntity)

    @Query("UPDATE suppliers SET balance_due = balance_due + :deltaAmount WHERE id = :supplierId")
    suspend fun updateSupplierBalance(supplierId: Long, deltaAmount: Double)

    @Delete
    suspend fun deleteSupplier(supplier: SupplierEntity)
}
