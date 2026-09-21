package com.digitalmunshi.pos.core.hardware.scanner

import android.view.KeyEvent

class HardwareScannerInterceptor(
    private val onBarcodeScanned: (String) -> Unit
) {

    private val barcodeBuffer = StringBuilder()
    private var lastKeyTimestamp = 0L
    private val MAX_INTER_KEY_DELAY_MS = 100L // Hardware guns type keys very quickly (<50ms)

    /**
     * Call this from Activity.dispatchKeyEvent(event).
     * Returns true if the event was consumed as part of a barcode scan.
     */
    fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastKeyTimestamp > MAX_INTER_KEY_DELAY_MS && barcodeBuffer.isNotEmpty()) {
            // Buffer timed out, reset
            barcodeBuffer.setLength(0)
        }
        lastKeyTimestamp = currentTime

        if (event.keyCode == KeyEvent.KEYCODE_ENTER) {
            val scannedBarcode = barcodeBuffer.toString().trim()
            barcodeBuffer.setLength(0)
            if (scannedBarcode.length >= 3) {
                onBarcodeScanned(scannedBarcode)
                return true
            }
            return false
        }

        val unicodeChar = event.unicodeChar
        if (unicodeChar != 0) {
            val char = unicodeChar.toChar()
            if (char.isLetterOrDigit() || char in "-_./") {
                barcodeBuffer.append(char)
                return true
            }
        }

        return false
    }
}
