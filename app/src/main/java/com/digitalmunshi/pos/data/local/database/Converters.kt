package com.digitalmunshi.pos.data.local.database

import androidx.room.TypeConverter
import com.digitalmunshi.pos.domain.models.*

class Converters {
    @TypeConverter
    fun fromUnitType(value: UnitType): String = value.name

    @TypeConverter
    fun toUnitType(value: String): UnitType = runCatching { UnitType.valueOf(value) }.getOrDefault(UnitType.PIECE)

    @TypeConverter
    fun fromPaymentMode(value: PaymentMode): String = value.name

    @TypeConverter
    fun toPaymentMode(value: String): PaymentMode = runCatching { PaymentMode.valueOf(value) }.getOrDefault(PaymentMode.CASH)

    @TypeConverter
    fun fromTransactionStatus(value: TransactionStatus): String = value.name

    @TypeConverter
    fun toTransactionStatus(value: String): TransactionStatus = runCatching { TransactionStatus.valueOf(value) }.getOrDefault(TransactionStatus.COMPLETED)

    @TypeConverter
    fun fromKhataEntryType(value: KhataEntryType): String = value.name

    @TypeConverter
    fun toKhataEntryType(value: String): KhataEntryType = runCatching { KhataEntryType.valueOf(value) }.getOrDefault(KhataEntryType.DEBIT)

    @TypeConverter
    fun fromUserRole(value: UserRole): String = value.name

    @TypeConverter
    fun toUserRole(value: String): UserRole = runCatching { UserRole.valueOf(value) }.getOrDefault(UserRole.CASHIER)

    @TypeConverter
    fun fromAuditAction(value: AuditAction): String = value.name

    @TypeConverter
    fun toAuditAction(value: String): AuditAction = runCatching { AuditAction.valueOf(value) }.getOrDefault(AuditAction.SALE_CHECKOUT)
}
