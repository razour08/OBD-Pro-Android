package com.ramzi.obdpro

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.ramzi.obdpro.model.Screen
import com.ramzi.obdpro.ui.components.BottomNavigationBar
import com.ramzi.obdpro.ui.components.DeviceSelectionDialog
import com.ramzi.obdpro.ui.screens.ConsoleScreen
import com.ramzi.obdpro.ui.screens.DashboardScreen
import com.ramzi.obdpro.ui.screens.DtcScreen
import com.ramzi.obdpro.ui.screens.ExplorerScreen
import com.ramzi.obdpro.ui.screens.SettingsScreen
import com.ramzi.obdpro.ui.theme.OBDProTheme
import com.ramzi.obdpro.viewmodel.ObdViewModel
import com.ramzi.obdpro.viewmodel.ObdViewModelFactory

/**
 * Main Activity — the single entry point for OBD Pro.
 *
 * This activity:
 * 1. Requests Bluetooth permissions on launch
 * 2. Sets up the Compose UI with Material 3 dark theme
 * 3. Delegates all business logic to [ObdViewModel]
 *
 * Kept lean (~130 lines vs 632 in V1) by extracting all screens
 * and components into dedicated files.
 */
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var viewModel: ObdViewModel

    // ── Permission Launcher (Code Review Fix #3 — handle results) ───
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            Log.i(TAG, "All permissions granted")
            viewModel.refreshPairedDevices()
        } else {
            Log.w(TAG, "Some permissions denied: $permissions")
            // The app will show a warning in the device selection dialog
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize ViewModel with app context (not activity context for leaks)
        viewModel = ViewModelProvider(
            this,
            ObdViewModelFactory(applicationContext)
        )[ObdViewModel::class.java]

        // Request Bluetooth permissions
        requestBluetoothPermissions()

        setContent {
            OBDProTheme {
                MainScreen(viewModel = viewModel)
            }
        }
    }

    /**
     * Requests all necessary Bluetooth permissions based on Android version.
     */
    private fun requestBluetoothPermissions() {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            // Android 10-11
            permissions.add(Manifest.permission.BLUETOOTH)
            permissions.add(Manifest.permission.BLUETOOTH_ADMIN)
        }

        // Location for BT scanning on Android 10+
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)

        // Notifications on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Filter to only not-yet-granted permissions
        val needed = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }
}

// ════════════════════════════════════════════════════════════════════
//  Root Composable
// ════════════════════════════════════════════════════════════════════

@SuppressLint("MissingPermission")
@Composable
private fun MainScreen(viewModel: ObdViewModel) {
    // Collect all state flows
    val currentScreen by viewModel.currentScreen.collectAsState()
    val liveData by viewModel.liveData.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()
    val isPolling by viewModel.isPolling.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val dtcList by viewModel.dtcList.collectAsState()
    val pairedDevices by viewModel.pairedDevices.collectAsState()
    val isDemoMode by viewModel.isDemoMode.collectAsState()

    // Device selection dialog state
    var showDeviceDialog by remember { mutableStateOf(false) }

    Scaffold(
        bottomBar = {
            BottomNavigationBar(
                currentScreen = currentScreen,
                onNavigate = { viewModel.navigateTo(it) }
            )
        }
    ) { paddingValues ->
        // ── Screen Router (type-safe enum) ──────────────────────────
        when (currentScreen) {
            Screen.DASHBOARD -> DashboardScreen(
                liveData = liveData,
                connectionState = connectionState,
                isConnected = isConnected,
                isPolling = isPolling,
                onConnectClick = {
                    viewModel.refreshPairedDevices()
                    showDeviceDialog = true
                },
                onDisconnectClick = { viewModel.disconnect() },
                onStartPolling = { viewModel.startPolling() },
                onStopPolling = { viewModel.stopPolling() },
                modifier = Modifier.padding(paddingValues)
            )

            Screen.EXPLORER -> ExplorerScreen(
                liveData = liveData,
                isPolling = isPolling,
                modifier = Modifier.padding(paddingValues)
            )

            Screen.DTC -> DtcScreen(
                dtcList = dtcList,
                isConnected = isConnected,
                onReadDtcs = { viewModel.readDtcs() },
                onClearDtcs = { viewModel.clearDtcs() },
                modifier = Modifier.padding(paddingValues)
            )

            Screen.CONSOLE -> ConsoleScreen(
                logs = logs,
                isConnected = isConnected,
                onSendCommand = { viewModel.sendRawCommand(it) },
                modifier = Modifier.padding(paddingValues)
            )

            Screen.SETTINGS -> SettingsScreen(
                isDemoMode = isDemoMode,
                isConnected = isConnected,
                onStartDemo = {
                    viewModel.connectDemo()
                    viewModel.navigateTo(Screen.DASHBOARD)
                },
                onStopDemo = { viewModel.disconnect() },
                modifier = Modifier.padding(paddingValues)
            )
        }

        // ── Device Selection Dialog ─────────────────────────────────
        if (showDeviceDialog) {
            DeviceSelectionDialog(
                pairedDevices = pairedDevices,
                onDeviceSelected = { device ->
                    showDeviceDialog = false
                    viewModel.connect(device.address)
                },
                onDismiss = { showDeviceDialog = false }
            )
        }
    }
}
