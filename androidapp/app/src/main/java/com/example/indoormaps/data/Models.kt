package com.example.indoormaps.data

/**
 * Represents a single WiFi access point scan result
 */
data class AccessPoint(
    val bssid: String,      // MAC address (e.g., "70:e4:22:c0:1a:08")
    val ssid: String,       // Network name
    val rssi: Int,          // Signal strength in dBm
    val frequency: Int = 0  // Channel frequency in MHz (optional)
)

/**
 * Represents a complete WiFi scan with multiple access points
 */
data class WifiScan(
    val timestamp: Long,
    val accessPoints: List<AccessPoint>
) {
    val apCount: Int get() = accessPoints.size
}

/**
 * Prediction result from either local model or remote API
 */
data class PredictionResult(
    val location: String,
    val confidence: Float,
    val top3: List<Pair<String, Float>>,
    val matchedAPs: Int = 0,
    val totalAPs: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val source: PredictionSource = PredictionSource.LOCAL
)

/**
 * Source of prediction (local model vs remote API)
 */
enum class PredictionSource {
    LOCAL,      // TFLite model on device
    REMOTE      // API prediction from server
}

/**
 * Model metadata loaded from JSON
 */
data class ModelMetadata(
    val featureList: List<String>,
    val classes: List<String>,
    val nFeatures: Int,
    val nClasses: Int
)

/**
 * Scanning state
 */
sealed class ScanState {
    object Idle : ScanState()
    object Scanning : ScanState()
    data class Error(val message: String) : ScanState()
}
