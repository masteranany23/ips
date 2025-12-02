package com.example.indoormaps.data

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class WifiScanner(private val context: Context) {
    
    companion object {
        private const val TAG = "WifiScanner"
        // Optimized scan intervals
        private const val SCAN_INTERVAL_FAST = 3000L  // 3 seconds for fast updates
        private const val SCAN_INTERVAL_THROTTLED = 5000L  // 5 seconds when throttled
    }
    
    private val wifiManager = context.applicationContext
        .getSystemService(Context.WIFI_SERVICE) as WifiManager
    
    private val _scanResults = MutableStateFlow<WifiScan?>(null)
    val scanResults: StateFlow<WifiScan?> = _scanResults.asStateFlow()
    
    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()
    
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()
    
    private val wifiScanReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val success = intent?.getBooleanExtra(
                WifiManager.EXTRA_RESULTS_UPDATED, 
                false
            ) ?: false
            
            if (success) {
                handleScanSuccess()
            } else {
                handleScanFailure()
            }
        }
    }
    
    private var isReceiverRegistered = false
    
    private var scanHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var nextScanRunnable: Runnable? = null
    
    private var currentScanInterval = SCAN_INTERVAL_FAST
    private var consecutiveThrottles = 0
    
    fun hasPermissions(): Boolean {
        val fineLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        
        val wifiState = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_WIFI_STATE
        ) == PackageManager.PERMISSION_GRANTED
        
        return fineLocation && wifiState
    }
    
    fun startScanning() {
        if (!hasPermissions()) {
            _scanState.value = ScanState.Error("Permissions not granted")
            Log.e(TAG, "Missing required permissions")
            return
        }
        
        if (!wifiManager.isWifiEnabled) {
            _scanState.value = ScanState.Error("WiFi is disabled - please enable WiFi")
            Log.e(TAG, "WiFi is not enabled")
            return
        }
        
        if (!isReceiverRegistered) {
            val intentFilter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(
                    wifiScanReceiver, 
                    intentFilter, 
                    Context.RECEIVER_NOT_EXPORTED
                )
            } else {
                context.registerReceiver(wifiScanReceiver, intentFilter)
            }
            isReceiverRegistered = true
            Log.d(TAG, "WiFi scan receiver registered")
        }
        
        _isScanning.value = true
        _scanState.value = ScanState.Scanning
        
        // IMMEDIATELY get cached results first
        Log.d(TAG, "Getting cached WiFi scan results first...")
        getCachedResults()
        
        // Schedule first scan attempt after 1 second
        scheduleNextScan(1000)
    }
    
    fun stopScanning() {
        _isScanning.value = false
        _scanState.value = ScanState.Idle
        
        // Cancel pending scans
        nextScanRunnable?.let { scanHandler.removeCallbacks(it) }
        nextScanRunnable = null
        
        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(wifiScanReceiver)
                isReceiverRegistered = false
                Log.d(TAG, "WiFi scan receiver unregistered")
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Receiver was not registered", e)
            }
        }
    }
    
    private fun scheduleNextScan(delayMs: Long) {
        // Cancel any pending scan
        nextScanRunnable?.let { scanHandler.removeCallbacks(it) }
        
        // Schedule new scan
        nextScanRunnable = Runnable {
            if (_isScanning.value) {
                requestScan()
            }
        }
        scanHandler.postDelayed(nextScanRunnable!!, delayMs)
    }
    
    private fun requestScan() {
        try {
            if (!hasPermissions()) {
                Log.e(TAG, "Cannot scan - permissions not granted")
                _scanState.value = ScanState.Error("Permissions required")
                return
            }
            
            val success = wifiManager.startScan()
            if (!success) {
                consecutiveThrottles++
                Log.w(TAG, "Scan throttled (${consecutiveThrottles}x) - using cached results")
                
                // Adapt scan interval based on throttling
                if (consecutiveThrottles > 2) {
                    currentScanInterval = SCAN_INTERVAL_THROTTLED
                    Log.d(TAG, "Switching to slower interval (${currentScanInterval}ms)")
                }
                
                getCachedResults()
            } else {
                consecutiveThrottles = 0
                currentScanInterval = SCAN_INTERVAL_FAST
                Log.d(TAG, "Scan requested successfully")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException during scan", e)
            _scanState.value = ScanState.Error("Permission denied")
            getCachedResults()
        }
    }
    
    private fun getCachedResults() {
        try {
            if (!hasPermissions()) {
                Log.e(TAG, "Cannot access scan results")
                return
            }
            
            val results: List<ScanResult> = wifiManager.scanResults
            
            if (results.isEmpty()) {
                Log.w(TAG, "No cached results")
                scheduleNextScan(currentScanInterval)
                return
            }
            
            processResults(results)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting cached results", e)
        }
    }
    
    private fun handleScanSuccess() {
        try {
            if (!hasPermissions()) {
                Log.e(TAG, "Cannot access scan results")
                return
            }
            
            val results: List<ScanResult> = wifiManager.scanResults
            Log.d(TAG, "Scan callback - Got ${results.size} WiFi results")
            
            if (results.isNotEmpty()) {
                processResults(results)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in handleScanSuccess", e)
        }
    }
    
    private fun handleScanFailure() {
        Log.w(TAG, "WiFi scan callback reported failure")
        // Don't do anything - we already have cached results
        // Just schedule next scan
        if (_isScanning.value) {
            scheduleNextScan(5000)
        }
    }
    
    private fun processResults(results: List<ScanResult>) {
        val accessPoints = results.map { result ->
            AccessPoint(
                bssid = normalizeBssid(result.BSSID),
                ssid = result.SSID.takeIf { it.isNotEmpty() } ?: "Hidden Network",
                rssi = result.level,
                frequency = result.frequency
            )
        }
        
        val wifiScan = WifiScan(
            timestamp = System.currentTimeMillis(),
            accessPoints = accessPoints
        )
        
        _scanResults.value = wifiScan
        _scanState.value = ScanState.Scanning
        
        Log.d(TAG, "✓ Scan processed: ${accessPoints.size} APs (interval: ${currentScanInterval}ms)")
        
        // Use adaptive interval
        if (_isScanning.value) {
            scheduleNextScan(currentScanInterval)
        }
    }
    
    private fun normalizeBssid(bssid: String): String {
        return bssid.lowercase().trim()
    }
    
    fun getCurrentResults(): WifiScan? {
        return _scanResults.value
    }
    
    fun cleanup() {
        stopScanning()
        nextScanRunnable?.let { scanHandler.removeCallbacks(it) }
    }
}