package com.digitalmunshi.pos.core.hardware.printer

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import com.digitalmunshi.pos.core.hardware.escpos.StoreReceiptMetadata
import com.digitalmunshi.pos.data.local.entities.TransactionEntity
import com.digitalmunshi.pos.domain.models.CartItem
import com.digitalmunshi.pos.domain.models.CartTotals
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object InvoicePdfGenerator {

    private val dateFormat = SimpleDateFormat("dd-MMM-yyyy hh:mm a", Locale.US)

    /**
     * Generates a clean, professional offline printable PDF invoice receipt.
     * Saved to app-specific local documents storage: files/invoices/
     */
    fun generateInvoicePdf(
        context: Context,
        store: StoreReceiptMetadata,
        transaction: TransactionEntity,
        items: List<CartItem>,
        totals: CartTotals,
        upiQrBitmap: Bitmap? = null
    ): File {
        val invoicesDir = File(context.filesDir, "invoices").apply { mkdirs() }
        val cleanInvoiceNo = transaction.invoiceNo.replace("/", "_").replace("-", "_")
        val pdfFile = File(invoicesDir, "Invoice_${cleanInvoiceNo}.pdf")

        val pageWidth = 400 // Standard 80mm thermal receipt / slip format in points
        val baseHeight = 380 + (items.size * 32) + (if (upiQrBitmap != null) 140 else 0)
        val pageHeight = maxOf(600, baseHeight)

        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        // Background
        val bgPaint = Paint().apply { color = Color.WHITE }
        canvas.drawRect(0f, 0f, pageWidth.toFloat(), pageHeight.toFloat(), bgPaint)

        // Paints
        val textPaint = Paint().apply {
            color = Color.DKGRAY
            isAntiAlias = true
            textSize = 10f
        }

        val boldTitlePaint = Paint().apply {
            color = Color.BLACK
            isAntiAlias = true
            isFakeBoldText = true
            textSize = 16f
            textAlign = Paint.Align.CENTER
        }

        val centerTextPaint = Paint().apply {
            color = Color.GRAY
            isAntiAlias = true
            textSize = 9f
            textAlign = Paint.Align.CENTER
        }

        val dividerPaint = Paint().apply {
            color = Color.LTGRAY
            strokeWidth = 1f
            style = Paint.Style.STROKE
        }

        var y = 30f
        val centerX = pageWidth / 2f

        // 1. Store Header
        canvas.drawText(store.storeName, centerX, y, boldTitlePaint)
        y += 16f
        canvas.drawText(store.addressLine1, centerX, y, centerTextPaint)
        y += 14f
        canvas.drawText("Tel: ${store.phone} | GSTIN: ${store.gstin}", centerX, y, centerTextPaint)
        y += 18f

        // Top Divider
        canvas.drawLine(20f, y, pageWidth - 20f, y, dividerPaint)
        y += 16f

        // 2. Invoice Details
        textPaint.isFakeBoldText = true
        canvas.drawText("TAX INVOICE", 20f, y, textPaint)
        canvas.drawText("Payment: ${transaction.paymentMode}", pageWidth - 140f, y, textPaint)
        y += 14f

        textPaint.isFakeBoldText = false
        canvas.drawText("Invoice No : #${transaction.invoiceNo}", 20f, y, textPaint)
        canvas.drawText("Cashier : ${transaction.cashierId}", pageWidth - 140f, y, textPaint)
        y += 14f
        canvas.drawText("Date       : ${dateFormat.format(Date(transaction.timestamp))}", 20f, y, textPaint)
        y += 18f

        // Header Divider
        canvas.drawLine(20f, y, pageWidth - 20f, y, dividerPaint)
        y += 16f

        // 3. Tabular Columns: Item (180), Qty (60), Rate (60), Total (60)
        val headerPaint = Paint().apply {
            color = Color.BLACK
            isAntiAlias = true
            isFakeBoldText = true
            textSize = 9.5f
        }
        canvas.drawText("ITEM DESCRIPTION", 20f, y, headerPaint)
        canvas.drawText("QTY", 210f, y, headerPaint)
        canvas.drawText("RATE", 270f, y, headerPaint)
        canvas.drawText("TOTAL", 330f, y, headerPaint)
        y += 8f
        canvas.drawLine(20f, y, pageWidth - 20f, y, dividerPaint)
        y += 16f

        // 4. Items
        for (item in items) {
            val itemName = if (item.product.name.length > 24) item.product.name.take(22) + ".." else item.product.name
            canvas.drawText(itemName, 20f, y, textPaint)
            val qtyStr = String.format(Locale.US, "%.2f %s", item.quantity, item.product.unitType.displayName)
            canvas.drawText(qtyStr, 210f, y, textPaint)
            canvas.drawText("₹${String.format(Locale.US, "%.2f", item.appliedUnitPrice)}", 270f, y, textPaint)
            canvas.drawText("₹${String.format(Locale.US, "%.2f", item.lineTotal)}", 330f, y, textPaint)
            y += 14f

            if (item.isWholesaleApplied) {
                val wholesaleTagPaint = Paint().apply {
                    color = Color.parseColor("#059669")
                    isAntiAlias = true
                    textSize = 8f
                }
                canvas.drawText("  *Wholesale tier rate applied", 20f, y, wholesaleTagPaint)
                y += 12f
            }
        }

        y += 6f
        canvas.drawLine(20f, y, pageWidth - 20f, y, dividerPaint)
        y += 16f

        // 5. Totals
        val alignRightPaint = Paint().apply {
            color = Color.DKGRAY
            isAntiAlias = true
            textSize = 10f
            textAlign = Paint.Align.RIGHT
        }

        canvas.drawText("Subtotal : ₹${String.format(Locale.US, "%.2f", totals.subtotal)}", pageWidth - 20f, y, alignRightPaint)
        y += 14f

        if (totals.taxTotal > 0.0) {
            val halfTax = totals.taxTotal / 2.0
            canvas.drawText("CGST : ₹${String.format(Locale.US, "%.2f", halfTax)}", pageWidth - 20f, y, alignRightPaint)
            y += 14f
            canvas.drawText("SGST : ₹${String.format(Locale.US, "%.2f", halfTax)}", pageWidth - 20f, y, alignRightPaint)
            y += 14f
        }

        val grandTotalPaint = Paint().apply {
            color = Color.BLACK
            isAntiAlias = true
            isFakeBoldText = true
            textSize = 14f
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("GRAND TOTAL : ₹${String.format(Locale.US, "%.2f", totals.grandTotal)}", pageWidth - 20f, y, grandTotalPaint)
        y += 20f
        canvas.drawLine(20f, y, pageWidth - 20f, y, dividerPaint)
        y += 20f

        // 6. Dynamic UPI QR on receipt
        if (upiQrBitmap != null) {
            val qrSize = 100
            val qrLeft = centerX - (qrSize / 2f)
            val scaledBitmap = Bitmap.createScaledBitmap(upiQrBitmap, qrSize, qrSize, false)
            canvas.drawBitmap(scaledBitmap, qrLeft, y, null)
            y += qrSize + 14f
            canvas.drawText("Scan to Pay via any UPI App", centerX, y, centerTextPaint)
            y += 16f
        }

        // 7. Footer
        canvas.drawText(store.footerMessage, centerX, y, centerTextPaint)
        y += 12f
        canvas.drawText("Generated Offline by Digital Munshi Native POS", centerX, y, centerTextPaint)

        pdfDocument.finishPage(page)
        FileOutputStream(pdfFile).use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()

        return pdfFile
    }
}
