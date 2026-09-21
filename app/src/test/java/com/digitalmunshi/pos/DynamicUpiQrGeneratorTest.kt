package com.digitalmunshi.pos

import com.digitalmunshi.pos.core.qr.DynamicUpiQrGenerator
import org.junit.Assert.*
import org.junit.Test

class DynamicUpiQrGeneratorTest {

    @Test
    fun `buildUpiUri generates compliant NPCI standard URI`() {
        val uri = DynamicUpiQrGenerator.buildUpiUri(
            payeeVpa = "merchant@okaxis",
            payeeName = "Digital Munshi Store",
            amount = 1450.50,
            transactionRef = "INV-2026-001",
            transactionNote = "POS Bill #INV-2026-001"
        )

        assertTrue(uri.startsWith("upi://pay?"))
        assertTrue(uri.contains("pa=merchant@okaxis"))
        assertTrue(uri.contains("am=1450.50"))
        assertTrue(uri.contains("cu=INR"))
        assertTrue(uri.contains("tr=INV-2026-001"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `buildUpiUri rejects zero or negative amount`() {
        DynamicUpiQrGenerator.buildUpiUri(
            payeeVpa = "merchant@upi",
            payeeName = "Store",
            amount = 0.0,
            transactionRef = "INV-001"
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `buildUpiUri rejects blank VPA`() {
        DynamicUpiQrGenerator.buildUpiUri(
            payeeVpa = "   ",
            payeeName = "Store",
            amount = 100.0,
            transactionRef = "INV-001"
        )
    }
}
