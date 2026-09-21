package com.digitalmunshi.pos.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.digitalmunshi.pos.domain.models.AuditAction

@Entity(
    tableName = "audit_logs",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["action"]),
        Index(value = ["cashier_id"])
    ]
)
data class AuditLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "cashier_id")
    val cashierId: String,

    @ColumnInfo(name = "action")
    val action: AuditAction,

    @ColumnInfo(name = "details")
    val details: String,

    @ColumnInfo(name = "payload_json")
    val payloadJson: String? = null
)
