package com.ramzi.obdpro.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.ramzi.obdpro.MainActivity
import com.ramzi.obdpro.ObdApplication
import com.ramzi.obdpro.bluetooth.ObdBluetoothManager
import com.ramzi.obdpro.model.ConnectionState
import com.ramzi.obdpro.protocol.ObdCommander
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground service maintaining the Bluetooth connection to the ELM327.
 *
 * Runs as a foreground service with a persistent notification to prevent
 * Android from killing the connection during background operation.
 *
 * Handles:
 * - Connection lifecycle (connect → init → poll → disconnect)
 * - Auto-reconnection with exponential backoff (max 10 retries)
 * - Alert notifications with 30s cooldown
 * - Clean shutdown on service destruction
 */
class ObdService : Service() {

    companion object {
        private const val TAG = "ObdService"
        private const val NOTIFICATION_ID = 1
        private const val MAX_RECONNECT_ATTEMPTS = 10
        private const val ALERT_COOLDOWN_MS = 30_000L
    }

    // ─── Service binding ────────────────────────────────────────────────
    inner class ObdBinder : Binder() {
        fun getService(): ObdService = this@ObdService
    }

    private val binder = ObdBinder()

    override fun onBind(intent: Intent?): IBinder = binder

    // ─── Core components ────────────────────────────────────────────────
    lateinit var bluetoothManager: ObdBluetoothManager
        private set
    lateinit var commander: ObdCommander
        private set

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ─── State ──────────────────────────────────────────────────────────
    private val _isConnected = MutableStateFlow(false)
    val isConnected = _isConnected.asStateFlow()

    private val _isPolling = MutableStateFlow(false)
    val isPolling = _isPolling.asStateFlow()

    private var pollingJob: Job? = null
    private var reconnectJob: Job? = null
    private var lastConnectedAddress: String? = null
    private var lastAlertTimeMs = 0L

    /** Whether running in demo mode (no Bluetooth needed) */
    val isDemoMode get() = commander.isDemoMode

    // ════════════════════════════════════════════════════════════════════
    //  Service Lifecycle
    // ════════════════════════════════════════════════════════════════════

