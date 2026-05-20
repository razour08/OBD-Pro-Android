package com.ramzi.obdpro.protocol

import com.ramzi.obdpro.model.ObdLiveData
import kotlin.math.sin
import kotlin.random.Random

/**
 * ECU Simulator — generates realistic fake sensor data for UI testing.
 *
 * Simulates a Jiangnan TT engine going through warmup cycles:
 * - RPM oscillates around idle (800-900 rpm) with occasional revving
 * - Coolant temperature gradually rises from 25°C to 90°C
 * - Battery voltage stays around 12.4V ± 0.3V
 * - TPS fluctuates between 0-15% at idle
 * - Speed stays 0 (simulating a parked vehicle)
 * - O2 sensor oscillates rapidly between lean/rich
 * - Electrical load varies with RPM
 * - Ignition timing adjusts with engine state
 *
 * Usage: Call [generateData] every 400ms in the polling loop.
 */
object EcuSimulator {

    private var tickCount = 0L
    private var warmupProgress = 0f  // 0.0 → 1.0 over ~2 minutes

    /**
     * Generates a simulated [ObdLiveData] snapshot.
     * Each call increments the simulation clock.
     */
    fun generateData(): ObdLiveData {
        tickCount++
        // Warmup: 0 → 1.0 over ~300 ticks (2 minutes at 400ms intervals)
        warmupProgress = (warmupProgress + 0.003f).coerceAtMost(1.0f)

        val time = tickCount * 0.4  // seconds

        // ── RPM: Idle with occasional gentle rev ────────────────────
        val baseRpm = 820 + (sin(time * 0.3) * 50).toInt()
        val revBurst = if (tickCount % 40 in 15L..25L) {
            (sin((tickCount % 40 - 15) * 0.31) * 1200).toInt().coerceAtLeast(0)
        } else 0
        val rpm = (baseRpm + revBurst).coerceIn(0, 7000)

        // ── Coolant: Cold start → warmup ────────────────────────────
        val coolant = (25 + warmupProgress * 67).toInt()  // 25°C → 92°C

        // ── Battery: Stable ~12.4V with ripple ──────────────────────
        val battery = 12.4f + (sin(time * 0.7) * 0.3f).toFloat()

        // ── MAP: Manifold pressure at idle (~35 kPa) ────────────────
        val mapPressure = 35.0f + (sin(time * 0.5) * 3f).toFloat() +
                if (revBurst > 0) 15f else 0f

        // ── TPS: Low at idle, spikes during rev ─────────────────────
        val tps = if (revBurst > 0) {
            (revBurst / 1200f * 60f).coerceIn(0f, 85f)
        } else {
            3.0f + (sin(time * 0.4) * 2f).toFloat()
        }

        // ── IAT: Stable ambient + engine heat ───────────────────────
        val iat = (32 + warmupProgress * 14).toInt()  // 32°C → 46°C

        // ── O2 Sensor: Rapid oscillation (lean/rich cycles) ─────────
        val o2 = (0.45f + (sin(time * 3.0) * 0.35f).toFloat()).coerceIn(0.05f, 0.95f)

        // ── Speed: ECU does NOT report speed via KWP2000 ──────────
        val speed: Int? = null

        // ── Electrical Load: Rises with RPM, fluctuates ─────────────
        val elecLoad = (15f + (rpm / 8000f) * 40f + (sin(time * 0.8) * 5f).toFloat())
            .coerceIn(0f, 100f)

        // ── TPS Secondary: Tracks primary TPS raw value ─────────────
        val tpsSecondary = ((tps / 100f) * 255f).toInt().coerceIn(0, 255)

        // ── Ignition Advance: Varies with RPM, warmup ───────────────
        val ignAdvance = (10f + warmupProgress * 8f + (sin(time * 0.6) * 3f).toFloat() +
                if (revBurst > 0) -5f else 0f).coerceIn(-10f, 40f)

        // ── Fuel Enrichment: Non-zero during revving (WOT) ──────────
        val fuelEnrich = if (revBurst > 200) Random.nextInt(128, 200) else 0

        // ── Engine Runtime: Simple counter ───────────────────────────
        val runtime = ((tickCount * 0.4) % 256).toInt()

        // ── Build 64-byte raw data array ────────────────────────────
        val rawData = IntArray(64)
        for (i in rawData.indices) {
            rawData[i] = Random.nextInt(0, 0x80)
        }

        // Place sensors at correct byte offsets (corrected per video forensics)
        rawData[8] = (battery / 0.1f).toInt().coerceIn(0, 255)
        rawData[9] = (mapPressure * 2.0f).toInt().coerceIn(0, 255)
        rawData[10] = (iat + 40).coerceIn(0, 255)
        rawData[12] = (elecLoad * 255f / 100f).toInt().coerceIn(0, 255)
        rawData[14] = (o2 * 200.0f).toInt().coerceIn(0, 255)
        // Byte 17: always 0 — ECU does not report speed
        rawData[17] = 0
        rawData[20] = (tps * 255.0f / 100.0f).toInt().coerceIn(0, 255)   // was 21
        rawData[21] = tpsSecondary                                         // was 22
        rawData[23] = (rpm shr 8) and 0xFF                                // was 24
        rawData[24] = rpm and 0xFF                                         // was 25
        rawData[32] = (ignAdvance + 64).toInt().coerceIn(0, 255)                // was 33; formula: val+64
        rawData[35] = (coolant + 40).coerceIn(0, 255)                     // was 36
        rawData[38] = fuelEnrich.coerceIn(0, 255)                         // was 39
        rawData[53] = runtime                                              // was 54

        // Format as "61 01 XX XX XX..."
        val hexBytes = rawData.joinToString(" ") { String.format("%02X", it) }
        val rawHex = "61 01 $hexBytes"

        return ObdLiveData(
            rpm = rpm,
            speed = speed,
            coolantTemp = coolant,
            batteryVoltage = battery,
            mapPressure = mapPressure,
            iat = iat,
            tps = tps,
            o2Voltage = o2,
            electricalLoad = elecLoad,
            tpsSecondary = tpsSecondary,
            ignitionAdvance = ignAdvance,
            fuelEnrichment = fuelEnrich,
            engineRuntime = runtime,
            rawBytes = rawData,
            rawHex = rawHex,
            timestamp = System.currentTimeMillis()
        )
    }

    /**
     * Resets the simulator state (for new demo sessions).
     */
    fun reset() {
        tickCount = 0
        warmupProgress = 0f
    }
}
