package com.ramzi.obdpro.protocol

import android.util.Log
import com.ramzi.obdpro.bluetooth.ObdBluetoothManager
import com.ramzi.obdpro.model.ConnectionState
import com.ramzi.obdpro.model.DtcCode
import com.ramzi.obdpro.model.ObdLiveData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * OBD Commander — the core protocol orchestrator for the Jiangnan TT.
 *
 * This class manages:
 * 1. The ELM327 AT initialization sequence (ATZ → ATFI)
 * 2. Continuous live data polling via KWP2000 Service 21 01
 * 3. DTC read/clear via Services 18 and 14
 * 4. Tester-present keep-alive (Service 3E)
 *
 * All operations are suspend functions running on Dispatchers.IO
 * via the BluetoothManager, ensuring zero main-thread blocking.
 *
 * ════════════════════════════════════════════════════════════════════
 *  SAFETY: Service 27 (Security Access) is NEVER sent.
 *  See KwpParser.kt header for the full safety rationale.
 * ════════════════════════════════════════════════════════════════════
 *
 * @param bluetoothManager The Bluetooth SPP connection manager
 */
class ObdCommander(private val bluetoothManager: ObdBluetoothManager) {

    companion object {
        private const val TAG = "ObdCommander"

        /** How long to wait between poll cycles (ms) */
        const val POLL_INTERVAL_MS = 400L

        /** Tester present interval (ms) — send every 2.5 seconds */
        private const val TESTER_PRESENT_INTERVAL_MS = 2500L

        /** Max console log entries to keep in memory */
        private const val MAX_LOG_ENTRIES = 500
    }

    // ─── State flows ────────────────────────────────────────────────────

    private val _liveData = MutableStateFlow(ObdLiveData())
    val liveData = _liveData.asStateFlow()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connectionState = _connectionState.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs = _logs.asStateFlow()

    private val _dtcList = MutableStateFlow<List<DtcCode>>(emptyList())
    val dtcList = _dtcList.asStateFlow()

    /** Event flow for one-shot events (errors, notifications) */
    private val _events = MutableSharedFlow<String>(replay = 0)
    val events = _events.asSharedFlow()

    // ─── Demo Mode ──────────────────────────────────────────────────────
    private val _isDemoMode = MutableStateFlow(false)
    val isDemoMode = _isDemoMode.asStateFlow()

    // ════════════════════════════════════════════════════════════════════
    //  Console Logging
    // ════════════════════════════════════════════════════════════════════

    /**
     * Appends a log entry to the console log.
     * Automatically caps the list at [MAX_LOG_ENTRIES] to prevent OOM.
     */
    fun log(message: String) {
        val timestamp = java.text.SimpleDateFormat(
            "HH:mm:ss.SSS", java.util.Locale.US
        ).format(java.util.Date())

        val entry = "[$timestamp] $message"
        _logs.value = (_logs.value + entry).takeLast(MAX_LOG_ENTRIES)
        Log.d(TAG, message)
    }

    private fun logTx(command: String) = log("TX ▸ $command")
    private fun logRx(response: String) = log("RX ◂ $response")
    private fun logSys(message: String) = log("⚙ $message")

    // ════════════════════════════════════════════════════════════════════
    //  ELM327 Initialization Sequence
    // ════════════════════════════════════════════════════════════════════

