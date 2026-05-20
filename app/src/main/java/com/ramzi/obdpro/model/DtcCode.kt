package com.ramzi.obdpro.model

/**
 * Represents a Diagnostic Trouble Code parsed from KWP2000 Service 18.
 *
 * @property code   The standardized DTC code (e.g., "P0103")
 * @property description Human-readable description of the fault
 * @property status Raw status byte from the ECU (0xFF = unknown)
 */
data class DtcCode(
    val code: String,
    val description: String = "Unknown DTC",
    val status: Int = 0xFF
)
