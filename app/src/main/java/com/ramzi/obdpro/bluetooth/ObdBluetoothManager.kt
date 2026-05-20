package com.ramzi.obdpro.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/**
 * Manages Bluetooth Classic (SPP) connections to ELM327 adapters.
 *
 * This class handles:
 * - Device discovery and paired device listing
 * - RFCOMM socket connection via SPP UUID
 * - Non-blocking, coroutine-based stream reading with timeout
 * - Thread-safe write operations
 *
 * All I/O operations are suspend functions using Dispatchers.IO,
 * resolving the Thread.sleep() blocking issue from the V1 code review.
 *
 * @param context Application context for Bluetooth and permission access
 */
class ObdBluetoothManager(private val context: Context) {

    companion object {
        private const val TAG = "ObdBluetoothManager"

        /** Standard Serial Port Profile UUID for ELM327 adapters */
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

        /** Default read timeout for ELM327 responses (ms) */
        private const val DEFAULT_READ_TIMEOUT_MS = 4000L

        /** Small delay between read polls to avoid CPU spin (ms) */
        private const val READ_POLL_DELAY_MS = 10L
    }

    // ─── Bluetooth system services ──────────────────────────────────────
    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val adapter: BluetoothAdapter? = bluetoothManager?.adapter

    // ─── Connection streams ─────────────────────────────────────────────
    private var socket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    // ─── Discovery state ────────────────────────────────────────────────
    private val _discoveredDevices = MutableStateFlow<Set<BluetoothDevice>>(emptySet())
    val discoveredDevices = _discoveredDevices.asStateFlow()

    private var isReceiverRegistered = false

    // ─── BroadcastReceiver for device discovery ─────────────────────────
    private val discoveryReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    device?.let { d ->
                        _discoveredDevices.value = _discoveredDevices.value + d
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    Log.d(TAG, "Discovery finished. Found ${_discoveredDevices.value.size} devices.")
                }
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  Permission Helpers
    // ════════════════════════════════════════════════════════════════════

    /** Check if this app has Bluetooth connect permission (API 31+) */
    fun hasConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.BLUETOOTH
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    /** Check if this app has Bluetooth scan permission (API 31+) */
    fun hasScanPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    /** Check if Bluetooth is enabled on the device */
    fun isBluetoothEnabled(): Boolean = adapter?.isEnabled == true

    // ════════════════════════════════════════════════════════════════════
    //  Device Discovery
    // ════════════════════════════════════════════════════════════════════

    /**
     * Returns a list of already-paired Bluetooth devices.
     * Must be called from a background thread or coroutine.
     */
    @SuppressLint("MissingPermission")
    fun getPairedDevices(): List<BluetoothDevice> {
        if (!hasConnectPermission()) return emptyList()
        return adapter?.bondedDevices?.toList() ?: emptyList()
    }

    /**
     * Starts Bluetooth discovery for new (unpaired) devices.
     * Results flow through [discoveredDevices] StateFlow.
     */
    @SuppressLint("MissingPermission")
    fun startDiscovery() {
        if (!hasScanPermission()) {
            Log.w(TAG, "Missing BLUETOOTH_SCAN permission. Cannot discover devices.")
            return
        }
        _discoveredDevices.value = emptySet()

        // Prevent double-registration (code review fix #5)
        if (!isReceiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            }
            context.registerReceiver(discoveryReceiver, filter)
            isReceiverRegistered = true
        }

