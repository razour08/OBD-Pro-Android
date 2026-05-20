package com.ramzi.obdpro.model

/**
 * Sealed class representing all possible connection states.
 * Provides exhaustive when-matching for UI state rendering.
 */
sealed class ConnectionState(val label: String) {
    data object Idle : ConnectionState("Disconnected")
    data object Connecting : ConnectionState("Connecting…")
    data object Initializing : ConnectionState("Initializing ECU…")
    data object Connected : ConnectionState("Connected")
    data object Polling : ConnectionState("Live Data Active")
    data object Disconnected : ConnectionState("Disconnected")
    data class Error(val message: String) : ConnectionState("Error: $message")
    data object Failed : ConnectionState("Connection Failed")
}
