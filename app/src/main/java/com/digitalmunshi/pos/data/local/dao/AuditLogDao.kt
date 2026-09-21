package com.digitalmunshi.pos.data.local.dao

import androidx.room.*
import com.digitalmunshi.pos.data.local.entities.AuditLogEntity
import com.digitalmunshi.pos.domain.models.AuditAction
import kotlinx.coroutines.flow.Flow

@Dao
interface AuditLogDao {

    @Insert
    suspend fun insertLog(log: AuditLogEntity): Long

    @Query("SELECT * FROM audit_logs ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentLogsFlow(limit: Int = 200): Flow<List<AuditLogEntity>>

    @Query("SELECT * FROM audit_logs WHERE action = :action ORDER BY timestamp DESC")
    fun getLogsByActionFlow(action: AuditAction): Flow<List<AuditLogEntity>>

    @Query("SELECT * FROM audit_logs WHERE cashier_id = :cashierId ORDER BY timestamp DESC")
    fun getLogsByCashierFlow(cashierId: String): Flow<List<AuditLogEntity>>
}
