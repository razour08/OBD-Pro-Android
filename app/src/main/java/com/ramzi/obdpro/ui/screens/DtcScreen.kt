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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ramzi.obdpro.model.DtcCode
import com.ramzi.obdpro.ui.theme.*

/**
 * DTC (Diagnostic Trouble Code) screen.
 * Reads and clears DTCs using KWP2000 Services 18 and 14.
 */
@Composable
fun DtcScreen(
    dtcList: List<DtcCode>,
    isConnected: Boolean,
    onReadDtcs: () -> Unit,
    onClearDtcs: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showClearDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp)
    ) {
        // ── Header ──────────────────────────────────────────────────
        Text(
            text = "Diagnostic Trouble Codes",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "KWP2000 Service 18 — Read / Service 14 — Clear",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(16.dp))

        // ── Action Buttons ──────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onReadDtcs,
                enabled = isConnected,
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Read DTCs", color = DarkBackground)
            }

            OutlinedButton(
                onClick = { showClearDialog = true },
                enabled = isConnected && dtcList.isNotEmpty(),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp),
                    tint = if (dtcList.isNotEmpty()) NeonRed else TextDim)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Clear DTCs", color = if (dtcList.isNotEmpty()) NeonRed else TextDim)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = DarkBorder)
        Spacer(modifier = Modifier.height(12.dp))

        // ── DTC List ────────────────────────────────────────────────
        if (dtcList.isEmpty()) {
            // Empty state
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = NeonGreen.copy(alpha = 0.4f),
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "No DTCs Found",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Tap \"Read DTCs\" to scan the ECU",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextDim
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(dtcList) { dtc ->
                    DtcCard(dtc)
                }
            }
        }
    }

    // ── Clear Confirmation Dialog ────────────────────────────────────
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            containerColor = DarkSurface,
            icon = {
                Icon(Icons.Default.Warning, contentDescription = null, tint = NeonAmber)
            },
            title = {
                Text("Clear All DTCs?", color = MaterialTheme.colorScheme.onSurface)
            },
            text = {
                Text(
                    "This will erase ALL stored diagnostic trouble codes and freeze frame data. " +
                    "This action cannot be undone.\n\n" +
                    "Command: 14 FF 00",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearDialog = false
                        onClearDtcs()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonRed)
                ) {
                    Text("Clear", color = DarkBackground)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun DtcCard(dtc: DtcCode) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = NeonAmber,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = dtc.code,
                    style = MaterialTheme.typography.titleLarge,
                    color = NeonAmber
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = dtc.description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary
                )
            }
            // Status badge
            Text(
                text = String.format("0x%02X", dtc.status),
                style = MaterialTheme.typography.bodySmall,
                color = TextDim
            )
        }
    }
}
