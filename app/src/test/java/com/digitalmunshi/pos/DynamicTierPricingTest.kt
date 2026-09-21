package com.digitalmunshi.pos

import com.digitalmunshi.pos.data.local.entities.ProductEntity
import com.digitalmunshi.pos.domain.models.CartItem
import com.digitalmunshi.pos.domain.models.UnitType
import org.junit.Assert.*
import org.junit.Test

class DynamicTierPricingTest {

    private val sampleBasmatiRice = ProductEntity(
        id = 1L,
        barcode = "8901030383344",
        sku = "RICE-01",
        name = "Royal Basmati Rice",
        category = "Grains",
        unitType = UnitType.KG,
        costPrice = 80.0,
        retailPrice = 110.0,
        wholesalePrice = 95.0,
        wholesaleMinQty = 5.0, // Wholesale threshold
        taxSlab = 5.0, // 5% GST
        stockQty = 100.0,
        minStockWarning = 10.0
    )

    @Test
    fun `retail pricing applies when quantity is below wholesale threshold`() {
        // Buying 2.5 kg (< 5.0 kg threshold)
        val cartItem = CartItem(
            product = sampleBasmatiRice,
            quantity = 2.5
        )

        assertFalse("Wholesale rate should NOT be applied", cartItem.isWholesaleApplied)
        assertEquals("Unit price should equal retail price", 110.0, cartItem.appliedUnitPrice, 0.001)

        // Subtotal: 2.5 * 110 = 275.0
        assertEquals(275.0, cartItem.lineSubtotal, 0.001)

        // 5% tax: 275 * 0.05 = 13.75
        assertEquals(13.75, cartItem.taxAmount, 0.001)

        // Line total: 275 + 13.75 = 288.75
        assertEquals(288.75, cartItem.lineTotal, 0.001)
    }

    @Test
    fun `dynamic tier switching shifts unit price to wholesale rate when threshold is met`() {
        // Buying 5.0 kg (exactly reaches wholesale threshold)
        val cartItem = CartItem(
            product = sampleBasmatiRice,
            quantity = 5.0
        )

        assertTrue("Wholesale rate SHOULD be applied", cartItem.isWholesaleApplied)
        assertEquals("Unit price should dynamically shift to wholesale price", 95.0, cartItem.appliedUnitPrice, 0.001)

        // Subtotal: 5.0 * 95 = 475.0
        assertEquals(475.0, cartItem.lineSubtotal, 0.001)

        // 5% tax: 475 * 0.05 = 23.75
        assertEquals(23.75, cartItem.taxAmount, 0.001)

        // Line total: 475 + 23.75 = 498.75
        assertEquals(498.75, cartItem.lineTotal, 0.001)
    }

    @Test
    fun `fractional decimal weight calculation precision`() {
        // Electronic scale reading: 1.450 kg
        val cartItem = CartItem(
            product = sampleBasmatiRice,
            quantity = 1.450
        )

        assertFalse(cartItem.isWholesaleApplied)
        assertEquals(110.0, cartItem.appliedUnitPrice, 0.001)

        // Subtotal: 1.450 * 110.0 = 159.50
        assertEquals(159.50, cartItem.lineSubtotal, 0.001)
        // Tax: 159.50 * 0.05 = 7.975
        assertEquals(7.975, cartItem.taxAmount, 0.001)
        // Line total: 159.50 + 7.975 = 167.475
        assertEquals(167.475, cartItem.lineTotal, 0.001)
    }

    @Test
    fun `manual price override takes precedence over dynamic wholesale tier`() {
        val cartItem = CartItem(
            product = sampleBasmatiRice,
            quantity = 10.0, // Above wholesale min qty
            manualOverridePrice = 90.0 // Special negotiated price
        )

        assertFalse("Wholesale flag is overridden", cartItem.isWholesaleApplied)
        assertEquals(90.0, cartItem.appliedUnitPrice, 0.001)
        assertEquals(900.0, cartItem.lineSubtotal, 0.001)
    }
}
