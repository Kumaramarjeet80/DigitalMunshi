package com.digitalmunshi.pos.core.hardware.printer

import android.graphics.*
import android.graphics.pdf.PdfDocument
import com.digitalmunshi.pos.data.local.entities.BatchEntity
import com.digitalmunshi.pos.data.local.entities.ProductEntity
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

enum class LabelGridType(
    val columns: Int,
    val rows: Int,
    val totalLabels: Int,
    val labelWidthPt: Float,
    val labelHeightPt: Float,
    val marginLeftPt: Float,
    val marginTopPt: Float,
    val horizontalGapPt: Float,
    val verticalGapPt: Float
) {
    GRID_24(3, 8, 24, 180f, 96f, 20f, 30f, 10f, 4f),
    GRID_30(3, 10, 30, 180f, 76f, 20f, 30f, 10f, 4f),
    GRID_40(4, 10, 40, 135f, 76f, 15f, 30f, 8f, 4f)
}

data class LabelItemData(
    val product: ProductEntity,
    val batch: BatchEntity?,
    val quantityToPrint: Int = 1
)

object LabelPdfGenerator {

    private val A4_WIDTH_PT = 595
    private val A4_HEIGHT_PT = 842
    private val dateFormat = SimpleDateFormat("MM/yy", Locale.US)

    /**
     * Generates a multi-page A4 PDF containing barcodes in a 24, 30, or 40 grid per page.
     * Rendered completely offline using native Android PdfDocument and ZXing.
     */
    fun generateLabelPdf(
        items: List<LabelItemData>,
        gridType: LabelGridType,
        outputStream: OutputStream
    ) {
        val pdfDocument = PdfDocument()

        // Flatten items according to quantity to print
        val flattenedLabels = mutableListOf<Pair<ProductEntity, BatchEntity?>>()
        for (item in items) {
            repeat(item.quantityToPrint) {
                flattenedLabels.add(Pair(item.product, item.batch))
            }
        }

        if (flattenedLabels.isEmpty()) {
            pdfDocument.close()
            return
        }

        val totalLabels = flattenedLabels.size
        val labelsPerPage = gridType.totalLabels
        val pageCount = (totalLabels + labelsPerPage - 1) / labelsPerPage

        var labelIndex = 0

        val textPaint = Paint().apply {
            color = Color.BLACK
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        val boldTextPaint = Paint().apply {
            color = Color.BLACK
            isAntiAlias = true
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }

        for (pageIndex in 0 until pageCount) {
            val pageInfo = PdfDocument.PageInfo.Builder(A4_WIDTH_PT, A4_HEIGHT_PT, pageIndex + 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas

            for (row in 0 until gridType.rows) {
                for (col in 0 until gridType.columns) {
                    if (labelIndex >= totalLabels) break

                    val (product, batch) = flattenedLabels[labelIndex]

                    val left = gridType.marginLeftPt + col * (gridType.labelWidthPt + gridType.horizontalGapPt)
                    val top = gridType.marginTopPt + row * (gridType.labelHeightPt + gridType.verticalGapPt)
                    val right = left + gridType.labelWidthPt
                    val bottom = top + gridType.labelHeightPt

                    drawSingleLabel(
                        canvas = canvas,
                        rect = RectF(left, top, right, bottom),
                        product = product,
                        batch = batch,
                        textPaint = textPaint,
                        boldTextPaint = boldTextPaint
                    )

                    labelIndex++
                }
            }

            pdfDocument.finishPage(page)
        }

        pdfDocument.writeTo(outputStream)
        pdfDocument.close()
    }

    private fun drawSingleLabel(
        canvas: Canvas,
        rect: RectF,
        product: ProductEntity,
        batch: BatchEntity?,
        textPaint: Paint,
        boldTextPaint: Paint
    ) {
        val centerX = rect.centerX()
        var currentY = rect.top + 10f

        // Product Name (truncated to fit)
        boldTextPaint.textSize = 8f
        val displayName = if (product.name.length > 22) product.name.take(20) + ".." else product.name
        canvas.drawText(displayName, centerX, currentY, boldTextPaint)
        currentY += 10f

        // Barcode Image
        val barcodeBitmap = generateCode128Bitmap(product.barcode, (rect.width() * 0.85f).toInt(), 24)
        if (barcodeBitmap != null) {
            val bmpLeft = centerX - (barcodeBitmap.width / 2f)
            canvas.drawBitmap(barcodeBitmap, bmpLeft, currentY, null)
            currentY += barcodeBitmap.height + 8f
        }

        // Barcode number text
        textPaint.textSize = 7f
        canvas.drawText(product.barcode, centerX, currentY, textPaint)
        currentY += 9f

        // Price and Expiry info
        boldTextPaint.textSize = 8f
        val priceText = "MRP: Rs. ${String.format(Locale.US, "%.2f", product.retailPrice)}"
        val expText = if (batch != null) "Exp: ${dateFormat.format(Date(batch.expiryDate))}" else ""
        val batchText = if (batch != null) "B:${batch.batchNo}" else ""

        canvas.drawText("$priceText $batchText $expText".trim(), centerX, currentY, boldTextPaint)
    }

    private fun generateCode128Bitmap(content: String, width: Int, height: Int): Bitmap? {
        return try {
            val bitMatrix = MultiFormatWriter().encode(content, BarcodeFormat.CODE_128, width, height)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE)
                }
            }
            bitmap
        } catch (_: Exception) {
            null
        }
    }
}
