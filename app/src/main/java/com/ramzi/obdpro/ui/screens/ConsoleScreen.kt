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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.ramzi.obdpro.ui.theme.*

/**
 * Raw serial console screen for direct AT/OBD command interaction.
 *
 * Features:
 * - Color-coded TX/RX/SYS messages
 * - Auto-scroll to latest entry
 * - Command input with Send button (or Enter key)
 * - Safety gate blocks Service 27 commands
 */
@Composable
fun ConsoleScreen(
    logs: List<String>,
    isConnected: Boolean,
    onSendCommand: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var commandInput by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Auto-scroll to bottom when new logs arrive
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // ── Header ──────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Serial Console",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = if (isConnected) "● Connected" else "● Disconnected",
                style = MaterialTheme.typography.labelSmall,
                color = if (isConnected) StatusConnected else StatusDisconnected
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ── Console Log ─────────────────────────────────────────────
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = ConsoleBackground),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(logs) { logEntry ->
                    Text(
                        text = logEntry,
                        style = MaterialTheme.typography.bodySmall,
                        color = when {
                            logEntry.contains("TX ▸")  -> ConsoleTx
                            logEntry.contains("RX ◂")  -> ConsoleRx
                            logEntry.contains("⚙")     -> ConsoleSys
                            logEntry.contains("✗")     -> ConsoleError
                            logEntry.contains("🚨")    -> NeonRed
                            logEntry.contains("⛔")    -> NeonRed
                            logEntry.contains("✓")     -> NeonGreen
                            else -> TextSecondary
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(color = DarkBorder)
        Spacer(modifier = Modifier.height(8.dp))

        // ── Command Input ───────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = commandInput,
                onValueChange = { commandInput = it.uppercase() },
                placeholder = {
                    Text("Enter AT or OBD command…", color = TextDim)
                },
                singleLine = true,
                modifier = Modifier.weight(1f),
                textStyle = MaterialTheme.typography.bodySmall.copy(color = TextPrimary),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonCyan,
                    unfocusedBorderColor = DarkBorder,
                    cursorColor = NeonCyan,
                    focusedContainerColor = ConsoleBackground,
                    unfocusedContainerColor = ConsoleBackground
                ),
                shape = RoundedCornerShape(8.dp),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Send
                ),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (commandInput.isNotBlank() && isConnected) {
                            onSendCommand(commandInput.trim())
                            commandInput = ""
                        }
                    }
                )
            )

            Button(
                onClick = {
                    if (commandInput.isNotBlank()) {
                        onSendCommand(commandInput.trim())
                        commandInput = ""
                    }
                },
                enabled = isConnected && commandInput.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonCyan,
                    disabledContainerColor = TextDim
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    Icons.Default.Send,
                    contentDescription = "Send",
                    tint = DarkBackground
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
    }
}
