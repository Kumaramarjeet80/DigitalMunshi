package com.digitalmunshi.pos.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.digitalmunshi.pos.data.local.dao.KhataDao
import com.digitalmunshi.pos.data.local.dao.TransactionDao
import com.digitalmunshi.pos.data.local.entities.KhataCustomerEntity
import com.digitalmunshi.pos.domain.usecases.KhataReminderData
import com.digitalmunshi.pos.domain.usecases.KhataReminderGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class KhataUiState(
    val customersWithDues: List<KhataCustomerEntity> = emptyList(),
    val allCustomers: List<KhataCustomerEntity> = emptyList(),
    val totalOutstandingDebt: Double = 0.0,
    val selectedCustomerForSettlement: KhataCustomerEntity? = null,
    val isRecordingPayment: Boolean = false,
    val errorMessage: String? = null
)

class KhataViewModel(
    private val khataDao: KhataDao,
    private val transactionDao: TransactionDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(KhataUiState())
    val uiState: StateFlow<KhataUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            khataDao.getAllCustomersFlow().collect { all ->
                val duesOnly = all.filter { it.currentBalance > 0 }
                val totalDebt = duesOnly.sumOf { it.currentBalance }
                _uiState.update {
                    it.copy(
                        allCustomers = all,
                        customersWithDues = duesOnly,
                        totalOutstandingDebt = totalDebt
                    )
                }
            }
        }
    }

    /**
     * Single-tap offline payment reminder generator:
     * Builds pre-filled SMS/WhatsApp text with customer balance, pending invoice details, and merchant UPI link.
     */
    fun generateReminder(
        context: Context,
        customer: KhataCustomerEntity,
        storeName: String = "Digital Munshi Store",
        merchantVpa: String = "merchant@upi",
        merchantName: String = "Digital Munshi Store"
    ): KhataReminderData {
        return KhataReminderGenerator.createReminder(
            customer = customer,
            storeName = storeName,
            merchantVpa = merchantVpa,
            merchantName = merchantName
        )
    }

    /**
     * Records a repayment / settlement from customer towards their balance
     */
    fun recordSettlement(customerId: Long, amount: Double, notes: String = "Cash Settlement") {
        if (amount <= 0.0) return

        viewModelScope.launch {
            _uiState.update { it.copy(isRecordingPayment = true) }
            try {
                withContext(Dispatchers.IO) {
                    khataDao.recordCustomerPayment(customerId, amount, notes)
                }
                _uiState.update { it.copy(isRecordingPayment = false, selectedCustomerForSettlement = null) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isRecordingPayment = false, errorMessage = e.message) }
            }
        }
    }

    fun addNewCustomer(name: String, phone: String, creditLimit: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            val customer = KhataCustomerEntity(
                name = name.trim(),
                phone = phone.trim(),
                maxCreditLimit = creditLimit,
                currentBalance = 0.0
            )
            khataDao.insertCustomer(customer)
        }
    }
}
