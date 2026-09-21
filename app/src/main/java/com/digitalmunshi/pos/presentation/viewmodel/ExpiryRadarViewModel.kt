package com.digitalmunshi.pos.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.digitalmunshi.pos.data.local.dao.BatchDao
import com.digitalmunshi.pos.data.local.dao.ExpiringBatchWithProduct
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

enum class ExpirySeverity {
    EXPIRED,   // Passed today
    CRITICAL,  // Expiring within 7 days
    WARNING,   // Expiring within 30 days
    UPCOMING   // Within selected horizon
}

data class ExpiryRadarItem(
    val batchWithProduct: ExpiringBatchWithProduct,
    val daysRemaining: Int,
    val severity: ExpirySeverity
)

data class ExpiryRadarUiState(
    val thresholdDays: Int = 30,
    val items: List<ExpiryRadarItem> = emptyList(),
    val expiredCount: Int = 0,
    val criticalCount: Int = 0,
    val totalAtRiskStockValue: Double = 0.0,
    val isLoading: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class ExpiryRadarViewModel(
    private val batchDao: BatchDao
) : ViewModel() {

    private val _thresholdDays = MutableStateFlow(30)
    val thresholdDays: StateFlow<Int> = _thresholdDays.asStateFlow()

    val uiState: StateFlow<ExpiryRadarUiState> = _thresholdDays
        .flatMapLatest { days ->
            val now = System.currentTimeMillis()
            val thresholdMillis = now + (days.toLong() * 86_400_000L)

            batchDao.getExpiringBatchesRadarFlow(thresholdMillis).map { rawList ->
                var expired = 0
                var critical = 0
                var totalAtRiskValue = 0.0

                val radarItems = rawList.map { item ->
                    val diffMillis = item.batch.expiryDate - now
                    val remainingDays = (diffMillis / 86_400_000L).toInt()

                    val severity = when {
                        remainingDays < 0 -> {
                            expired++
                            ExpirySeverity.EXPIRED
                        }
                        remainingDays <= 7 -> {
                            critical++
                            ExpirySeverity.CRITICAL
                        }
                        remainingDays <= 30 -> ExpirySeverity.WARNING
                        else -> ExpirySeverity.UPCOMING
                    }

                    totalAtRiskValue += (item.batch.costPrice * item.batch.stockQty)

                    ExpiryRadarItem(
                        batchWithProduct = item,
                        daysRemaining = remainingDays,
                        severity = severity
                    )
                }

                ExpiryRadarUiState(
                    thresholdDays = days,
                    items = radarItems,
                    expiredCount = expired,
                    criticalCount = critical,
                    totalAtRiskStockValue = totalAtRiskValue,
                    isLoading = false
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ExpiryRadarUiState(isLoading = true)
        )

    fun setThresholdDays(days: Int) {
        _thresholdDays.value = days
    }
}