    override fun onCreate() {
        super.onCreate()
        bluetoothManager = ObdBluetoothManager(applicationContext)
        commander = ObdCommander(bluetoothManager)
        Log.d(TAG, "ObdService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Try to promote to foreground, but don't crash if BT permissions
        // aren't granted yet (e.g. emulator, first launch).
        tryStartForeground("OBD Pro — Ready")
        return START_STICKY
    }

    /**
     * Safely attempts to start as a foreground service.
     * On SDK 34+ with type connectedDevice, this requires BT permissions
     * to be granted at runtime. Falls back gracefully if not possible.
     */
    private fun tryStartForeground(text: String) {
        try {
            startForeground(NOTIFICATION_ID, createNotification(text))
        } catch (e: SecurityException) {
            // Expected on emulator or before permissions are granted.
            // The service continues as a bound service without foreground.
            Log.w(TAG, "Cannot start foreground (BT permissions not granted): ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean shutdown (code review fix #6)
        disconnect()
        serviceScope.cancel()
        Log.d(TAG, "ObdService destroyed")
    }

    // ════════════════════════════════════════════════════════════════════
    //  Connection Management
    // ════════════════════════════════════════════════════════════════════

    /**
     * Connects to the specified ELM327 adapter and runs the init sequence.
     *
     * @param address MAC address of the Bluetooth device
     * @return true if connection AND initialization succeeded
     */
    suspend fun connect(address: String): Boolean {
        // Now that BT permissions are granted, promote to foreground service
        tryStartForeground("OBD Pro — Connecting…")
        commander.log("⚙ Connecting to $address…")

        val connected = bluetoothManager.connect(address)
        if (!connected) {
            commander.log("✗ Bluetooth connection failed")
            _isConnected.value = false
            return false
        }

        _isConnected.value = true
        lastConnectedAddress = address
        updateNotification("OBD Pro — Connected")

        // Run the ELM327 + KWP2000 init sequence
        val initSuccess = commander.initSequence()
        if (!initSuccess) {
            commander.log("✗ ECU initialization failed")
            // Don't disconnect — user might want to retry init manually
        }

        return initSuccess
    }

    /**
     * Disconnects from the ELM327 and cleans up all resources.
     */
    fun disconnect() {
        stopPolling()
        reconnectJob?.cancel()
        if (!commander.isDemoMode.value) {
            bluetoothManager.disconnect()
        }
        commander.resetState()
        commander.stopDemoMode()
        _isConnected.value = false
        _isPolling.value = false
        updateNotification("OBD Pro — Disconnected")
        commander.log("⚙ Disconnected")
    }

    // ════════════════════════════════════════════════════════════════════
    //  Demo Mode Connection (no Bluetooth required)
    // ════════════════════════════════════════════════════════════════════

    /**
     * Connects in demo mode — no Bluetooth adapter needed.
     * Simulates the full ELM327 init sequence via [EcuSimulator].
     */
    suspend fun connectDemo(): Boolean {
        commander.startDemoMode()
        updateNotification("OBD Pro — Demo Mode")

        val initSuccess = commander.initSequence()
        if (initSuccess) {
            _isConnected.value = true
        }
        return initSuccess
    }

    // ════════════════════════════════════════════════════════════════════
    //  Live Data Polling
    // ════════════════════════════════════════════════════════════════════

    /**
     * Starts continuous polling of KWP2000 Service 21 01.
     * Polls every [ObdCommander.POLL_INTERVAL_MS] milliseconds.
     */
    fun startPolling() {
        if (_isPolling.value) return
        _isPolling.value = true
        updateNotification("OBD Pro — Live Data Active")

        pollingJob = serviceScope.launch {
            commander.log("⚙ Live data polling started")
            var consecutiveErrors = 0

            while (isActive && _isPolling.value) {
                try {
                    val success = commander.pollLiveData()

                    if (success) {
                        consecutiveErrors = 0
                        checkAlerts(commander.liveData.value)
                    } else {
                        consecutiveErrors++

                        if (consecutiveErrors >= 5) {
                            commander.log("⚠ 5 consecutive poll failures. Stopping.")
                            stopPolling()
                            handleDisconnect()
                            return@launch
                        }
                    }

                    delay(ObdCommander.POLL_INTERVAL_MS)

                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    commander.log("✗ Polling error: ${e.message}")
                    consecutiveErrors++
                    delay(1000)  // Longer delay after errors
                }
            }
        }
    }

    /**
     * Stops the continuous polling loop.
     */
    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
        _isPolling.value = false
        updateNotification("OBD Pro — Connected (Idle)")
        commander.log("⚙ Polling stopped")
    }

    // ════════════════════════════════════════════════════════════════════
    //  Auto-Reconnection (Code Review Fix #7)
    // ════════════════════════════════════════════════════════════════════

    /**
     * Handles disconnection with exponential backoff reconnection.
     *
     * - Starts at 5s delay, increases to max 60s
     * - Max 10 attempts before giving up
     * - Logs each attempt for debugging
     */
    private fun handleDisconnect() {
        _isPolling.value = false
        _isConnected.value = false

        reconnectJob?.cancel()
        reconnectJob = serviceScope.launch {
            var attempt = 0
            val address = lastConnectedAddress ?: return@launch

            while (isActive && attempt < MAX_RECONNECT_ATTEMPTS) {
                attempt++
                val backoffMs = (5000L * attempt).coerceAtMost(60_000L)
                commander.log("⚙ Reconnect attempt $attempt/$MAX_RECONNECT_ATTEMPTS (wait ${backoffMs / 1000}s)")

                delay(backoffMs)

                if (bluetoothManager.connect(address)) {
                    if (commander.initSequence()) {
                        commander.log("✓ Auto-reconnect successful!")
                        _isConnected.value = true
                        startPolling()
                        return@launch
                    }
                }

                bluetoothManager.disconnect()
            }

            commander.log("✗ Max reconnect attempts reached. Giving up.")
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  Alert System (Code Review Fix #9 — 30s cooldown)
    // ════════════════════════════════════════════════════════════════════

    /**
     * Checks live sensor data against safety thresholds.
     * Only fires alerts every [ALERT_COOLDOWN_MS] to prevent spam.
     */
    private fun checkAlerts(data: com.ramzi.obdpro.model.ObdLiveData) {
        val now = System.currentTimeMillis()
        if (now - lastAlertTimeMs < ALERT_COOLDOWN_MS) return

        if (data.coolantTemp > 105) {
            commander.log("🚨 ALERT: Engine overheating! Coolant: ${data.coolantTemp}°C")
            lastAlertTimeMs = now
        }

        if (data.batteryVoltage < 11.5f && data.rpm > 0) {
            commander.log("🚨 ALERT: Low battery! ${String.format("%.1f", data.batteryVoltage)}V")
            lastAlertTimeMs = now
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  Notifications
    // ════════════════════════════════════════════════════════════════════

    private fun createNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, ObdApplication.CHANNEL_SERVICE)
            .setContentTitle("OBD Pro")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        try {
            val notification = createNotification(text)
            val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
            manager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update notification", e)
        }
    }
}
