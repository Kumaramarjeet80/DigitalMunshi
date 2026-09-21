package com.digitalmunshi.pos.domain.models

import com.digitalmunshi.pos.data.local.entities.BatchEntity
import com.digitalmunshi.pos.data.local.entities.ProductEntity
import java.util.UUID

data class CartItem(
    val cartItemId: String = UUID.randomUUID().toString(),
    val product: ProductEntity,
    val selectedBatch: BatchEntity? = null,
    val quantity: Double = 1.0,
    val manualOverridePrice: Double? = null
) {
    /**
     * Dynamic Tier Switching:
     * Shifts unit price to wholesalePrice automatically when wholesaleMinQty threshold is met!
     */
    val isWholesaleApplied: Boolean
        get() = manualOverridePrice == null &&
                product.wholesalePrice > 0.0 &&
                quantity >= product.wholesaleMinQty

    val appliedUnitPrice: Double
        get() = manualOverridePrice
            ?: if (isWholesaleApplied) product.wholesalePrice else product.retailPrice

    val lineSubtotal: Double
        get() = quantity * appliedUnitPrice

    val taxAmount: Double
        get() = (lineSubtotal * product.taxSlab) / 100.0

    val lineTotal: Double
        get() = lineSubtotal + taxAmount

    val costPriceSnapshot: Double
        get() = selectedBatch?.costPrice ?: product.costPrice
}

data class CartTotals(
    val subtotal: Double,
    val taxTotal: Double,
    val discountTotal: Double,
    val grandTotal: Double,
    val itemCount: Int,
    val totalWeightKg: Double
)
