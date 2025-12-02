package com.example.indoormaps.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.indoormaps.data.prediction.PredictionService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Shared ViewModel across Home and Map screens
 * Combines WiFi scanning predictions and map display
 */
class SharedLocationViewModel(application: Application) : AndroidViewModel(application) {
    
    // Expose current prediction ID for map highlighting
    private val _currentLocationId = MutableStateFlow<String?>(null)
    val currentLocationId: StateFlow<String?> = _currentLocationId.asStateFlow()
    
    /**
     * Update current location from predictions
     */
    fun updateLocation(locationId: String?) {
        _currentLocationId.value = locationId
        
        // Emit to PredictionService for map highlighting
        locationId?.let {
            PredictionService.emitPrediction(it)
        }
    }
}
