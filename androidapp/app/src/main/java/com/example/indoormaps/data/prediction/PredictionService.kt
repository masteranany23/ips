package com.example.indoormaps.data.prediction

import kotlinx.coroutines.flow.*

/**
 * Singleton service for handling location predictions
 */
object PredictionService {
    
    private val _predictions = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 10)
    
    /**
     * Raw predictions stream
     */
    val predictions: SharedFlow<String> = _predictions.asSharedFlow()
    
    /**
     * Debounced and deduplicated predictions (300ms)
     */
    val debouncedPredictions: Flow<String> = predictions
        .debounce(300)
        .distinctUntilChanged()
    
    /**
     * Emit a new prediction (thread-safe)
     */
    fun emitPrediction(locationId: String) {
        _predictions.tryEmit(locationId)
    }
    
    /**
     * Dev mode: simulate predictions for testing
     */
    suspend fun simulateTestPredictions() {
        kotlinx.coroutines.delay(1000)
        emitPrediction("TRI01F1_ROOM_103")
        kotlinx.coroutines.delay(2000)
        emitPrediction("TRI01F1_ROOM_110")
        kotlinx.coroutines.delay(2000)
        emitPrediction("TRI01F1_ROOM_122")
        kotlinx.coroutines.delay(2000)
        emitPrediction("TRI01F1_ROOM_119")
    }
}
