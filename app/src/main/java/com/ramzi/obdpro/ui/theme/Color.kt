package com.ramzi.obdpro.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * OBD Pro — Industrial Dark Theme Color Palette
 *
 * Designed for automotive dashboard readability:
 * - High contrast text on dark backgrounds
 * - Neon accent colors for gauges (visible in sunlight)
 * - Warning colors following automotive conventions (red = hot, blue = cold)
 */

// ═══════════════════════════════════════════════════════════════════
//  Surface & Background
// ═══════════════════════════════════════════════════════════════════
val DarkBackground    = Color(0xFF0D1117)    // Deep space black
val DarkSurface       = Color(0xFF161B22)    // Card surfaces
val DarkSurfaceAlt    = Color(0xFF1C2128)    // Elevated surfaces
val DarkBorder        = Color(0xFF30363D)    // Subtle borders

// ═══════════════════════════════════════════════════════════════════
//  Gauge Accent Colors (Neon / Industrial)
// ═══════════════════════════════════════════════════════════════════
val NeonCyan          = Color(0xFF00E5FF)    // RPM gauge
val NeonAmber         = Color(0xFFFFAB00)    // Coolant temp gauge
val NeonMagenta       = Color(0xFFE040FB)    // Speed gauge
val NeonGreen         = Color(0xFF00E676)    // Battery / healthy
val NeonRed           = Color(0xFFFF1744)    // Alerts / overheating
val NeonBlue          = Color(0xFF448AFF)    // MAP / cold temp
val NeonOrange        = Color(0xFFFF6D00)    // TPS gauge
val NeonTeal          = Color(0xFF1DE9B6)    // O2 sensor

// ═══════════════════════════════════════════════════════════════════
//  Text Colors
// ═══════════════════════════════════════════════════════════════════
val TextPrimary       = Color(0xFFF0F6FC)    // Primary text (near-white)
val TextSecondary     = Color(0xFF8B949E)    // Labels, secondary info
val TextDim           = Color(0xFF484F58)    // Disabled / placeholder

// ═══════════════════════════════════════════════════════════════════
//  Console Colors
// ═══════════════════════════════════════════════════════════════════
val ConsoleTx         = Color(0xFFBB86FC)    // TX (commands sent) — purple
val ConsoleRx         = Color(0xFF03DAC5)    // RX (responses) — teal
val ConsoleSys        = Color(0xFFFFAB00)    // System messages — amber
val ConsoleError      = Color(0xFFCF6679)    // Errors — soft red
val ConsoleBackground = Color(0xFF0A0E14)    // Terminal black

// ═══════════════════════════════════════════════════════════════════
//  Status Indicator Colors
// ═══════════════════════════════════════════════════════════════════
val StatusConnected    = Color(0xFF00E676)
val StatusConnecting   = Color(0xFFFFAB00)
val StatusDisconnected = Color(0xFFFF1744)
val StatusIdle         = Color(0xFF484F58)
