package com.digitalmunshi.pos.core.hardware.printer

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.*
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UsbEscPosPrinter(private val context: Context) {

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private var usbConnection: UsbDeviceConnection? = null
    private var bulkOutEndpoint: UsbEndpoint? = null
    private var activeDevice: UsbDevice? = null

    companion object {
        private const val ACTION_USB_PERMISSION = "com.digitalmunshi.pos.USB_PERMISSION"
    }

    /**
     * Finds and connects to the first available USB ESC/POS thermal printer.
     */
    suspend fun findAndConnectPrinter(): Boolean = withContext(Dispatchers.IO) {
        val deviceList = usbManager.deviceList
        for ((_, device) in deviceList) {
            // Check for Printer Class (Class 7) or standard vendor IDs
            for (i in 0 until device.interfaceCount) {
                val iface = device.getInterface(i)
                if (iface.interfaceClass == UsbConstants.USB_CLASS_PRINTER || device.deviceClass == UsbConstants.USB_CLASS_PRINTER) {
                    return@withContext connectToDevice(device, iface)
                }
            }
        }
        false
    }

    private fun connectToDevice(device: UsbDevice, usbInterface: UsbInterface): Boolean {
        if (!usbManager.hasPermission(device)) {
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
            val permissionIntent = PendingIntent.getBroadcast(context, 0, Intent(ACTION_USB_PERMISSION), flags)
            usbManager.requestPermission(device, permissionIntent)
            return false
        }

        val connection = usbManager.openDevice(device) ?: return false
        if (!connection.claimInterface(usbInterface, true)) {
            connection.close()
            return false
        }

        // Find bulk OUT endpoint
        for (i in 0 until usbInterface.endpointCount) {
            val endpoint = usbInterface.getEndpoint(i)
            if (endpoint.direction == UsbConstants.USB_DIR_OUT && endpoint.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                this.usbConnection = connection
                this.bulkOutEndpoint = endpoint
                this.activeDevice = device
                return true
            }
        }

        connection.releaseInterface(usbInterface)
        connection.close()
        return false
    }

    /**
     * Sends raw ESC/POS byte payload over USB bulk transfer
     */
    suspend fun printBytes(bytes: ByteArray): Result<Int> = withContext(Dispatchers.IO) {
        val connection = usbConnection
        val endpoint = bulkOutEndpoint
        if (connection == null || endpoint == null) {
            return@withContext Result.failure(IllegalStateException("No USB printer connected."))
        }

        val chunkSize = 4096
        var offset = 0
        var totalWritten = 0

        while (offset < bytes.size) {
            val length = minOf(chunkSize, bytes.size - offset)
            val chunk = bytes.copyOfRange(offset, offset + length)
            val transferred = connection.bulkTransfer(endpoint, chunk, chunk.size, 5000)
            if (transferred < 0) {
                return@withContext Result.failure(IllegalStateException("USB transfer failed with code: $transferred"))
            }
            totalWritten += transferred
            offset += length
        }

        Result.success(totalWritten)
    }

    fun disconnect() {
        try {
            usbConnection?.close()
        } catch (_: Exception) {}
        usbConnection = null
        bulkOutEndpoint = null
        activeDevice = null
    }
}