        adapter?.startDiscovery()
        Log.d(TAG, "Started Bluetooth discovery")
    }

    /**
     * Stops Bluetooth discovery and unregisters the receiver.
     */
    @SuppressLint("MissingPermission")
    fun stopDiscovery() {
        if (hasScanPermission()) {
            adapter?.cancelDiscovery()
        }
        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(discoveryReceiver)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Receiver was already unregistered", e)
            }
            isReceiverRegistered = false
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  RFCOMM Socket Connection
    // ════════════════════════════════════════════════════════════════════

    /**
     * Connects to the specified Bluetooth device via RFCOMM (SPP).
     *
     * This is a suspend function that runs on Dispatchers.IO, never
     * blocking the calling coroutine's thread (code review fix #1).
     *
     * @param address MAC address of the target ELM327 device
     * @return true if connection was successful
     */
    @SuppressLint("MissingPermission")
    suspend fun connect(address: String): Boolean = withContext(Dispatchers.IO) {
        if (!hasConnectPermission()) {
            Log.e(TAG, "Missing BLUETOOTH_CONNECT permission")
            return@withContext false
        }

        // Cancel any ongoing discovery to speed up connection
        try { adapter?.cancelDiscovery() } catch (_: SecurityException) {}

        val device: BluetoothDevice? = adapter?.getRemoteDevice(address)
        if (device == null) {
            Log.e(TAG, "Device not found: $address")
            return@withContext false
        }

        try {
            // Close any existing connection first
            disconnect()

            // Create RFCOMM socket using SPP UUID
            socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            socket?.connect()

            inputStream = socket?.inputStream
            outputStream = socket?.outputStream

            Log.i(TAG, "Connected to ${device.name} ($address)")
            return@withContext true

        } catch (e: IOException) {
            Log.e(TAG, "Connection failed to $address", e)
            disconnect()
            return@withContext false
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception connecting to $address", e)
            disconnect()
            return@withContext false
        }
    }

    /**
     * Disconnects from the current Bluetooth device.
     * Safely closes all streams and the socket.
     */
    fun disconnect() {
        try { inputStream?.close() } catch (_: IOException) {}
        try { outputStream?.close() } catch (_: IOException) {}
        try { socket?.close() } catch (_: IOException) {}
        inputStream = null
        outputStream = null
        socket = null
        Log.d(TAG, "Disconnected")
    }

    /** Returns true if the RFCOMM socket is currently connected */
    fun isConnected(): Boolean = socket?.isConnected == true

    // ════════════════════════════════════════════════════════════════════
    //  Stream I/O (Non-blocking, coroutine-based)
    // ════════════════════════════════════════════════════════════════════

    /**
     * Writes a command string to the ELM327 adapter.
     *
     * Appends carriage return (\r) as the ELM327 command terminator.
     * Runs on Dispatchers.IO.
     *
     * @param command The AT or OBD command to send (e.g., "ATZ", "21 01")
     * @throws IOException if the stream is closed or write fails
     */
    suspend fun write(command: String) = withContext(Dispatchers.IO) {
        val stream = outputStream
            ?: throw IOException("OutputStream is null — not connected")
        try {
            stream.write("${command}\r".toByteArray(Charsets.US_ASCII))
            stream.flush()
            Log.d(TAG, "TX → $command")
        } catch (e: IOException) {
            Log.e(TAG, "Write failed for command: $command", e)
            throw e
        }
    }

    /**
     * Reads the ELM327 response until the `>` prompt character or timeout.
     *
     * Uses non-blocking suspend `delay()` instead of `Thread.sleep()`
     * (code review fix #1). Wrapped in `withTimeoutOrNull` for clean
     * coroutine cancellation support.
     *
     * @param timeoutMs Maximum time to wait for a response
     * @return The cleaned response string, or empty string on timeout
     */
    suspend fun readUntilPrompt(timeoutMs: Long = DEFAULT_READ_TIMEOUT_MS): String =
        withContext(Dispatchers.IO) {
            withTimeoutOrNull(timeoutMs) {
                val sb = StringBuilder()
                try {
                    while (true) {
                        val available = inputStream?.available() ?: 0
                        if (available > 0) {
                            val byte = inputStream?.read() ?: -1
                            if (byte == -1) break  // Stream closed
                            val char = byte.toChar()
                            if (char == '>') break  // ELM327 prompt
                            sb.append(char)
                        } else {
                            delay(READ_POLL_DELAY_MS)  // Non-blocking suspend!
                        }
                    }
                } catch (e: IOException) {
                    Log.e(TAG, "Read failed", e)
                }
                // Clean up: remove \r, collapse \n to space, trim
                sb.toString()
                    .replace("\r", "")
                    .replace("\n", " ")
                    .trim()
            } ?: ""  // Return empty on timeout
        }

    /**
     * Sends a command and reads the complete response.
     *
     * This is the primary communication primitive. All higher-level
     * operations (init sequence, polling, DTC reads) use this method.
     *
     * @param command The command to send
     * @param timeoutMs Response timeout
     * @return The response string
     */
    suspend fun sendAndRead(command: String, timeoutMs: Long = DEFAULT_READ_TIMEOUT_MS): String {
        write(command)
        return readUntilPrompt(timeoutMs)
    }

    /**
     * Cleanup method — call when the service/activity is destroyed.
     * Unregisters the broadcast receiver and disconnects.
     */
    fun cleanup() {
        stopDiscovery()
        disconnect()
    }
}
