package com.example.indoormaps.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.indoormaps.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class LocationViewModel(application: Application) : AndroidViewModel(application) {
    
    companion object {
        private const val TAG = "LocationViewModel"
    }
    
    private val wifiScanner = WifiScanner(application.applicationContext)
    
    // ENABLE local model
    private val modelInference = ModelInference(application.applicationContext)
    
    private val apiClient = ApiClient()
    
    private val _localPrediction = MutableStateFlow<PredictionResult?>(null)
    val localPrediction: StateFlow<PredictionResult?> = _localPrediction.asStateFlow()
    
    private val _remotePrediction = MutableStateFlow<PredictionResult?>(null)
    val remotePrediction: StateFlow<PredictionResult?> = _remotePrediction.asStateFlow()
    
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()
    
    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()
    
    private val _scannedAPCount = MutableStateFlow(0)
    val scannedAPCount: StateFlow<Int> = _scannedAPCount.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    private val _modelInfo = MutableStateFlow("Loading model...")
    val modelInfo: StateFlow<String> = _modelInfo.asStateFlow()
    
    init {
        Log.d(TAG, "========================================")
        Log.d(TAG, "LocationViewModel INITIALIZING")
        Log.d(TAG, "========================================")
        
        viewModelScope.launch {
            Log.d(TAG, "Starting to collect scanResults flow...")
            wifiScanner.scanResults.collect { scan ->
                val apCount = scan?.accessPoints?.size ?: 0
                Log.d(TAG, "*** FLOW COLLECTED *** Scan with $apCount APs")
                scan?.let { handleNewScan(it) }
            }
        }
        
        viewModelScope.launch {
            wifiScanner.isScanning.collect { scanning ->
                _isScanning.value = scanning
            }
        }
        
        viewModelScope.launch {
            wifiScanner.scanState.collect { state ->
                _scanState.value = state
                if (state is ScanState.Error) {
                    _errorMessage.value = state.message
                }
            }
        }
        
        // Load model info
        _modelInfo.value = modelInference.getModelInfo()
        
        // Show initial helpful message
        _errorMessage.value = "Enable WiFi and Location, grant permissions, then tap Start"
        
        Log.d(TAG, "ViewModel initialization complete")
    }
    
    private fun handleNewScan(scan: WifiScan) {
        val apCount = scan.accessPoints.size
        val scanTime = System.currentTimeMillis()
        
        Log.d(TAG, "=== NEW SCAN RECEIVED ===")
        Log.d(TAG, "Processing $apCount APs")
        
        _scannedAPCount.value = apCount
        
        if (scan.accessPoints.isEmpty()) {
            _errorMessage.value = "No WiFi networks detected"
            return
        }
        
        // Run BOTH predictions IN PARALLEL for speed
        val localJob = viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            try {
                val localResult = modelInference.predict(scan.accessPoints)
                _localPrediction.value = localResult
                val elapsed = System.currentTimeMillis() - startTime
                Log.d(TAG, "Local prediction: ${localResult?.location} (${elapsed}ms)")
            } catch (e: Exception) {
                Log.e(TAG, "Local prediction failed", e)
                _localPrediction.value = PredictionResult(
                    location = "Error",
                    confidence = 0f,
                    top3 = listOf(),
                    matchedAPs = 0,
                    totalAPs = apCount,
                    source = PredictionSource.LOCAL
                )
            }
        }
        
        val remoteJob = viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            try {
                val remoteResult = apiClient.sendScanToServer(scan.accessPoints)
                val elapsed = System.currentTimeMillis() - startTime
                
                if (remoteResult != null) {
                    _remotePrediction.value = remoteResult
                    val confidencePct = (remoteResult.confidence * 100).toInt()
                    
                    // Show both predictions in status
                    val localLoc = _localPrediction.value?.location ?: "..."
                    _errorMessage.value = "Local: $localLoc | Remote: ${remoteResult.location}"
                    
                    Log.d(TAG, "Remote prediction: ${remoteResult.location} ($confidencePct%) (${elapsed}ms)")
                } else {
                    _errorMessage.value = "API offline - using local only"
                }
            } catch (e: Exception) {
                Log.e(TAG, "API error", e)
                _errorMessage.value = "API offline - using local only"
            }
        }
        
        // Optional: Log total processing time
        viewModelScope.launch {
            localJob.join()
            remoteJob.join()
            val totalTime = System.currentTimeMillis() - scanTime
            Log.d(TAG, "Total processing time: ${totalTime}ms")
        }
    }
    
    fun startScanning() {
        if (!wifiScanner.hasPermissions()) {
            _errorMessage.value = "Please grant Location permission (tap 'Grant Permissions' button)"
            Log.e(TAG, "Cannot start scanning - permissions missing")
            return
        }
        
        _errorMessage.value = "Starting WiFi scan..."
        Log.d(TAG, "Starting WiFi scanning")
        wifiScanner.startScanning()
    }
    
    fun stopScanning() {
        Log.d(TAG, "Stopping WiFi scanning")
        wifiScanner.stopScanning()
    }
    
    fun hasPermissions(): Boolean {
        return wifiScanner.hasPermissions()
    }
    
    fun testApiConnection() {
        viewModelScope.launch {
            val isConnected = apiClient.testConnection()
            val message = if (isConnected) {
                "API connected successfully"
            } else {
                "API connection failed"
            }
            _errorMessage.value = message
            Log.d(TAG, message)
        }
    }
    
    fun clearError() {
        _errorMessage.value = null
    }
    
    override fun onCleared() {
        super.onCleared()
        wifiScanner.cleanup()
        modelInference.close()
        Log.d(TAG, "ViewModel cleared")
    }
}