    /**
     * Executes the complete ELM327 + KWP2000 initialization sequence.
     *
     * Sequence (from Doc/OBD_COMMANDS.md):
     * 1. ATZ    — Reset the ELM327 chip
     * 2. ATE0   — Echo OFF (cleaner responses)
     * 3. ATS1   — Spaces ON (human-readable hex)
     * 4. ATH0   — Headers OFF (hide protocol headers)
     * 5. ATSP 5 — Force KWP2000 Fast Init protocol (CRITICAL)
     * 6. ATFI   — Perform Fast Initialization (expect BUS INIT: OK)
     *
     * @return true if the sequence completed and bus init was successful
     */
    suspend fun initSequence(): Boolean {
        // ── DEMO MODE: Simulate init ─────────────────────────────────
        if (_isDemoMode.value) {
            return simulateInit()
        }

        _connectionState.value = ConnectionState.Initializing
        logSys("Starting ELM327 initialization sequence…")

        try {
            // ── Step 1: Reset ────────────────────────────────────────
            val resetResponse = sendCommand("ATZ", 3000)
            if (resetResponse.isEmpty()) {
                logSys("⚠ No response to ATZ. Adapter may not be powered.")
                _connectionState.value = ConnectionState.Error("No response to ATZ")
                return false
            }
            logSys("Adapter: $resetResponse")

            // ── Step 2: Echo OFF ─────────────────────────────────────
            sendCommand("ATE0")

            // ── Step 3: Spaces ON ────────────────────────────────────
            sendCommand("ATS1")

            // ── Step 4: Headers OFF ──────────────────────────────────
            sendCommand("ATH0")

            // ── Step 5: Set KWP2000 Fast Init Protocol ───────────────
            // This is CRITICAL — the Jiangnan TT only speaks KWP over K-Line
            val spResponse = sendCommand("ATSP 5")
            if (!spResponse.contains("OK")) {
                logSys("⚠ ATSP 5 failed: $spResponse")
            }

            // Small delay before bus init to let the adapter settle
            delay(200)

            // ── Step 6: Perform Fast Initialization ──────────────────
            logSys("Performing KWP2000 Fast Initialization…")
            val fiResponse = sendCommand("ATFI", 5000)

            val upperResponse = fiResponse.uppercase()
            return if (upperResponse.contains("BUS INIT: OK") ||
                       (upperResponse.contains("OK") && !upperResponse.contains("ERROR"))) {
                logSys("✓ BUS INIT successful!")
                _connectionState.value = ConnectionState.Connected
                true
            } else {
                logSys("✗ BUS INIT failed: $fiResponse")
                _connectionState.value = ConnectionState.Error("Bus init failed")
                false
            }

        } catch (e: CancellationException) {
            throw e  // Don't catch coroutine cancellation
        } catch (e: Exception) {
            logSys("✗ Init sequence error: ${e.message}")
            _connectionState.value = ConnectionState.Error(e.message ?: "Unknown error")
            return false
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  Live Data Polling (Service 21 01)
    // ════════════════════════════════════════════════════════════════════

    /**
     * Sends a single poll request (21 01) and parses the response.
     *
     * @return true if a valid response was received and parsed
     */
    suspend fun pollLiveData(): Boolean {
        // ── DEMO MODE: Use simulator ─────────────────────────────────
        if (_isDemoMode.value) {
            val data = EcuSimulator.generateData()
            _liveData.value = data
            logTx("21 01")
            logRx(data.rawHex.take(60) + "…")
            return true
        }

        try {
            val response = sendCommand("21 01", 4000)

            if (response.isEmpty() || response.contains("NO DATA") ||
                response.contains("UNABLE TO CONNECT") ||
                response.contains("ERROR")) {
                logSys("⚠ Poll returned: ${response.ifEmpty { "empty" }}")
                return false
            }

            // Check for NRC, but ONLY if it's not a false positive inside data
            if (KwpParser.isNegativeResponse(response)) {
                logSys("⚠ ECU rejected: ${KwpParser.getNrcDescription(response)}")
                return false
            }

            // Parse the 64-byte live data stream
            val data = KwpParser.parseLiveData(response)
            if (data != null) {
                _liveData.value = data
                return true
            }

            logSys("⚠ Could not parse response: ${response.take(80)}")
            return false

        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logSys("✗ Poll error: ${e.message}")
            return false
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  DTC Operations (Services 18 and 14)
    // ════════════════════════════════════════════════════════════════════

    /**
     * Reads all stored Diagnostic Trouble Codes via KWP2000 Service 18.
     *
     * Command: 18 00 FF 00
     * Expected response: 58 [count] [DTC bytes...]
     */
    suspend fun readDtcs(): List<DtcCode> {
        // ── DEMO MODE: Return fake DTCs ──────────────────────────────
        if (_isDemoMode.value) {
            logSys("Reading DTCs (Service 18) [DEMO]…")
            logTx("18 00 FF 00")
            delay(500) // Simulate ECU processing time
            val fakeDtcs = listOf(
                DtcCode("P0300", "Random/Multiple Cylinder Misfire Detected", 0x08),
                DtcCode("P0171", "System Too Lean (Bank 1)", 0x08)
            )
            logRx("58 02 03 00 08 01 71 08")
            _dtcList.value = fakeDtcs
            logSys("Found ${fakeDtcs.size} DTC(s): ${fakeDtcs.joinToString { it.code }}")
            return fakeDtcs
        }

        logSys("Reading DTCs (Service 18)…")
        val response = sendCommand("18 00 FF 00", 5000)
        val dtcs = KwpParser.parseDtcResponse(response)
        _dtcList.value = dtcs

        if (dtcs.isEmpty()) {
            logSys("✓ No DTCs stored")
        } else {
            logSys("Found ${dtcs.size} DTC(s): ${dtcs.joinToString { it.code }}")
        }

        return dtcs
    }

    /**
     * Clears all stored DTCs via KWP2000 Service 14.
     *
     * Command: 14 FF 00
     * Expected response: 54 (positive acknowledgment)
     *
     * ⚠ WARNING: This also erases freeze frame data!
     */
    suspend fun clearDtcs(): Boolean {
        // ── DEMO MODE: Fake clear ───────────────────────────────────
        if (_isDemoMode.value) {
            logSys("Clearing DTCs (Service 14) [DEMO]…")
            logTx("14 FF 00")
            delay(300)
            logRx("54")
            logSys("✓ DTCs cleared successfully [DEMO]")
            _dtcList.value = emptyList()
            return true
        }

        logSys("Clearing DTCs (Service 14)…")
        val response = sendCommand("14 FF 00", 5000)

        return if (KwpParser.isDtcClearSuccess(response)) {
            logSys("✓ DTCs cleared successfully")
            _dtcList.value = emptyList()
            true
        } else {
            logSys("✗ DTC clear failed: $response")
            false
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  Tester Present (Service 3E)
    // ════════════════════════════════════════════════════════════════════

    /**
     * Sends the KWP2000 Tester Present keep-alive signal.
     * Expected response: 7E (positive acknowledgment).
     *
     * Should be called every 2-3 seconds during idle periods
     * to prevent the ECU from timing out the diagnostic session.
     */
    suspend fun sendTesterPresent() {
        try {
            val response = sendCommand("3E", 2000)
            if (!response.uppercase().contains("7E")) {
                logSys("⚠ Tester present failed: $response")
            }
        } catch (e: Exception) {
            // Don't crash on tester present failures
            Log.w(TAG, "Tester present failed", e)
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  Raw Command (Console)
    // ════════════════════════════════════════════════════════════════════

    /**
     * Sends a raw command from the user console.
     *
     * ════════════════════════════════════════════════════════════════
     * SAFETY GATE: Reject any attempt to send Service 27 commands.
     * This prevents accidental ECU lockout.
     * ════════════════════════════════════════════════════════════════
     *
     * @param command The raw command string from the user
     * @return The response string
     */
    suspend fun sendRawCommand(command: String): String {
        val cleaned = command.uppercase().replace(" ", "")

        // ── SAFETY GATE — BLOCK SERVICE 27 ──────────────────────────
        if (cleaned.startsWith("27")) {
            val warning = "⛔ BLOCKED: Service 27 (Security Access) is prohibited. " +
                    "Sending incorrect keys can permanently lock the ECU. " +
                    "See Doc/OBD_COMMANDS.md for details."
            logSys(warning)
            return warning
        }

        // ── DEMO MODE: No real Bluetooth connection ─────────────────
        if (_isDemoMode.value) {
            logTx(command)
            val simResponse = "[DEMO] No real connection — command not sent"
            logRx(simResponse)
            return simResponse
        }

        return sendCommand(command)
    }

    // ════════════════════════════════════════════════════════════════════
    //  Core Communication
    // ════════════════════════════════════════════════════════════════════

    /**
     * Sends a command and reads the response via Bluetooth.
     * Handles TX/RX logging automatically.
     *
     * Wrapped in try-catch as a defensive safety layer — if the
     * OutputStream is unexpectedly null (e.g., connection lost mid-session),
     * the app logs the error instead of crashing.
     */
    private suspend fun sendCommand(command: String, timeoutMs: Long = 4000): String {
        logTx(command)
        return try {
            val response = bluetoothManager.sendAndRead(command, timeoutMs)
            logRx(response.ifEmpty { "(empty)" })
            response
        } catch (e: java.io.IOException) {
            val errorMsg = "⚠ Send failed: ${e.message}"
            logRx(errorMsg)
            Log.e(TAG, "sendCommand failed for '$command'", e)
            errorMsg
        }
    }

    /**
     * Resets all state flows to their initial values.
     * Called when disconnecting.
     */
    fun resetState() {
        _liveData.value = ObdLiveData()
        _connectionState.value = ConnectionState.Idle
        _dtcList.value = emptyList()
    }

    // ════════════════════════════════════════════════════════════════════
    //  Demo Mode Control
    // ════════════════════════════════════════════════════════════════════

    /**
     * Activates demo mode — uses EcuSimulator instead of real Bluetooth.
     * The UI behaves identically; all data is simulated.
     */
    fun startDemoMode() {
        _isDemoMode.value = true
        EcuSimulator.reset()
        logSys("🎮 DEMO MODE activated — using simulated ECU data")
        logSys("⚙ No Bluetooth connection needed")
    }

    /**
     * Deactivates demo mode.
     */
    fun stopDemoMode() {
        _isDemoMode.value = false
        _liveData.value = ObdLiveData()
        logSys("⚙ DEMO MODE deactivated")
    }

    /**
     * Simulates the full ELM327 initialization sequence.
     * Visible in the console for educational purposes.
     */
    private suspend fun simulateInit(): Boolean {
        _connectionState.value = ConnectionState.Initializing
        logSys("Starting ELM327 initialization [DEMO]…")

        val steps = listOf(
            "ATZ"    to "ELM327 v1.5 [SIMULATED]",
            "ATE0"   to "OK",
            "ATS1"   to "OK",
            "ATH0"   to "OK",
            "ATSP 5" to "OK",
            "ATFI"   to "BUS INIT: OK"
        )

        for ((cmd, response) in steps) {
            logTx(cmd)
            delay(200)  // Simulate serial delay
            logRx(response)
        }

        logSys("✓ BUS INIT successful! [DEMO]")
        _connectionState.value = ConnectionState.Connected
        return true
    }
}
