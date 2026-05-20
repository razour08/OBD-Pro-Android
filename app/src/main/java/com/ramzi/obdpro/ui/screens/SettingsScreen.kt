package com.ramzi.obdpro.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ramzi.obdpro.ui.theme.*

/**
 * Settings / Info screen showing vehicle and ECU details,
 * protocol configuration, demo mode toggle, and safety information.
 */
@Composable
fun SettingsScreen(
    isDemoMode: Boolean,
    isConnected: Boolean,
    onStartDemo: () -> Unit,
    onStopDemo: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Vehicle & ECU Info",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        // ── Demo Mode Card ───────────────────────────────────────────
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isDemoMode) NeonMagenta.copy(alpha = 0.12f) else DarkSurface
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "🎮",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Demo Mode",
                            style = MaterialTheme.typography.titleLarge,
                            color = if (isDemoMode) NeonMagenta else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isDemoMode) "Simulating LEC3A ECU data"
                                   else "Test the app without Bluetooth or vehicle",
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextSecondary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                if (isDemoMode) {
                    OutlinedButton(
                        onClick = onStopDemo,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonRed)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Stop Demo Mode")
                    }
                } else {
                    Button(
                        onClick = onStartDemo,
                        enabled = !isConnected,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonMagenta,
                            disabledContainerColor = TextDim
                        )
                    ) {
                        Icon(
                            Icons.Default.PlayArrow, contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isConnected) "Disconnect first" else "Start Demo Mode",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Demo mode simulates engine warmup: RPM idle oscillation, " +
                           "coolant temp rising from 25°C → 92°C, and O₂ sensor cycling. " +
                           "All UI features work identically — no hardware needed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextDim
                )
            }
        }

        // ── Vehicle Info Card ────────────────────────────────────────
        InfoCard(
            icon = Icons.Default.DirectionsCar,
            iconColor = NeonCyan,
            title = "Jiangnan TT",
            items = listOf(
                "Engine" to "JN-368 (368cc, 3-Cyl)",
                "Protocol" to "ISO 14230-4 KWP2000",
                "Interface" to "K-Line (OBD Pin 7)",
                "Adapter" to "ELM327 v1.5 (Bluetooth SPP)",
                "Emissions" to "EOBD"
            )
        )

        // ── ECU Info Card ────────────────────────────────────────────
        InfoCard(
            icon = Icons.Default.Memory,
            iconColor = NeonAmber,
            title = "ECU: Wuhan Lingdian LEC3A",
            items = listOf(
                "Part Number" to "JNJ7082AF-3610100",
                "Hardware Code" to "0 103 022 101",
                "Serial" to "SN.DE020535",
                "Calibration" to "ENG-JN-368-S201",
                "Data Command" to "KWP Service 21 01 (64 bytes)"
            )
        )

        // ── Protocol Info ────────────────────────────────────────────
        InfoCard(
            icon = Icons.Default.Info,
            iconColor = NeonBlue,
            title = "Protocol Configuration",
            items = listOf(
                "Init Sequence" to "ATZ → ATE0 → ATS1 → ATH0 → ATSP 5 → ATFI",
                "Data Polling" to "Service 21 01 → Response 61 01",
                "DTC Read" to "Service 18 00 FF 00 → Response 58",
                "DTC Clear" to "Service 14 FF 00 → Response 54",
                "Keep-Alive" to "Service 3E every 2.5s"
            )
        )

        // ── Safety Warning ───────────────────────────────────────────
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = NeonRed.copy(alpha = 0.1f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = NeonRed,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Safety Notice",
                        style = MaterialTheme.typography.titleLarge,
                        color = NeonRed
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Service 27 (Security Access) is BLOCKED in this app. " +
                                "The seed-key algorithm for this ECU is unknown. " +
                                "Sending incorrect keys 3 times triggers a 10-minute lockout. " +
                                "Repeated blind guessing risks PERMANENT ECU lockout.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "CAN-L Defect: OBD Pin 6 (CAN-H) must be insulated with tape " +
                                "for emissions inspections to prevent K-Line interference.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary
                    )
                }
            }
        }

        // ── App Info ─────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "OBD Pro v2.0 — May 2026 — Built for Jiangnan TT",
                style = MaterialTheme.typography.labelSmall,
                color = TextDim
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun InfoCard(
    icon: ImageVector,
    iconColor: androidx.compose.ui.graphics.Color,
    title: String,
    items: List<Pair<String, String>>
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = DarkBorder)
            Spacer(modifier = Modifier.height(8.dp))

            items.forEach { (label, value) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary,
                        modifier = Modifier.weight(0.4f)
                    )
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextPrimary,
                        modifier = Modifier.weight(0.6f)
                    )
                }
            }
        }
    }
}
