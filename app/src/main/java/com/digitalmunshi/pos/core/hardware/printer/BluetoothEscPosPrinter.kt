package com.digitalmunshi.pos.core.hardware.printer

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.util.*

class BluetoothEscPosPrinter {

    companion object {
        // Standard Bluetooth Serial Port Profile (SPP) UUID
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private var socket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null

    @SuppressLint("MissingPermission")
    suspend fun connect(macAddress: String): Result<Unit> = withContext(Dispatchers.IO) {
        val adapter = BluetoothAdapter.getDefaultAdapter()
            ?: return@withContext Result.failure(IllegalStateException("Device does not support Bluetooth."))

        if (!adapter.isEnabled) {
            return@withContext Result.failure(IllegalStateException("Bluetooth is disabled."))
        }

        try {
            disconnect()
            val device: BluetoothDevice = adapter.getRemoteDevice(macAddress)
            val btSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            adapter.cancelDiscovery()
            btSocket.connect()
            this@BluetoothEscPosPrinter.socket = btSocket
            this@BluetoothEscPosPrinter.outputStream = btSocket.outputStream
            Result.success(Unit)
        } catch (e: Exception) {
            disconnect()
            Result.failure(e)
        }
    }

    suspend fun printBytes(bytes: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        val stream = outputStream
            ?: return@withContext Result.failure(IllegalStateException("Bluetooth printer not connected."))

        try {
            stream.write(bytes)
            stream.flush()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun disconnect() {
        try {
            outputStream?.close()
            socket?.close()
        } catch (_: Exception) {}
        outputStream = null
        socket = null
    }
}
