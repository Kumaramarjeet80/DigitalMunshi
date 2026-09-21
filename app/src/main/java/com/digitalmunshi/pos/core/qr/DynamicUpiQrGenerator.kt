package com.digitalmunshi.pos.core.qr

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.net.URLEncoder
import java.util.*

object DynamicUpiQrGenerator {

    /**
     * Builds a compliant NPCI UPI payment deep-link URI:
     * upi://pay?pa=...&pn=...&am=...&cu=INR&tr=...&tn=...
     */
    fun buildUpiUri(
        payeeVpa: String,
        payeeName: String,
        amount: Double,
        transactionRef: String,
        transactionNote: String = "Digital Munshi POS Bill"
    ): String {
        require(payeeVpa.isNotBlank()) { "Payee VPA cannot be blank." }
        require(amount > 0.0) { "Amount must be greater than zero." }

        val formattedAmount = String.format(Locale.US, "%.2f", amount)
        val encodedName = URLEncoder.encode(payeeName, "UTF-8")
        val encodedNote = URLEncoder.encode(transactionNote, "UTF-8")
        val encodedRef = URLEncoder.encode(transactionRef, "UTF-8")

        return "upi://pay?pa=$payeeVpa&pn=$encodedName&am=$formattedAmount&cu=INR&tr=$encodedRef&tn=$encodedNote"
    }

    /**
     * Generates a monochrome ARGB_8888 Bitmap using ZXing Core.
     * Suitable for Jetpack Compose Image and customer-facing screen display.
     */
    fun generateQrBitmap(
        content: String,
        width: Int = 512,
        height: Int = 512
    ): Bitmap {
        val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
            put(EncodeHintType.CHARACTER_SET, "UTF-8")
            put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M)
            put(EncodeHintType.MARGIN, 1) // 1 quiet zone module
        }

        val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, width, height, hints)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE)
            }
        }

        return bitmap
    }
}
