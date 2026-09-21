package com.digitalmunshi.pos.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.digitalmunshi.pos.domain.models.UserRole

@Entity(
    tableName = "users",
    indices = [
        Index(value = ["username"], unique = true)
    ]
)
data class UserEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "username")
    val username: String,

    @ColumnInfo(name = "display_name")
    val displayName: String,

    @ColumnInfo(name = "pin_hash")
    val pinHash: String, // PBKDF2WithHmacSHA256 hash of PIN

    @ColumnInfo(name = "salt")
    val salt: String, // Hex encoded salt

    @ColumnInfo(name = "role")
    val role: UserRole, // ADMIN or CASHIER

    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
