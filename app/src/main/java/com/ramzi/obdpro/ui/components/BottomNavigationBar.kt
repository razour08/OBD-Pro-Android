package com.ramzi.obdpro.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.ramzi.obdpro.model.Screen
import com.ramzi.obdpro.ui.theme.DarkSurface
import com.ramzi.obdpro.ui.theme.NeonCyan
import com.ramzi.obdpro.ui.theme.TextDim

/**
 * Bottom navigation bar with type-safe Screen enum routing.
 */
@Composable
fun BottomNavigationBar(
    currentScreen: Screen,
    onNavigate: (Screen) -> Unit
) {
    NavigationBar(
        containerColor = DarkSurface,
        contentColor = NeonCyan
    ) {
        Screen.entries.forEach { screen ->
            val isSelected = currentScreen == screen
            NavigationBarItem(
                selected = isSelected,
                onClick = { onNavigate(screen) },
                icon = {
                    Icon(
                        imageVector = screenIcon(screen),
                        contentDescription = screen.label
                    )
                },
                label = {
                    Text(
                        text = screen.label,
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = NeonCyan,
                    selectedTextColor = NeonCyan,
                    unselectedIconColor = TextDim,
                    unselectedTextColor = TextDim,
                    indicatorColor = NeonCyan.copy(alpha = 0.12f)
                )
            )
        }
    }
}

private fun screenIcon(screen: Screen): ImageVector {
    return when (screen) {
        Screen.DASHBOARD -> Icons.Default.Dashboard
        Screen.EXPLORER  -> Icons.Default.Search
        Screen.DTC       -> Icons.Default.Warning
        Screen.CONSOLE   -> Icons.Default.Terminal
        Screen.SETTINGS  -> Icons.Default.Settings
    }
}
