package com.ramzi.obdpro.viewmodel

import android.bluetooth.BluetoothDevice
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ramzi.obdpro.model.ConnectionState
import com.ramzi.obdpro.model.DtcCode
import com.ramzi.obdpro.model.ObdLiveData
import com.ramzi.obdpro.model.Screen
import com.ramzi.obdpro.service.ObdService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel bridging the ObdService/Commander layer to the Compose UI.
 *
 * Key design decisions (from code review):
 * - All service references use safe null checks (no `!!` force-unwrap)
 * - Paired devices exposed as StateFlow (not called during composition)
 * - Navigation uses type-safe Screen enum
 * - Service operations delegated through clean methods
 *
 * @param applicationContext Needed for service binding
 */
class ObdViewModel(private val applicationContext: Context) : ViewModel() {

    companion object {
        private const val TAG = "ObdViewModel"
    }

    // ─── Service binding ────────────────────────────────────────────────
    private var obdService: ObdService? = null
    private val _isBound = MutableStateFlow(false)

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val serviceBinder = binder as ObdService.ObdBinder
            obdService = serviceBinder.getService()
            _isBound.value = true
            Log.d(TAG, "Service bound")
            refreshPairedDevices()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            obdService = null
            _isBound.value = false
            Log.d(TAG, "Service disconnected")
        }
    }

    init {
        // Bind the service only — do NOT startForegroundService yet.
        // The service promotes to foreground only when connecting (after permissions are granted).
        // This prevents SecurityException on emulators or before BT permissions are granted.
        val serviceIntent = Intent(applicationContext, ObdService::class.java)
        applicationContext.startService(serviceIntent)
        applicationContext.bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    // ════════════════════════════════════════════════════════════════════
    //  State Flows — All use safe null checks (Code Review Fix #2)
    // ════════════════════════════════════════════════════════════════════

    /** Live sensor data from the ECU */
    val liveData: StateFlow<ObdLiveData> = _isBound.flatMapLatest { bound ->
        val service = obdService
        if (bound && service != null) service.commander.liveData else flowOf(ObdLiveData())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ObdLiveData())

    /** Connection state (Idle, Connecting, Connected, etc.) */
    val connectionState: StateFlow<ConnectionState> = _isBound.flatMapLatest { bound ->
        val service = obdService
        if (bound && service != null) service.commander.connectionState
        else flowOf(ConnectionState.Idle)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ConnectionState.Idle)

    /** Whether actively polling live data */
    val isPolling: StateFlow<Boolean> = _isBound.flatMapLatest { bound ->
        val service = obdService
        if (bound && service != null) service.isPolling else flowOf(false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** Whether connected to ELM327 */
    val isConnected: StateFlow<Boolean> = _isBound.flatMapLatest { bound ->
        val service = obdService
        if (bound && service != null) service.isConnected else flowOf(false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** Console log entries */
    val logs: StateFlow<List<String>> = _isBound.flatMapLatest { bound ->
        val service = obdService
        if (bound && service != null) service.commander.logs else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** DTC list */
    val dtcList: StateFlow<List<DtcCode>> = _isBound.flatMapLatest { bound ->
        val service = obdService
        if (bound && service != null) service.commander.dtcList else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Demo mode state */
    val isDemoMode: StateFlow<Boolean> = _isBound.flatMapLatest { bound ->
        val service = obdService
        if (bound && service != null) service.isDemoMode else flowOf(false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // ─── Navigation ─────────────────────────────────────────────────────
    private val _currentScreen = MutableStateFlow(Screen.DASHBOARD)
    val currentScreen = _currentScreen.asStateFlow()

    fun navigateTo(screen: Screen) {
        _currentScreen.value = screen
    }

    // ─── Paired Devices (StateFlow, not called in composition) ─────────
    private val _pairedDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val pairedDevices = _pairedDevices.asStateFlow()

    fun refreshPairedDevices() {
        viewModelScope.launch(Dispatchers.IO) {
            val service = obdService ?: return@launch
            _pairedDevices.value = service.bluetoothManager.getPairedDevices()
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  Actions (delegated through service)
    // ════════════════════════════════════════════════════════════════════

    /** Connect to the specified Bluetooth device */
    fun connect(address: String) {
        viewModelScope.launch {
            obdService?.connect(address)
        }
    }

    /** Connect in demo mode (no Bluetooth needed) */
    fun connectDemo() {
        viewModelScope.launch {
            obdService?.connectDemo()
        }
    }

    /** Disconnect from the current device */
    fun disconnect() {
        obdService?.disconnect()
    }

    /** Start live data polling */
    fun startPolling() {
        obdService?.startPolling()
    }

    /** Stop live data polling */
    fun stopPolling() {
        obdService?.stopPolling()
    }

    /** Read DTCs from the ECU */
    fun readDtcs() {
        viewModelScope.launch {
            obdService?.commander?.readDtcs()
        }
    }

    /** Clear all DTCs */
    fun clearDtcs() {
        viewModelScope.launch {
            obdService?.commander?.clearDtcs()
        }
    }

    /** Send a raw command from the console */
    fun sendRawCommand(command: String) {
        viewModelScope.launch {
            obdService?.commander?.sendRawCommand(command)
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  Cleanup
    // ════════════════════════════════════════════════════════════════════

    override fun onCleared() {
        super.onCleared()
        try {
            applicationContext.unbindService(serviceConnection)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Service was already unbound", e)
        }
    }
}
