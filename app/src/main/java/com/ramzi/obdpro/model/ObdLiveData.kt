package com.ramzi.obdpro.model

/**
 * Live sensor data parsed from KWP2000 Service 21 01 response.
 *
 * The Wuhan Lingdian LEC3A ECU returns a proprietary 64-byte data block
 * with the response header `61 01`. Each field is mapped from specific
 * byte offsets using formulas verified through physical testing on a
 * Jiangnan TT (see Doc/jiangnan_tt_ecu_research.md).
 *
 * ┌──────────────────┬────────┬──────────────────────────────┬──────┬───────────┐
 * │ Sensor            │ Byte(s)│ Formula                      │ Unit │ Status    │
 * ├──────────────────┼────────┼──────────────────────────────┼──────┼───────────┤
 * │ Battery           │ 8      │ d[8] * 0.1                   │ V    │ Confirmed │
 * │ MAP               │ 9      │ d[9] / 2.0                   │ kPa  │ Confirmed │
 * │ IAT               │ 10     │ d[10] - 40                   │ °C   │ Confirmed │
 * │ Elec. Load        │ 12     │ d[12] * 100.0 / 255.0        │ %    │ Suspected │
 * │ O2 Voltage        │ 14     │ d[14] / 200.0                │ V    │ Confirmed │
 * │ Speed             │ 17     │ N/A — ECU does not report    │ km/h │ ❌ N/A    │
 * │ TPS               │ 20     │ d[20] * 100.0 / 255.0        │ %    │ Confirmed │
 * │ TPS Secondary     │ 21     │ d[21]                        │ raw  │ Suspected │
 * │ RPM               │ 23,24  │ (d[23] * 256) + d[24]        │ rpm  │ Confirmed │
 * │ Ignition Advance  │ 32     │ (d[32] - 128) * 0.5          │ °    │ Unmapped  │
 * │ Coolant           │ 35     │ d[35] - 40                   │ °C   │ Confirmed │
 * │ Fuel Enrichment   │ 38     │ d[38]                        │ flag │ Unmapped  │
 * │ Engine Runtime    │ 53     │ d[53]                        │ sec  │ Isolated  │
 * └──────────────────┴────────┴──────────────────────────────┴──────┴───────────┘
 *
 * CORRECTION (2026-05-20 Video Forensics):
 * All offsets ≥20 were shifted by -1 from the original research document.
 * Verified against 12 raw hex packets extracted from test recording.
 *
 * NOTE: Speed (byte 17) is ALWAYS 0x00 in real-world driving tests.
 * The LEC3A ECU does NOT report vehicle speed via Service 21 01.
 * The VSS signal goes directly to the instrument cluster, bypassing ECU.
 */
data class ObdLiveData(
    // ── Confirmed Sensors ────────────────────────────────────────────
    val rpm: Int = 0,
    val speed: Int? = null,  // null = ECU does not report speed
    val coolantTemp: Int = -40,
    val batteryVoltage: Float = 0f,
    val mapPressure: Float = 0f,
    val iat: Int = -40,
    val tps: Float = 0f,
    val o2Voltage: Float = 0f,

    // ── Suspected / Unmapped Sensors (for Explorer) ──────────────────
    val electricalLoad: Float = 0f,       // Byte 12 — Alternator demand
    val tpsSecondary: Int = 0,            // Byte 21 — Raw ADC, moves with TPS (was 22)
    val ignitionAdvance: Float = 0f,      // Byte 32 — Timing advance (°) (was 33)
    val fuelEnrichment: Int = 0,          // Byte 38 — WOT enrichment flag (was 39)
    val engineRuntime: Int = 0,           // Byte 53 — Counter (seconds) (was 54)

    // ── Raw Data ─────────────────────────────────────────────────────
    val rawBytes: IntArray = IntArray(0), // Full 64-byte payload for Explorer
    val rawHex: String = "",
    val timestamp: Long = System.currentTimeMillis()
) {
    // IntArray doesn't play well with data class equals/hashCode
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ObdLiveData) return false
        return rpm == other.rpm &&
                coolantTemp == other.coolantTemp &&
                batteryVoltage == other.batteryVoltage &&
                timestamp == other.timestamp
    }

    override fun hashCode(): Int {
        var result = rpm
        result = 31 * result + coolantTemp
        result = 31 * result + batteryVoltage.hashCode()
        result = 31 * result + timestamp.hashCode()
        return result
    }
}
