package com.ramzi.obdpro.model

/**
 * Type-safe screen navigation enum.
 * Eliminates magic strings for navigation (code review item #12).
 */
enum class Screen(val route: String, val label: String, val icon: String) {
    DASHBOARD("dashboard", "Dashboard", "dashboard"),
    EXPLORER("explorer", "Explorer", "search"),
    DTC("dtc", "DTC", "warning"),
    CONSOLE("console", "Console", "terminal"),
    SETTINGS("settings", "Settings", "settings")
}
