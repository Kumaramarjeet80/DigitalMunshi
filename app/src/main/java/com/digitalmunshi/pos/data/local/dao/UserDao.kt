package com.digitalmunshi.pos.data.local.dao

import androidx.room.*
import com.digitalmunshi.pos.data.local.entities.UserEntity
import com.digitalmunshi.pos.domain.models.UserRole
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {

    @Query("SELECT * FROM users WHERE username = :username AND is_active = 1 LIMIT 1")
    suspend fun getUserByUsername(username: String): UserEntity?

    @Query("SELECT * FROM users WHERE role = :role AND is_active = 1")
    suspend fun getUsersByRole(role: UserRole): List<UserEntity>

    @Query("SELECT * FROM users ORDER BY username ASC")
    fun getAllUsersFlow(): Flow<List<UserEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertUser(user: UserEntity): Long

    @Update
    suspend fun updateUser(user: UserEntity)

    @Query("UPDATE users SET pin_hash = :newPinHash, salt = :newSalt WHERE id = :userId")
    suspend fun updatePin(userId: Long, newPinHash: String, newSalt: String)

    @Query("SELECT COUNT(*) FROM users")
    suspend fun getUserCount(): Int
}
