package com.digitalmunshi.pos.core.security

import com.digitalmunshi.pos.data.local.entities.UserEntity
import com.digitalmunshi.pos.domain.models.UserRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class UserSession(
    val user: UserEntity?,
    val role: UserRole,
    val isAuthenticated: Boolean,
    val sessionStartTime: Long
)

object SessionManager {

    private val _currentSession = MutableStateFlow(
        UserSession(
            user = null,
            role = UserRole.CASHIER,
            isAuthenticated = false,
            sessionStartTime = 0L
        )
    )
    val currentSession: StateFlow<UserSession> = _currentSession.asStateFlow()

    fun login(user: UserEntity) {
        _currentSession.value = UserSession(
            user = user,
            role = user.role,
            isAuthenticated = true,
            sessionStartTime = System.currentTimeMillis()
        )
    }

    fun logout() {
        _currentSession.value = UserSession(
            user = null,
            role = UserRole.CASHIER,
            isAuthenticated = false,
            sessionStartTime = 0L
        )
    }

    val currentRole: UserRole
        get() = _currentSession.value.role

    val currentCashierId: String
        get() = _currentSession.value.user?.username ?: "ANONYMOUS_CASHIER"

    /**
     * Cashier Lock Security Policies:
     * Disables cost prices, margins, reports, and overrides when active role is CASHIER.
     */
    fun canViewCostPrice(): Boolean = currentRole == UserRole.ADMIN

    fun canViewNetMargins(): Boolean = currentRole == UserRole.ADMIN

    fun canViewDayCloseReports(): Boolean = currentRole == UserRole.ADMIN

    fun canOverrideCartPrice(): Boolean = currentRole == UserRole.ADMIN

    fun canPerformRefund(): Boolean = currentRole == UserRole.ADMIN

    fun canTriggerManualDrawerKick(): Boolean = currentRole == UserRole.ADMIN
}
