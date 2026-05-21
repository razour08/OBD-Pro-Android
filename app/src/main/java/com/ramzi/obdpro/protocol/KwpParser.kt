package com.ramzi.obdpro.protocol

import android.util.Log
import com.ramzi.obdpro.model.DtcCode
import com.ramzi.obdpro.model.ObdLiveData

/**
 * Parser for KWP2000 responses from the Wuhan Lingdian LEC3A ECU.
 *
 * This parser is specifically designed for the proprietary 64-byte data
 * stream returned by Service 21 01. It handles the following edge cases
 * identified during real-world testing:
 *
 * 1. **False NRC**: The ECU data bytes can contain `7F` hex patterns
 *    that look like Negative Response Codes but are actually valid data.
 *    The parser anchors to the `61 01` header to identify valid responses.
 *
 * 2. **Incomplete reads**: Bluetooth may deliver partial responses.
 *    The parser gracefully handles arrays shorter than 64 bytes by
 *    returning defaults for missing bytes.
 *
 * 3. **Random characters**: ELM327 adapters sometimes inject noise
 *    characters (line feeds, extra prompts). These are filtered out.
 *
 * ════════════════════════════════════════════════════════════════════
 *  SAFETY CONSTRAINT — SERVICE 27 PROHIBITED
 * ════════════════════════════════════════════════════════════════════
 *  This parser does NOT implement, parse, or support KWP2000 Service 27
 *  (Security Access). The seed-key algorithm for the Wuhan Lingdian ECU
 *  is unknown. Sending incorrect keys 3 times triggers a 10-minute ECU
 *  lockout, and repeated blind guessing risks PERMANENT lockout,
 *  destroying the hardware.
 *
 *  Allowed services: 21 (Read Data), 18 (Read DTCs), 14 (Clear DTCs)
 * ════════════════════════════════════════════════════════════════════
 */
object KwpParser {

    private const val TAG = "KwpParser"

    /** Positive response header for Service 21 01 */
    private const val LIVE_DATA_HEADER = "6101"

    /** Positive response header for Service 18 (DTC read) */
    private const val DTC_READ_HEADER = "58"

    /** Positive response for Service 14 (DTC clear) */
    private const val DTC_CLEAR_SUCCESS = "54"

    /** Minimum expected data bytes for a valid 21 01 response */
    private const val MIN_DATA_BYTES = 37  // Need at least up to byte 36 for coolant

    // ════════════════════════════════════════════════════════════════════
    //  Live Data Parsing (Service 21 01 → Response 61 01)
    // ════════════════════════════════════════════════════════════════════

