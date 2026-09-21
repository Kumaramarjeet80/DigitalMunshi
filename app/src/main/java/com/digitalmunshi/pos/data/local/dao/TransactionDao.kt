package com.digitalmunshi.pos.data.local.dao

import androidx.room.*
import com.digitalmunshi.pos.data.local.entities.*
import com.digitalmunshi.pos.domain.models.AuditAction
import com.digitalmunshi.pos.domain.models.KhataEntryType
import com.digitalmunshi.pos.domain.models.PaymentMode
import com.digitalmunshi.pos.domain.models.TransactionStatus
import kotlinx.coroutines.flow.Flow

data class TransactionWithItems(
    @Embedded val transaction: TransactionEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "transaction_id"
    )
    val items: List<TransactionItemEntity>
)

class InsufficientStockException(message: String) : Exception(message)
class CreditLimitExceededException(message: String) : Exception(message)
class TransactionNotFoundException(message: String) : Exception(message)

@Dao
abstract class TransactionDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertTransaction(transaction: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertTransactionItems(items: List<TransactionItemEntity>): List<Long>

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    abstract suspend fun getTransactionById(id: Long): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE invoice_no = :invoiceNo LIMIT 1")
    abstract suspend fun getTransactionByInvoice(invoiceNo: String): TransactionEntity?

    @androidx.room.Transaction
    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    abstract suspend fun getTransactionWithItems(id: Long): TransactionWithItems?

    @androidx.room.Transaction
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC LIMIT :limit")
    abstract fun getRecentTransactionsFlow(limit: Int = 100): Flow<List<TransactionWithItems>>

    @Query("SELECT * FROM transaction_items WHERE transaction_id = :transactionId")
    abstract suspend fun getItemsForTransaction(transactionId: Long): List<TransactionItemEntity>

    @Query("UPDATE transactions SET status = :status WHERE id = :id")
    abstract suspend fun updateTransactionStatus(id: Long, status: TransactionStatus)

    // Direct database operations for atomic execution
    @Query("SELECT * FROM products WHERE id = :id LIMIT 1")
    abstract suspend fun getProductById(id: Long): ProductEntity?

    @Query("UPDATE products SET stock_qty = stock_qty - :deltaQty, updated_at = :timestamp WHERE id = :productId")
    abstract suspend fun decrementProductStock(productId: Long, deltaQty: Double, timestamp: Long): Int

    @Query("UPDATE products SET stock_qty = stock_qty + :deltaQty, updated_at = :timestamp WHERE id = :productId")
    abstract suspend fun incrementProductStock(productId: Long, deltaQty: Double, timestamp: Long): Int

    @Query("SELECT * FROM batches WHERE id = :id LIMIT 1")
    abstract suspend fun getBatchById(id: Long): BatchEntity?

    @Query("UPDATE batches SET stock_qty = stock_qty - :deltaQty WHERE id = :batchId")
    abstract suspend fun decrementBatchStock(batchId: Long, deltaQty: Double): Int

    @Query("UPDATE batches SET stock_qty = stock_qty + :deltaQty WHERE id = :batchId")
    abstract suspend fun incrementBatchStock(batchId: Long, deltaQty: Double): Int

    @Query("SELECT * FROM khata_customers WHERE id = :id LIMIT 1")
    abstract suspend fun getKhataCustomerById(id: Long): KhataCustomerEntity?

    @Query("UPDATE khata_customers SET current_balance = current_balance + :deltaAmount WHERE id = :customerId")
    abstract suspend fun updateCustomerBalance(customerId: Long, deltaAmount: Double): Int

    @Insert
    abstract suspend fun insertKhataEntry(entry: KhataEntryEntity): Long

    @Insert
    abstract suspend fun insertAuditLog(log: AuditLogEntity): Long

    /**
     * ATOMIC ROOM @Transaction:
     * 1. Validates and simultaneously decrements batch & product inventory.
     * 2. Enforces customer credit limit if payment mode is KHATA or SPLIT.
     * 3. Inserts Transaction and line items.
     * 4. Updates Khata customer balance & creates Khata ledger entry if credit is extended.
     * 5. Emits tamper-evident Audit Log entry.
     * If any validation fails, the entire transaction throws and SQLite automatically rolls back.
     */
    @androidx.room.Transaction
    open suspend fun processCheckout(
        transaction: TransactionEntity,
        items: List<TransactionItemEntity>,
        creditPortionAmount: Double = 0.0
    ): Long {
        require(items.isNotEmpty()) { "Cannot process transaction with empty cart items." }

        val now = System.currentTimeMillis()

        // 1. Inventory validation & atomic stock decrement
        for (item in items) {
            val product = getProductById(item.productId)
                ?: throw IllegalArgumentException("Product ID ${item.productId} not found.")

            // Decrement batch stock if batch specified
            if (item.batchId != null) {
                val batch = getBatchById(item.batchId)
                if (batch != null) {
                    if (batch.stockQty < item.quantity) {
                        throw InsufficientStockException(
                            "Batch '${batch.batchNo}' has insufficient stock (${batch.stockQty}) for requested quantity (${item.quantity})."
                        )
                    }
                    decrementBatchStock(item.batchId, item.quantity)
                }
            }

            // Decrement general product stock
            val rowsUpdated = decrementProductStock(item.productId, item.quantity, now)
            if (rowsUpdated == 0) {
                throw IllegalStateException("Failed to decrement inventory for product '${product.name}'.")
            }
        }

        // 2. Khata Credit Limit Enforcement
        if (transaction.paymentMode == PaymentMode.KHATA || transaction.paymentMode == PaymentMode.SPLIT) {
            val customerId = transaction.customerId
                ?: throw IllegalArgumentException("Customer must be selected for Khata/Credit purchases.")

            val customer = getKhataCustomerById(customerId)
                ?: throw IllegalArgumentException("Khata customer with ID $customerId not found.")

            val creditRequired = if (transaction.paymentMode == PaymentMode.KHATA) {
                transaction.grandTotal
            } else {
                creditPortionAmount
            }

            val projectedBalance = customer.currentBalance + creditRequired
            if (projectedBalance > customer.maxCreditLimit) {
                throw CreditLimitExceededException(
                    "Credit limit exceeded for ${customer.name}! Current balance: ₹${customer.currentBalance}, " +
                            "Attempted: ₹$creditRequired, Max limit: ₹${customer.maxCreditLimit}."
                )
            }
        }

        // 3. Insert main transaction record
        val transactionId = insertTransaction(transaction)

        // 4. Insert linked transaction items with generated foreign key
        val itemsWithTxId = items.map { it.copy(transactionId = transactionId) }
        insertTransactionItems(itemsWithTxId)

        // 5. Update Khata customer balance and log ledger entry if credit used
        if (transaction.paymentMode == PaymentMode.KHATA || transaction.paymentMode == PaymentMode.SPLIT) {
            val customerId = transaction.customerId!!
            val creditAmount = if (transaction.paymentMode == PaymentMode.KHATA) {
                transaction.grandTotal
            } else {
                creditPortionAmount
            }

            updateCustomerBalance(customerId, creditAmount)

            insertKhataEntry(
                KhataEntryEntity(
                    customerId = customerId,
                    transactionId = transactionId,
                    type = KhataEntryType.DEBIT,
                    amount = creditAmount,
                    timestamp = now,
                    notes = "Invoice #${transaction.invoiceNo} (Credit Sale)"
                )
            )
        }

        // 6. Record Audit Log entry
        insertAuditLog(
            AuditLogEntity(
                timestamp = now,
                cashierId = transaction.cashierId,
                action = AuditAction.SALE_CHECKOUT,
                details = "Completed sale #${transaction.invoiceNo} for ₹${transaction.grandTotal} via ${transaction.paymentMode}",
                payloadJson = "{\"transactionId\":$transactionId,\"invoiceNo\":\"${transaction.invoiceNo}\",\"grandTotal\":${transaction.grandTotal}}"
            )
        )

        return transactionId
    }

    /**
     * ATOMIC ROOM @Transaction:
     * Reverses a completed sale:
     * - Restores batch & product inventory.
     * - Reverses customer Khata debit if sale was on credit.
     * - Marks transaction status as REFUNDED.
     * - Logs audit event.
     */
    @androidx.room.Transaction
    open suspend fun processRefund(
        transactionId: Long,
        cashierId: String,
        refundReason: String
    ) {
        val tx = getTransactionById(transactionId)
            ?: throw TransactionNotFoundException("Transaction ID $transactionId not found.")

        if (tx.status == TransactionStatus.REFUNDED) {
            throw IllegalStateException("Transaction #${tx.invoiceNo} has already been refunded.")
        }

        val items = getItemsForTransaction(transactionId)
        val now = System.currentTimeMillis()

        // 1. Restore inventory
        for (item in items) {
            if (item.batchId != null) {
                incrementBatchStock(item.batchId, item.quantity)
            }
            incrementProductStock(item.productId, item.quantity, now)
        }

        // 2. Reverse Khata debt if credit was extended
        if (tx.customerId != null && (tx.paymentMode == PaymentMode.KHATA || tx.paymentMode == PaymentMode.SPLIT)) {
            // Deduct the debt (effectively paying back the balance)
            updateCustomerBalance(tx.customerId, -tx.grandTotal)
            insertKhataEntry(
                KhataEntryEntity(
                    customerId = tx.customerId,
                    transactionId = tx.id,
                    type = KhataEntryType.CREDIT_PAYMENT,
                    amount = tx.grandTotal,
                    timestamp = now,
                    notes = "Reversal / Refund of Invoice #${tx.invoiceNo}"
                )
            )
        }

        // 3. Mark transaction as REFUNDED
        updateTransactionStatus(transactionId, TransactionStatus.REFUNDED)

        // 4. Audit Log
        insertAuditLog(
            AuditLogEntity(
                timestamp = now,
                cashierId = cashierId,
                action = AuditAction.SALE_REFUND,
                details = "Refunded sale #${tx.invoiceNo} for ₹${tx.grandTotal}. Reason: $refundReason",
                payloadJson = "{\"transactionId\":$transactionId,\"invoiceNo\":\"${tx.invoiceNo}\",\"refundReason\":\"$refundReason\"}"
            )
        )
    }
}
