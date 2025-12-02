package com.example.indoormaps.ui.map

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.indoormaps.data.map.FloorMap
import com.example.indoormaps.data.map.LocationNode
import com.example.indoormaps.data.map.MapRepository
import com.example.indoormaps.data.prediction.PredictionService
import com.example.indoormaps.ui.LocationViewModel
import com.example.indoormaps.ui.LocationViewModelProvider
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel for floor map display with integrated WiFi scanning
 */
class MapViewModel(application: Application) : AndroidViewModel(application) {
    
    companion object {
        private const val TAG = "MapViewModel"
    }
    
    private val repository = MapRepository(application)
    
    // Use shared singleton instance (same as Home screen)
    private val locationViewModel = LocationViewModelProvider.getInstance(application)
    
    // State
    private val _currentMap = MutableStateFlow<FloorMap?>(null)
    val currentMap: StateFlow<FloorMap?> = _currentMap.asStateFlow()
    
    private val _activeLocationId = MutableStateFlow<String?>(null)
    val activeLocationId: StateFlow<String?> = _activeLocationId.asStateFlow()
    
    private val _activeNode = MutableStateFlow<LocationNode?>(null)
    val activeNode: StateFlow<LocationNode?> = _activeNode.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    // Prediction source toggle (true = remote, false = local)
    private val _useRemotePrediction = MutableStateFlow(true)
    val useRemotePrediction: StateFlow<Boolean> = _useRemotePrediction.asStateFlow()
    
    // WiFi scanning state (pass-through from LocationViewModel)
    val isScanning: StateFlow<Boolean> = locationViewModel.isScanning
    val scannedAPCount: StateFlow<Int> = locationViewModel.scannedAPCount
    
    init {
        loadMaps()
        observePredictions()
    }
    
    /**
     * Load all maps from assets
     */
    private fun loadMaps() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.loadMaps()
                Log.d(TAG, "Maps loaded successfully")
                
                // Auto-select first map
                val maps = repository.allMaps()
                if (maps.isNotEmpty()) {
                    _currentMap.value = maps.first()
                    Log.d(TAG, "Auto-selected map: ${maps.first().buildingId}-${maps.first().floorId}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load maps", e)
                _errorMessage.value = "Failed to load maps: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Observe predictions from LocationViewModel and map to nodes
     */
    private fun observePredictions() {
        viewModelScope.launch {
            // Observe both local and remote predictions
            combine(
                locationViewModel.localPrediction,
                locationViewModel.remotePrediction,
                _useRemotePrediction
            ) { local, remote, useRemote ->
                Triple(local, remote, useRemote)
            }.collect { (local, remote, useRemote) ->
                val prediction = if (useRemote) remote else local
                
                prediction?.location?.let { locationId ->
                    Log.d(TAG, "Received prediction: $locationId (source: ${if (useRemote) "remote" else "local"})")
                    mapPredictionToNode(locationId)
                }
            }
        }
    }
    
    /**
     * Map prediction location ID to actual map node
     * This function handles the manual mapping between prediction IDs and map node IDs
     */
    private fun mapPredictionToNode(predictionId: String) {
        Log.d(TAG, "Mapping prediction ID: $predictionId")
        
        // Manual mapping table between prediction IDs and map node IDs
        val mappedNodeId = when (predictionId.lowercase()) {
            // Map prediction IDs to actual node IDs in tri01_f1.json
            "p1405" -> "TRI01F1_ROOM_103"
            "p1407" -> "TRI01F1_ROOM_104"
            "messdh" -> "TRI01F1_ROOM_105"
            "mini118" -> "TRI01F1_ROOM_106"
            "mini122" -> "TRI01F1_ROOM_122"
            "oatfront1" -> "TRI01F1_OAT"
            
            // If already matches a node ID pattern, try direct search
            else -> {
                // Try to find by suffix or contains match
                val currentMapNodes = _currentMap.value?.nodes ?: emptyList()
                currentMapNodes.find { 
                    it.id.contains(predictionId, ignoreCase = true) ||
                    it.label.contains(predictionId, ignoreCase = true)
                }?.id
            }
        }
        
        if (mappedNodeId != null) {
            Log.d(TAG, "Mapped $predictionId -> $mappedNodeId")
            handlePrediction(mappedNodeId)
        } else {
            Log.w(TAG, "No mapping found for prediction: $predictionId")
            _errorMessage.value = "Location not found: $predictionId (add mapping in MapViewModel)"
        }
    }
    
    /**
     * Handle incoming prediction with mapped node ID
     */
    private fun handlePrediction(nodeId: String) {
        val result = repository.findMapForLocation(nodeId)
        
        if (result != null) {
            val (map, node) = result
            
            // Switch map if different
            if (_currentMap.value?.buildingId != map.buildingId || 
                _currentMap.value?.floorId != map.floorId) {
                _currentMap.value = map
                Log.d(TAG, "Switched to map: ${map.buildingId}-${map.floorId}")
            }
            
            // Update active node
            _activeLocationId.value = nodeId
            _activeNode.value = node
            _errorMessage.value = null
            
            Log.d(TAG, "Active node: ${node.label}")
        } else {
            _activeLocationId.value = null
            _activeNode.value = null
            _errorMessage.value = "Location not found on map: $nodeId"
            Log.w(TAG, "Location not found: $nodeId")
        }
    }
    
    /**
     * Toggle between remote and local predictions
     */
    fun togglePredictionSource() {
        _useRemotePrediction.value = !_useRemotePrediction.value
        Log.d(TAG, "Prediction source: ${if (_useRemotePrediction.value) "Remote" else "Local"}")
    }
    
    /**
     * Get LocationViewModel for WiFi control
     */
    fun getLocationViewModel(): LocationViewModel = locationViewModel
    
    /**
     * Manually select a map
     */
    fun selectMap(buildingId: String, floorId: String) {
        val map = repository.getFloorMap(buildingId, floorId)
        if (map != null) {
            _currentMap.value = map
            _activeLocationId.value = null
            _activeNode.value = null
            Log.d(TAG, "Manually selected map: $buildingId-$floorId")
        }
    }
    
    /**
     * Clear error message
     */
    fun clearError() {
        _errorMessage.value = null
    }
    
    override fun onCleared() {
        super.onCleared()
        // Don't clean up locationViewModel - it's shared across screens
        Log.d(TAG, "MapViewModel cleared")
    }
}