    /**
     * Parses a raw ELM327 response string into structured live sensor data.
     *
     * @param rawResponse The complete response string from ELM327
     * @return Parsed [ObdLiveData] or null if the response is not a valid 61 01
     */
    fun parseLiveData(rawResponse: String): ObdLiveData? {
        // Step 1: Clean the response — remove spaces, newlines, non-hex chars
        val cleaned = rawResponse
            .uppercase()
            .replace(" ", "")
            .replace("\r", "")
            .replace("\n", "")

        // Step 2: Find the 61 01 header — this is the anchor point
        // (NRC Fix: ignore any 7F patterns that appear AFTER a valid 61 01 header)
        val headerIndex = cleaned.indexOf(LIVE_DATA_HEADER)
        if (headerIndex < 0) {
            Log.w(TAG, "No 61 01 header found in response: ${rawResponse.take(80)}")
            return null
        }

        // Step 3: Extract data bytes (everything after "6101")
        val dataHex = cleaned.substring(headerIndex + LIVE_DATA_HEADER.length)

        // Step 4: Convert hex string to byte array
        val dataBytes = hexStringToByteArray(dataHex)

        if (dataBytes.size < MIN_DATA_BYTES) {
            Log.w(TAG, "Incomplete response: got ${dataBytes.size} bytes, need $MIN_DATA_BYTES")
            // Parse what we can, defaulting missing values
        }

        // Step 5: Apply verified formulas — offsets corrected per
        //         video forensic analysis (2026-05-20 test recording).
        //
        //         Bytes 8-14: CORRECT (Battery, MAP, IAT, ElecLoad, O2)
        //         Bytes ≥20:  ALL SHIFTED -1 from original research doc
        //         Speed byte 17: always 0x00, ECU does not report it

        // ── Coolant Diagnostic Logging ──────────────────────────────
        // Log Byte 3 (ECT) to track the NTC resistance scaling.
        if (dataBytes.size > 3) {
            val b3 = safeGetByte(dataBytes, 3)
            val calculatedTemp = (112f - (0.438f * b3)).toInt()
            Log.i(TAG, "🌡 COOLANT DIAG (ECT): " +
                "b[3]=0x${"%02X".format(b3)}($b3)→${calculatedTemp}°C " +
                "| Total bytes: ${dataBytes.size}")
        }

        return ObdLiveData(
            // ── Confirmed Sensors ────────────────────────────────────
            batteryVoltage  = safeGetByte(dataBytes, 8) * 0.1f,
            mapPressure     = safeGetByte(dataBytes, 9) / 2.0f,
            iat             = safeGetByte(dataBytes, 10) - 40,
            o2Voltage       = safeGetByte(dataBytes, 14) / 200.0f,
            // Speed: Byte 17 is ALWAYS 0x00 — ECU does NOT report speed.
            speed           = null,
            tps             = safeGetByte(dataBytes, 20) * 100.0f / 255.0f,  // was 21
            rpm             = (safeGetByte(dataBytes, 23) * 256) + safeGetByte(dataBytes, 24),  // was 24,25
            // Coolant: Byte 3 confirmed via physical testing on Jiangnan TT (NTC thermistor).
            // Formula calibrated from raw data: 198 (cold) -> 25°C, 61 (operating) -> 85°C.
            // Linear equation: Temp = 112 - (0.438 * raw)
            coolantTemp     = (112f - (0.438f * safeGetByte(dataBytes, 3))).toInt(),

            // ── Suspected / Unmapped Sensors ─────────────────────────
            electricalLoad  = safeGetByte(dataBytes, 12) * 100.0f / 255.0f,
            tpsSecondary    = safeGetByte(dataBytes, 21),  // was 22
            ignitionAdvance = (safeGetByte(dataBytes, 32) - 64).toFloat(),  // was 33; formula: byte-64 (verified: old (byte-128)*0.5 gave -9.5° at idle)
            fuelEnrichment  = safeGetByte(dataBytes, 38),  // was 39
            engineRuntime   = safeGetByte(dataBytes, 53),  // was 54

            // ── Raw Data ─────────────────────────────────────────────
            rawBytes        = dataBytes,
            rawHex          = rawResponse.trim(),
            timestamp       = System.currentTimeMillis()
        )
    }

    // ════════════════════════════════════════════════════════════════════
    //  DTC Parsing (Service 18 → Response 58)
    // ════════════════════════════════════════════════════════════════════

    /**
     * Parses DTC response from KWP2000 Service 18 00 FF 00.
     *
     * Response format: 58 [count] [DTC_byte1 DTC_byte2 status_byte] ...
     * Each DTC entry is 3 bytes: 2 bytes DTC code + 1 byte status.
     *
     * @param rawResponse The response string from ELM327
     * @return List of parsed DTC codes
     */
    fun parseDtcResponse(rawResponse: String): List<DtcCode> {
        val cleaned = rawResponse.uppercase().replace(" ", "")

        // Find the 58 response header
        val headerIndex = cleaned.indexOf(DTC_READ_HEADER)
        if (headerIndex < 0) {
            Log.d(TAG, "No DTC data in response (no 58 header)")
            return emptyList()
        }

        // Skip the header byte "58" and the count byte
        val afterHeader = cleaned.substring(headerIndex + 2)
        if (afterHeader.length < 2) return emptyList()

        // First byte after 58 is the DTC count
        val dtcCount = afterHeader.substring(0, 2).toIntOrNull(16) ?: 0
        if (dtcCount == 0) return emptyList()

        val dtcData = afterHeader.substring(2)
        val dtcBytes = hexStringToByteArray(dtcData)

        val codes = mutableListOf<DtcCode>()
        // Each DTC is 3 bytes: [high_byte] [low_byte] [status]
        var i = 0
        while (i + 2 < dtcBytes.size && codes.size < dtcCount) {
            val byte1 = dtcBytes[i]
            val byte2 = dtcBytes[i + 1]
            val status = dtcBytes[i + 2]

            if (byte1 == 0 && byte2 == 0) {
                i += 3
                continue  // Skip empty slots
            }

            val dtcCode = decodeDtcBytes(byte1, byte2)
            codes.add(DtcCode(dtcCode, lookupDtcDescription(dtcCode), status))
            i += 3
        }

        Log.d(TAG, "Parsed ${codes.size} DTCs: ${codes.map { it.code }}")
        return codes
    }

