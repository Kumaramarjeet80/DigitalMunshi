package com.digitalmunshi.pos.domain.models

enum class UnitType(val displayName: String, val allowsFractional: Boolean) {
    PIECE("Pcs", false),
    KG("Kg", true),
    GRAM("g", true),
    LITER("L", true)
}

enum class PaymentMode {
    CASH,
    UPI,
    KHATA,
    SPLIT
}

enum class TransactionStatus {
    COMPLETED,
    REFUNDED
}

enum class KhataEntryType {
    DEBIT,           // Customer owes money (credit purchase at checkout)
    CREDIT_PAYMENT   // Customer pays back dues
}

enum class UserRole {
    ADMIN,
    CASHIER
}

enum class AuditAction {
    SALE_CHECKOUT,
    SALE_REFUND,
    ITEM_DELETED_FROM_CART,
    ZERO_VALUE_DRAWER_KICK,
    PRICE_OVERRIDE,
    BACKUP_EXPORTED,
    BACKUP_RESTORED,
    USER_LOGIN,
    DAY_CLOSE
}
