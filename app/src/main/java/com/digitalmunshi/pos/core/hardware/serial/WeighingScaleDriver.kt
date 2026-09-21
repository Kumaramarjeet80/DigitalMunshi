package com.digitalmunshi.pos.core.hardware.serial

import android.content.Context
import android.hardware.usb.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.regex.Pattern

data class ScaleReading(
    val weightKg: Double,
    val isStable: Boolean,
    val rawString: String
)

class WeighingScaleDriver(private val context: Context) {

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private var usbConnection: UsbDeviceConnection? = null
    private var inEndpoint: UsbEndpoint? = null
    private var readJob: Job? = null

    private val _weightFlow = MutableSharedFlow<ScaleReading>(replay = 1)
    val weightFlow: SharedFlow<ScaleReading> = _weightFlow.asSharedFlow()

    private val weightRegex = Pattern.compile("([+-]?\\s*\\d+(?:\\.\\d+)?)")

    /**
     * Finds and connects to a connected USB CDC or serial weighing scale device.
     */
    fun startScaleListener(scope: CoroutineScope): Boolean {
        val deviceList = usbManager.deviceList
        for ((_, device) in deviceList) {
            for (i in 0 until device.interfaceCount) {
                val iface = device.getInterface(i)
                // Check for CDC Data or Vendor specific serial
                if (iface.interfaceClass == UsbConstants.USB_CLASS_CDC_DATA || iface.interfaceClass == UsbConstants.USB_CLASS_VENDOR_SPEC) {
                    if (connect(device, iface)) {
                        startReadingStream(scope)
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun connect(device: UsbDevice, iface: UsbInterface): Boolean {
        if (!usbManager.hasPermission(device)) {
            return false
        }

        val connection = usbManager.openDevice(device) ?: return false
        if (!connection.claimInterface(iface, true)) {
            connection.close()
            return false
        }

        // Configure standard serial parameters: 9600 baud, 8 data bits, 1 stop bit, no parity (CDC SET_LINE_CODING)
        val lineCoding = byteArrayOf(
            0x80.toByte(), 0x25, 0x00, 0x00, // 9600 baud
            0x00,                             // 1 stop bit
            0x00,                             // parity none
            0x08                              // 8 data bits
        )
        connection.controlTransfer(0x21, 0x20, 0, 0, lineCoding, lineCoding.size, 1000)

        for (i in 0 until iface.endpointCount) {
            val endpoint = iface.getEndpoint(i)
            if (endpoint.direction == UsbConstants.USB_DIR_IN && endpoint.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                this.usbConnection = connection
                this.inEndpoint = endpoint
                return true
            }
        }

        connection.releaseInterface(iface)
        connection.close()
        return false
    }

    private fun startReadingStream(scope: CoroutineScope) {
        readJob?.cancel()
        readJob = scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(256)
            val lineBuilder = StringBuilder()

            while (isActive && usbConnection != null && inEndpoint != null) {
                val bytesRead = usbConnection!!.bulkTransfer(inEndpoint!!, buffer, buffer.size, 1000)
                if (bytesRead > 0) {
                    for (i in 0 until bytesRead) {
                        val char = buffer[i].toInt().toChar()
                        if (char == '\n' || char == '\r') {
                            val line = lineBuilder.toString().trim()
                            lineBuilder.setLength(0)
                            if (line.isNotEmpty()) {
                                parseScaleData(line)?.let { reading ->
                                    _weightFlow.emit(reading)
                                }
                            }
                        } else {
                            lineBuilder.append(char)
                        }
                    }
                }
            }
        }
    }

    /**
     * Parses scale strings like:
     * "ST,GS,+  1.450kg" -> 1.450 kg (Stable)
     * "US,GS,+  0.890kg" -> 0.890 kg (Unstable)
     * "WN01.450kg"       -> 1.450 kg
     */
    fun parseScaleData(raw: String): ScaleReading? {
        val isStable = !raw.contains("US") // 'US' indicates Unstable in Essae/Toledo
        val matcher = weightRegex.matcher(raw)
        if (matcher.find()) {
            val numStr = matcher.group(1)?.replace(" ", "") ?: return null
            val weight = numStr.toDoubleOrNull() ?: return null
            return ScaleReading(
                weightKg = weight,
                isStable = isStable,
                rawString = raw
            )
        }
        return null
    }

    fun stop() {
        readJob?.cancel()
        readJob = null
        try {
            usbConnection?.close()
        } catch (_: Exception) {}
        usbConnection = null
        inEndpoint = null
    }
}