    /**
     * Checks if a DTC clear response (Service 14) was successful.
     *
     * @param rawResponse Response from "14 FF 00" command
     * @return true if the response contains positive confirmation "54"
     */
    fun isDtcClearSuccess(rawResponse: String): Boolean {
        return rawResponse.uppercase().replace(" ", "").contains(DTC_CLEAR_SUCCESS)
    }

    // ════════════════════════════════════════════════════════════════════
    //  Response Validation
    // ════════════════════════════════════════════════════════════════════

    /**
     * Checks if a response is a Negative Response Code.
     *
     * NRC format: 7F [service_id] [error_code]
     *
     * CRITICAL: Only check for NRC at the START of the response.
     * The LEC3A ECU's 64-byte data stream can contain `7F` as valid
     * data bytes (e.g., sensor reading 0x7F = 127 decimal).
     * (This is the "False NRC" bug fix from V1)
     */
    fun isNegativeResponse(rawResponse: String): Boolean {
        val cleaned = rawResponse.uppercase().replace(" ", "").trim()

        // If the response contains a valid 61 01 header, it's NOT an NRC
        if (cleaned.contains(LIVE_DATA_HEADER)) return false

        // Only match 7F at the very beginning of the response
        return cleaned.startsWith("7F")
    }

    /**
     * Extracts the NRC error code from a negative response.
     */
    fun getNrcDescription(rawResponse: String): String {
        val cleaned = rawResponse.uppercase().replace(" ", "")
        if (cleaned.length < 6) return "Unknown error"

        val errorCode = cleaned.substring(4, 6)
        return when (errorCode) {
            "10" -> "General reject"
            "11" -> "Service not supported"
            "12" -> "Sub-function not supported"
            "13" -> "Incorrect message length"
            "21" -> "Busy — repeat request"
            "22" -> "Conditions not correct"
            "31" -> "Request out of range"
            "33" -> "Security access denied"
            "35" -> "Invalid key"
            "36" -> "Exceeded max attempts (wait 10 min)"
            "78" -> "Response pending — please wait"
            else -> "Unknown NRC: 0x$errorCode"
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  Private Helpers
    // ════════════════════════════════════════════════════════════════════

    /**
     * Converts a hex string to a byte array (as unsigned ints 0-255).
     * Handles odd-length strings by truncating the last nibble.
     * Skips any non-hex characters.
     */
    private fun hexStringToByteArray(hex: String): IntArray {
        // Filter only valid hex characters
        val filtered = hex.filter { it in '0'..'9' || it in 'A'..'F' || it in 'a'..'f' }
        val len = filtered.length / 2
        val result = IntArray(len)

        for (i in 0 until len) {
            val high = Character.digit(filtered[i * 2], 16)
            val low = Character.digit(filtered[i * 2 + 1], 16)
            if (high == -1 || low == -1) {
                result[i] = 0
            } else {
                result[i] = (high shl 4) or low
            }
        }
        return result
    }

    /**
     * Safely reads a byte from the data array, returning 0 for out-of-bounds.
     * Handles incomplete responses gracefully.
     */
    private fun safeGetByte(data: IntArray, index: Int): Int {
        return if (index in data.indices) data[index] else 0
    }

    /**
     * Decodes two raw bytes into a standard DTC code string (e.g., "P0103").
     *
     * DTC byte layout:
     * ┌──────────────────────────────────────────┐
     * │ Byte 1: [CC] [D1D1] [D2D2D2D2]          │
     * │ Byte 2: [D3D3D3D3] [D4D4D4D4]           │
     * │                                           │
     * │ CC = Category (P/C/B/U)                   │
     * │ D1 = Digit 1 (0-3)                        │
     * │ D2 = Digit 2 (0-F)                        │
     * │ D3 = Digit 3 (0-F)                        │
     * │ D4 = Digit 4 (0-F)                        │
     * └──────────────────────────────────────────┘
     */
    private fun decodeDtcBytes(byte1: Int, byte2: Int): String {
        val prefix = when ((byte1 shr 6) and 0x03) {
            0 -> "P"
            1 -> "C"
            2 -> "B"
            3 -> "U"
            else -> "P"
        }
        val digit1 = (byte1 shr 4) and 0x03
        val digit2 = byte1 and 0x0F
        return "$prefix$digit1${String.format("%X", digit2)}${String.format("%02X", byte2)}"
    }

    /**
     * Looks up a human-readable description for common DTCs.
     * In production, this should load from an external database
     * (e.g., the AndrOBD codes.properties file).
     */
    private fun lookupDtcDescription(code: String): String {
        return COMMON_DTCS[code] ?: "Manufacturer-specific DTC"
    }

    /** Subset of common DTCs for quick lookup */
    private val COMMON_DTCS = mapOf(
        "P0100" to "MAF Sensor Circuit Malfunction",
        "P0101" to "MAF Sensor Range/Performance",
        "P0102" to "MAF Sensor Low Input",
        "P0103" to "MAF Sensor High Input",
        "P0105" to "MAP Sensor Circuit Malfunction",
        "P0106" to "MAP Sensor Range/Performance",
        "P0110" to "IAT Sensor Circuit Malfunction",
        "P0115" to "ECT Sensor Circuit Malfunction",
        "P0116" to "ECT Sensor Range/Performance",
        "P0117" to "ECT Sensor Low Input",
        "P0118" to "ECT Sensor High Input",
        "P0120" to "TPS Sensor Circuit Malfunction",
        "P0121" to "TPS Sensor Range/Performance",
        "P0130" to "O2 Sensor Circuit Malfunction (B1S1)",
        "P0131" to "O2 Sensor Low Voltage (B1S1)",
        "P0132" to "O2 Sensor High Voltage (B1S1)",
        "P0133" to "O2 Sensor Slow Response (B1S1)",
        "P0134" to "O2 Sensor No Activity (B1S1)",
        "P0170" to "Fuel Trim Malfunction (B1)",
        "P0171" to "System Too Lean (B1)",
        "P0172" to "System Too Rich (B1)",
        "P0201" to "Injector Circuit Malfunction — Cyl. 1",
        "P0202" to "Injector Circuit Malfunction — Cyl. 2",
        "P0203" to "Injector Circuit Malfunction — Cyl. 3",
        "P0300" to "Random/Multiple Cylinder Misfire",
        "P0301" to "Misfire Detected — Cylinder 1",
        "P0302" to "Misfire Detected — Cylinder 2",
        "P0303" to "Misfire Detected — Cylinder 3",
        "P0335" to "CKP Sensor Circuit Malfunction",
        "P0340" to "CMP Sensor Circuit Malfunction",
        "P0420" to "Catalyst Efficiency Below Threshold (B1)",
        "P0443" to "EVAP Purge Valve Circuit Malfunction",
        "P0500" to "Vehicle Speed Sensor Malfunction",
        "P0505" to "Idle Air Control System Malfunction",
        "P0560" to "System Voltage Malfunction",
        "P0562" to "System Voltage Low",
        "P0563" to "System Voltage High",
        "P0600" to "Serial Communication Link Malfunction",
        "P0700" to "Transmission Control System Malfunction"
    )
}
