package com.digitalmunshi.pos.data.local.dao

import androidx.room.*
import com.digitalmunshi.pos.data.local.entities.KhataCustomerEntity
import com.digitalmunshi.pos.data.local.entities.KhataEntryEntity
import com.digitalmunshi.pos.domain.models.KhataEntryType
import kotlinx.coroutines.flow.Flow

data class CustomerWithEntries(
    @Embedded val customer: KhataCustomerEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "customer_id"
    )
    val entries: List<KhataEntryEntity>
)

@Dao
interface KhataDao {

    @Query("SELECT * FROM khata_customers WHERE id = :id LIMIT 1")
    suspend fun getCustomerById(id: Long): KhataCustomerEntity?

    @Query("SELECT * FROM khata_customers WHERE phone = :phone LIMIT 1")
    suspend fun getCustomerByPhone(phone: String): KhataCustomerEntity?

    @Query("SELECT * FROM khata_customers ORDER BY name ASC")
    fun getAllCustomersFlow(): Flow<List<KhataCustomerEntity>>

    @Query("SELECT * FROM khata_customers WHERE current_balance > 0 ORDER BY current_balance DESC")
    fun getCustomersWithPendingDuesFlow(): Flow<List<KhataCustomerEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCustomer(customer: KhataCustomerEntity): Long

    @Update
    suspend fun updateCustomer(customer: KhataCustomerEntity)

    @Query("UPDATE khata_customers SET current_balance = current_balance + :deltaAmount WHERE id = :customerId")
    suspend fun updateCustomerBalance(customerId: Long, deltaAmount: Double)

    @Insert
    suspend fun insertEntry(entry: KhataEntryEntity): Long

    @Query("SELECT * FROM khata_entries WHERE customer_id = :customerId ORDER BY timestamp DESC")
    fun getEntriesForCustomerFlow(customerId: Long): Flow<List<KhataEntryEntity>>

    @Transaction
    @Query("SELECT * FROM khata_customers WHERE id = :id")
    suspend fun getCustomerWithEntries(id: Long): CustomerWithEntries?

    /**
     * Record a settlement payment from customer
     */
    @Transaction
    suspend fun recordCustomerPayment(
        customerId: Long,
        paymentAmount: Double,
        notes: String? = "Cash/UPI Settlement"
    ) {
        require(paymentAmount > 0) { "Payment amount must be positive." }
        updateCustomerBalance(customerId, -paymentAmount)
        insertEntry(
            KhataEntryEntity(
                customerId = customerId,
                transactionId = null,
                type = KhataEntryType.CREDIT_PAYMENT,
                amount = paymentAmount,
                timestamp = System.currentTimeMillis(),
                notes = notes
            )
        )
    }
}
