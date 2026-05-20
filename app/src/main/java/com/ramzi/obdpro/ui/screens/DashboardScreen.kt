package com.ramzi.obdpro.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ramzi.obdpro.model.ConnectionState
import com.ramzi.obdpro.model.ObdLiveData
import com.ramzi.obdpro.ui.components.CircularGauge
import com.ramzi.obdpro.ui.theme.*

/**
 * Main Dashboard screen showing live sensor gauges in a grid.
 *
 * Layout:
 * ┌─────────────────────────────────────────┐
 * │  [Status Bar] ● Connected   [Connect]   │
 * ├─────────────┬─────────────┬─────────────┤
 * │   ◉ RPM     │  ◉ SPEED    │  ◉ COOLANT  │  ← Large circular gauges
 * ├─────────────┼─────────────┼─────────────┤
 * │  ◉ BATTERY  │  ◉ MAP      │  ◉ TPS      │  ← Secondary gauges
 * ├─────────────┼─────────────┼─────────────┤
 * │  ◉ IAT      │  ◉ O2       │  [POLL BTN] │
 * └─────────────┴─────────────┴─────────────┘
 */
@Composable
fun DashboardScreen(
    liveData: ObdLiveData,
    connectionState: ConnectionState,
    isConnected: Boolean,
    isPolling: Boolean,
    onConnectClick: () -> Unit,
    onDisconnectClick: () -> Unit,
    onStartPolling: () -> Unit,
    onStopPolling: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 12.dp)
    ) {
        // ── Status Bar ──────────────────────────────────────────────
        StatusBar(
            connectionState = connectionState,
            isConnected = isConnected,
            onConnectClick = onConnectClick,
            onDisconnectClick = onDisconnectClick
        )

        Spacer(modifier = Modifier.height(8.dp))

        // ── Gauge Grid ──────────────────────────────────────────────
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            // Row 1 — Primary gauges
            item {
                GaugeCard(
                    label = "ENGINE RPM",
                    value = liveData.rpm.toFloat(),
                    maxValue = 8000f,
                    unit = "rpm",
                    color = NeonCyan,
                    warningThreshold = 6500f
                )
            }
            item {
                if (liveData.speed != null) {
                    GaugeCard(
                        label = "SPEED",
                        value = liveData.speed.toFloat(),
                        maxValue = 200f,
                        unit = "km/h",
                        color = NeonMagenta
                    )
                } else {
                    // Speed not available from LEC3A ECU via KWP2000
                    SpeedUnavailableCard()
                }
            }
            item {
                GaugeCard(
                    label = "COOLANT",
                    value = liveData.coolantTemp.toFloat(),
                    maxValue = 130f,
                    unit = "°C",
                    color = NeonOrange,
                    warningThreshold = 105f
                )
            }

            // Row 2 — Secondary gauges
            item {
                GaugeCard(
                    label = "BATTERY",
                    value = liveData.batteryVoltage,
                    maxValue = 16f,
                    unit = "V",
                    color = if (liveData.batteryVoltage < 11.5f && liveData.batteryVoltage > 0f)
                        NeonRed else NeonGreen,
                    warningThreshold = null // Custom logic via color
                )
            }
            item {
                GaugeCard(
                    label = "MAP",
                    value = liveData.mapPressure,
                    maxValue = 110f,
                    unit = "kPa",
                    color = NeonBlue
                )
            }
            item {
                GaugeCard(
                    label = "THROTTLE",
                    value = liveData.tps,
                    maxValue = 100f,
                    unit = "%",
                    color = NeonOrange
                )
            }

            // Row 3 — Tertiary gauges + control
            item {
                GaugeCard(
                    label = "IAT",
                    value = liveData.iat.toFloat(),
                    maxValue = 80f,
                    unit = "°C",
                    color = NeonBlue
                )
            }
            item {
                GaugeCard(
                    label = "O₂ SENSOR",
                    value = liveData.o2Voltage,
                    maxValue = 1.2f,
                    unit = "V",
                    color = NeonTeal
                )
            }

            // Row 4 — Discovered / Experimental sensors
            item {
                GaugeCard(
                    label = "ELEC LOAD",
                    value = liveData.electricalLoad,
                    maxValue = 100f,
                    unit = "%",
                    color = NeonAmber
                )
            }
            item {
                GaugeCard(
                    label = "IGN ADV",
                    value = liveData.ignitionAdvance,
                    maxValue = 40f,
                    unit = "°",
                    color = NeonMagenta
                )
            }
            item {
                // Polling Control Card
                PollControlCard(
                    isPolling = isPolling,
                    isConnected = isConnected,
                    onStartPolling = onStartPolling,
                    onStopPolling = onStopPolling
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════
//  Sub-components
// ════════════════════════════════════════════════════════════════════

@Composable
private fun StatusBar(
    connectionState: ConnectionState,
    isConnected: Boolean,
    onConnectClick: () -> Unit,
    onDisconnectClick: () -> Unit
) {
    val statusColor by animateColorAsState(
        targetValue = when (connectionState) {
            is ConnectionState.Connected, is ConnectionState.Polling -> StatusConnected
            is ConnectionState.Connecting, is ConnectionState.Initializing -> StatusConnecting
            is ConnectionState.Error, is ConnectionState.Failed -> NeonRed
            else -> StatusIdle
        },
        animationSpec = tween(500),
        label = "statusColor"
    )

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status dot
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )
            Spacer(modifier = Modifier.width(10.dp))

            // Status label
            Text(
                text = connectionState.label,
                style = MaterialTheme.typography.bodyLarge,
                color = statusColor,
                modifier = Modifier.weight(1f)
            )

            // Vehicle info
            Text(
                text = "JN-TT | KWP2000",
                style = MaterialTheme.typography.labelSmall,
                color = TextDim
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Connect/Disconnect button
            if (isConnected) {
                OutlinedButton(
                    onClick = onDisconnectClick,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonRed),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text("Disconnect", style = MaterialTheme.typography.labelSmall)
                }
            } else {
                Button(
                    onClick = onConnectClick,
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Icon(
                        Icons.Default.Bluetooth,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "Connect",
                        style = MaterialTheme.typography.labelSmall,
                        color = DarkBackground
                    )
                }
            }
        }
    }
}

