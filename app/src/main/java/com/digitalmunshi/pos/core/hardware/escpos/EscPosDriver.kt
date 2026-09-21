package com.digitalmunshi.pos.core.hardware.escpos

import android.graphics.Bitmap
import android.graphics.Color
import com.digitalmunshi.pos.domain.models.CartItem
import com.digitalmunshi.pos.domain.models.CartTotals
import com.digitalmunshi.pos.domain.models.PaymentMode
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*

enum class PrinterPaperWidth(val charsPerLine: Int, val maxQrSizePixels: Int) {
    WIDTH_58MM(32, 256),
    WIDTH_80MM(48, 384)
}

data class StoreReceiptMetadata(
    val storeName: String = "DIGITAL MUNSHI STORE",
    val addressLine1: String = "Main Market Road, Commercial Hub",
    val phone: String = "+91 98765 43210",
    val gstin: String = "07AAAAA0000A1Z5",
    val footerMessage: String = "Thank you for your visit! No Returns on Fresh Food."
)

class EscPosDriver(
    private val paperWidth: PrinterPaperWidth = PrinterPaperWidth.WIDTH_80MM
) {

    private val dateFormat = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.US)

    /**
     * Builds a full receipt byte payload containing:
     * - Bold centered header
     * - GSTIN and invoice metadata
     * - Tabular line items with fractional weights
     * - Tax breakdown (CGST / SGST)
     * - Printed Dynamic UPI QR code (if applicable or requested)
     * - Footer and paper partial cut
     * - Optional drawer kick pulse
     */
    fun buildReceiptBytes(
        store: StoreReceiptMetadata,
        invoiceNo: String,
        cashierName: String,
        items: List<CartItem>,
        totals: CartTotals,
        paymentMode: PaymentMode,
        upiQrBitmap: Bitmap? = null,
        triggerCashDrawer: Boolean = (paymentMode == PaymentMode.CASH)
    ): ByteArray {
        val stream = ByteArrayOutputStream()

        // 1. Initialize printer
        stream.write(EscPosCommands.INIT_PRINTER)

        // 2. Header (Centered, Double Size / Bold)
        stream.write(EscPosCommands.ALIGN_CENTER)
        stream.write(EscPosCommands.DOUBLE_SIZE_ON)
        stream.write(EscPosCommands.BOLD_ON)
        stream.write("${store.storeName}\n".toByteArray(Charsets.UTF_8))
        stream.write(EscPosCommands.TEXT_NORMAL)
        stream.write(EscPosCommands.BOLD_OFF)

        stream.write("${store.addressLine1}\n".toByteArray(Charsets.UTF_8))
        stream.write("Tel: ${store.phone} | GSTIN: ${store.gstin}\n".toByteArray(Charsets.UTF_8))
        stream.write(dividerLine().toByteArray(Charsets.UTF_8))

        // 3. Invoice Metadata (Left Aligned)
        stream.write(EscPosCommands.ALIGN_LEFT)
        stream.write("Invoice : $invoiceNo\n".toByteArray(Charsets.UTF_8))
        stream.write("Date    : ${dateFormat.format(Date())}\n".toByteArray(Charsets.UTF_8))
        stream.write("Cashier : $cashierName | Pay: $paymentMode\n".toByteArray(Charsets.UTF_8))
        stream.write(dividerLine().toByteArray(Charsets.UTF_8))

        // 4. Tabular Header
        val colWidth = paperWidth.charsPerLine
        stream.write(EscPosCommands.BOLD_ON)
        if (paperWidth == PrinterPaperWidth.WIDTH_80MM) {
            // 80mm: Item Name (20), Qty (9), Rate (9), Total (10)
            stream.write(String.format(Locale.US, "%-20s %8s %8s %9s\n", "ITEM", "QTY", "RATE", "TOTAL").toByteArray(Charsets.UTF_8))
        } else {
            // 58mm: Item Name (14), Qty (6), Total (10)
            stream.write(String.format(Locale.US, "%-14s %7s %9s\n", "ITEM", "QTY", "TOTAL").toByteArray(Charsets.UTF_8))
        }
        stream.write(EscPosCommands.BOLD_OFF)
        stream.write(dividerLine().toByteArray(Charsets.UTF_8))

        // 5. Line Items
        for (item in items) {
            val name = if (item.product.name.length > 19 && paperWidth == PrinterPaperWidth.WIDTH_80MM) {
                item.product.name.take(19)
            } else if (item.product.name.length > 13 && paperWidth == PrinterPaperWidth.WIDTH_58MM) {
                item.product.name.take(13)
            } else {
                item.product.name
            }

            val qtyStr = String.format(Locale.US, "%.3f%s", item.quantity, item.product.unitType.displayName)
            val rateStr = String.format(Locale.US, "%.2f", item.appliedUnitPrice)
            val totalStr = String.format(Locale.US, "%.2f", item.lineTotal)

            if (paperWidth == PrinterPaperWidth.WIDTH_80MM) {
                val line = String.format(Locale.US, "%-20s %8s %8s %9s\n", name, qtyStr, rateStr, totalStr)
                stream.write(line.toByteArray(Charsets.UTF_8))
            } else {
                val line = String.format(Locale.US, "%-14s %7s %9s\n", name, qtyStr, totalStr)
                stream.write(line.toByteArray(Charsets.UTF_8))
            }

            // If wholesale tier applied, show note
            if (item.isWholesaleApplied) {
                stream.write("  *Wholesale Rate Applied*\n".toByteArray(Charsets.UTF_8))
            }
        }
        stream.write(dividerLine().toByteArray(Charsets.UTF_8))

        // 6. Totals Section
        stream.write(EscPosCommands.ALIGN_RIGHT)
        stream.write(String.format(Locale.US, "Subtotal: Rs. %10.2f\n", totals.subtotal).toByteArray(Charsets.UTF_8))
        if (totals.taxTotal > 0.0) {
            val halfTax = totals.taxTotal / 2.0
            stream.write(String.format(Locale.US, "CGST: Rs. %10.2f\n", halfTax).toByteArray(Charsets.UTF_8))
            stream.write(String.format(Locale.US, "SGST: Rs. %10.2f\n", halfTax).toByteArray(Charsets.UTF_8))
        }
        if (totals.discountTotal > 0.0) {
            stream.write(String.format(Locale.US, "Discount: -Rs. %9.2f\n", totals.discountTotal).toByteArray(Charsets.UTF_8))
        }

        stream.write(EscPosCommands.BOLD_ON)
        stream.write(EscPosCommands.DOUBLE_HEIGHT_ON)
        stream.write(String.format(Locale.US, "GRAND TOTAL: Rs. %10.2f\n", totals.grandTotal).toByteArray(Charsets.UTF_8))
        stream.write(EscPosCommands.TEXT_NORMAL)
        stream.write(EscPosCommands.BOLD_OFF)
        stream.write(dividerLine().toByteArray(Charsets.UTF_8))

        // 7. Dynamic UPI QR on Receipt
        if (upiQrBitmap != null) {
            stream.write(EscPosCommands.ALIGN_CENTER)
            stream.write("Scan to Pay with Any UPI App\n".toByteArray(Charsets.UTF_8))
            val rasterBytes = convertBitmapToEscPosRaster(upiQrBitmap)
            stream.write(rasterBytes)
            stream.write(EscPosCommands.LINE_FEED)
        }

        // 8. Footer
        stream.write(EscPosCommands.ALIGN_CENTER)
        stream.write("${store.footerMessage}\n".toByteArray(Charsets.UTF_8))
        stream.write("Software: Digital Munshi (Offline POS)\n".toByteArray(Charsets.UTF_8))

        // 9. Feed & Paper Cut
        stream.write(EscPosCommands.FEED_5_LINES)
        stream.write(EscPosCommands.PAPER_PARTIAL_CUT)

        // 10. Cash Drawer Kick Pulse (if Cash sale or forced)
        if (triggerCashDrawer) {
            stream.write(EscPosCommands.DRAWER_KICK_PULSE)
        }

        return stream.toByteArray()
    }

    /**
     * Converts an Android Bitmap into standard ESC/POS "GS v 0" raster bit-image commands.
     * Compatible with Epson, Star, Bixolon, Xprinter, TVS, and generic thermal printers.
     */
    fun convertBitmapToEscPosRaster(bitmap: Bitmap): ByteArray {
        val width = bitmap.width
        val height = bitmap.height
        val widthBytes = (width + 7) / 8

        val out = ByteArrayOutputStream()

        // Command: GS v 0 m xL xH yL yH
        out.write(EscPosCommands.GS.toInt())
        out.write(0x76) // 'v'
        out.write(0x30) // '0'
        out.write(0)    // normal mode m=0

        out.write(widthBytes % 256)
        out.write(widthBytes / 256)
        out.write(height % 256)
        out.write(height / 256)

        for (y in 0 until height) {
            for (byteX in 0 until widthBytes) {
                var slice = 0
                for (bit in 0 until 8) {
                    val pixelX = (byteX * 8) + bit
                    if (pixelX < width) {
                        val color = bitmap.getPixel(pixelX, y)
                        // In luminance: R*0.299 + G*0.587 + B*0.114 < 128 -> Black dot
                        val r = Color.red(color)
                        val g = Color.green(color)
                        val b = Color.blue(color)
                        val luminance = (r * 299 + g * 587 + b * 114) / 1000
                        if (luminance < 128) {
                            slice = slice or (1 shl (7 - bit))
                        }
                    }
                }
                out.write(slice)
            }
        }

        return out.toByteArray()
    }

    /**
     * Cash drawer kick pulse standalone generator
     */
    fun getDrawerKickBytes(): ByteArray {
        return EscPosCommands.DRAWER_KICK_PULSE
    }

    private fun dividerLine(): String = "-".repeat(paperWidth.charsPerLine) + "\n"
}
