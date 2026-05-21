package com.ramzi.obdpro.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ramzi.obdpro.model.ObdLiveData
import com.ramzi.obdpro.ui.theme.*

/**
 * Byte Explorer screen — shows all 64 bytes of the ECU data stream in real-time.
 *
 * Each byte cell is color-coded:
 * - 🟢 Green  = Confirmed sensor (verified formula)
 * - 🟡 Amber  = Suspected sensor (needs testing)
 * - 🔵 Blue   = Unmapped (changes observed during tests)
 * - ⚫ Gray   = Static / Unknown (no change observed)
 *
 * This screen is designed for reverse-engineering the remaining unknown
 * bytes in the LEC3A ECU's 64-byte data stream.
 */

/** Classification of each byte in the 64-byte data stream */
enum class ByteStatus(val label: String, val color: Color) {
    CONFIRMED("Confirmed", NeonGreen),
    SUSPECTED("Suspected", NeonAmber),
    UNMAPPED("Unmapped", NeonBlue),
    STATIC("Static", TextDim)
}

/** Metadata for each byte position */
data class ByteInfo(
    val index: Int,
    val name: String,
    val status: ByteStatus,
    val formula: String = ""
)

/** Complete byte map — corrected per video forensic analysis (2026-05-20)
 *  Bytes 8-14: correct as-is.
 *  Bytes >=20: ALL shifted -1 from original research document. */
private val BYTE_MAP: Map<Int, ByteInfo> = mapOf(
    3  to ByteInfo(3,  "Coolant",      ByteStatus.CONFIRMED, "112-0.438×X °C"),
    8  to ByteInfo(8,  "Battery V",    ByteStatus.CONFIRMED, "×0.1 V"),
    9  to ByteInfo(9,  "MAP",          ByteStatus.CONFIRMED, "÷2.0 kPa"),
    10 to ByteInfo(10, "IAT",          ByteStatus.CONFIRMED, "-40 °C"),
    11 to ByteInfo(11, "Unknown",      ByteStatus.UNMAPPED),
    12 to ByteInfo(12, "Elec Load",    ByteStatus.SUSPECTED, "×100/255 %"),
    14 to ByteInfo(14, "O₂ Sensor",    ByteStatus.CONFIRMED, "÷200 V"),
    // Byte 17: ALWAYS 0x00 — ECU does NOT report speed
    17 to ByteInfo(17, "Speed ✗",      ByteStatus.STATIC,    "N/A"),
    20 to ByteInfo(20, "TPS",          ByteStatus.CONFIRMED, "×100/255 %"),      // was 21
    21 to ByteInfo(21, "TPS-2 ADC",    ByteStatus.SUSPECTED, "raw"),             // was 22
    23 to ByteInfo(23, "RPM High",     ByteStatus.CONFIRMED, "×256"),            // was 24
    24 to ByteInfo(24, "RPM Low",      ByteStatus.CONFIRMED, "+byte23"),         // was 25
    32 to ByteInfo(32, "Ign. Advance", ByteStatus.SUSPECTED,  "x-64 °"),          // was 33
    35 to ByteInfo(35, "Static 0x93",   ByteStatus.STATIC,    "107 °C"),          // was 36
    38 to ByteInfo(38, "Fuel Enrich",  ByteStatus.UNMAPPED,  "WOT flag"),        // was 39
    53 to ByteInfo(53, "Runtime",      ByteStatus.UNMAPPED,  "counter")          // was 54
)

@Composable
fun ExplorerScreen(
    liveData: ObdLiveData,
    isPolling: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(12.dp)
    ) {
        // ── Header ───────────────────────────────────────────────────
        Text(
            text = "Byte Explorer",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "64-byte LEC3A data stream • Service 21 01",
            style = MaterialTheme.typography.bodySmall,
            color = TextDim
        )

        Spacer(modifier = Modifier.height(8.dp))

        // ── Legend ───────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ByteStatus.entries.forEach { status ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(status.color)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = status.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        fontSize = 10.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ── New Sensor Cards Row ─────────────────────────────────────
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "🔬 DISCOVERED SENSORS",
                    style = MaterialTheme.typography.labelMedium,
                    color = NeonAmber,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    SensorMiniCard("Elec Load", String.format("%.1f%%", liveData.electricalLoad), ByteStatus.SUSPECTED)
                    SensorMiniCard("TPS-2", "${liveData.tpsSecondary}", ByteStatus.SUSPECTED)
                    SensorMiniCard("Ign Adv", String.format("%.1f°", liveData.ignitionAdvance), ByteStatus.UNMAPPED)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    SensorMiniCard("Fuel Enr", "${liveData.fuelEnrichment}", ByteStatus.UNMAPPED)
                    SensorMiniCard("Runtime", "${liveData.engineRuntime}s", ByteStatus.UNMAPPED)
                    SensorMiniCard("Coolant", "${liveData.coolantTemp}°C", ByteStatus.CONFIRMED)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ── 64-Byte Grid ─────────────────────────────────────────────
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = if (isPolling) "⚡ LIVE" else "⏸ PAUSED",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isPolling) NeonGreen else TextDim,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                // Grid: 8 columns × 8 rows = 64 bytes
                LazyVerticalGrid(
                    columns = GridCells.Fixed(8),
                    contentPadding = PaddingValues(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(64) { index ->
                        val byteValue = if (index < liveData.rawBytes.size) {
                            liveData.rawBytes[index]
                        } else 0

                        val info = BYTE_MAP[index]
                        val status = info?.status ?: ByteStatus.STATIC
                        val borderColor = status.color

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(borderColor.copy(alpha = 0.08f))
                                .border(
                                    width = if (info != null) 1.dp else 0.5.dp,
                                    color = if (info != null) borderColor.copy(alpha = 0.5f)
                                    else DarkBorder,
                                    shape = RoundedCornerShape(4.dp)
                                )
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Byte index (tiny, top)
                                Text(
                                    text = "$index",
                                    fontSize = 7.sp,
                                    color = borderColor.copy(alpha = 0.7f),
                                    fontWeight = FontWeight.Light,
                                    lineHeight = 8.sp
                                )
                                // Hex value (main)
                                Text(
                                    text = String.format("%02X", byteValue),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (info != null) borderColor else TextDim,
                                    lineHeight = 14.sp
                                )
                                // Decimal value (tiny, bottom)
                                Text(
                                    text = "$byteValue",
                                    fontSize = 7.sp,
                                    color = TextDim,
                                    lineHeight = 8.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SensorMiniCard(
    label: String,
    value: String,
    status: ByteStatus
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(status.color.copy(alpha = 0.08f))
            .border(0.5.dp, status.color.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = status.color,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            fontSize = 9.sp
        )
    }
}