@Composable
private fun GaugeCard(
    label: String,
    value: Float,
    maxValue: Float,
    unit: String,
    color: Color,
    warningThreshold: Float? = null
) {
    val isWarning = warningThreshold != null && value > warningThreshold
    val displayColor = if (isWarning) NeonRed else color

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isWarning) 1.dp else 0.dp,
                color = if (isWarning) NeonRed.copy(alpha = 0.5f) else Color.Transparent,
                shape = RoundedCornerShape(16.dp)
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularGauge(
                value = value,
                maxValue = maxValue,
                label = label,
                unit = unit,
                color = displayColor,
                size = 120.dp,
                strokeWidth = 8.dp
            )
        }
    }
}

@Composable
private fun PollControlCard(
    isPolling: Boolean,
    isConnected: Boolean,
    onStartPolling: () -> Unit,
    onStopPolling: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "LIVE DATA",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (isPolling) {
                Button(
                    onClick = onStopPolling,
                    colors = ButtonDefaults.buttonColors(containerColor = NeonRed),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("STOP", color = Color.White, fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = onStartPolling,
                    enabled = isConnected,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonGreen,
                        disabledContainerColor = TextDim
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "POLL",
                        color = if (isConnected) DarkBackground else TextSecondary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (isPolling) "Polling 21 01…" else if (isConnected) "Ready" else "Not connected",
                style = MaterialTheme.typography.labelSmall,
                color = TextDim,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun SpeedUnavailableCard() {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(120.dp)
                    .padding(8.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "SPEED",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextDim,
                        fontSize = 10.sp
                    )
                    Text(
                        text = "N/A",
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextDim,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "km/h",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextDim.copy(alpha = 0.5f),
                        fontSize = 10.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "ECU ≠ VSS",
                        style = MaterialTheme.typography.labelSmall,
                        color = NeonAmber.copy(alpha = 0.6f),
                        fontSize = 8.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
