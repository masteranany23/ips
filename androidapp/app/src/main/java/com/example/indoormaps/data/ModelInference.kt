package com.example.indoormaps.data

import android.content.Context
import android.util.Log
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Handles local machine learning inference using TensorFlow Lite
 * Provides offline location prediction based on WiFi signals
 */
class ModelInference(private val context: Context) {
    
    companion object {
        private const val TAG = "ModelInference"
        private const val MODEL_FILE = "wifi_positioning.tflite"
        private const val METADATA_FILE = "model_metadata.json"
        private const val MISSING_RSSI = -110f
    }
    
    private var interpreter: Interpreter? = null
    private lateinit var metadata: ModelMetadata
    
    // Feature mapping for fast lookup
    private val featureIndexMap = mutableMapOf<String, Int>()
    
    private var isInitialized = false
    
    init {
        try {
            loadModel()
            loadMetadata()
            buildFeatureIndexMap()
            isInitialized = true
            Log.d(TAG, "Model initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize model", e)
            isInitialized = false
        }
    }
    
    private fun loadModel() {
        try {
            // Check if model file exists
            val assetFiles = context.assets.list("") ?: emptyArray()
            if (!assetFiles.contains(MODEL_FILE)) {
                throw Exception("Model file not found in assets. Available files: ${assetFiles.joinToString()}")
            }
            
            val modelBuffer = loadModelFile(MODEL_FILE)
            val options = Interpreter.Options().apply {
                setNumThreads(4)
            }
            interpreter = Interpreter(modelBuffer, options)
            Log.d(TAG, "TFLite model loaded successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading TFLite model: ${e.message}", e)
            throw e
        }
    }
    
    private fun loadMetadata() {
        try {
            // Check if metadata file exists
            val assetFiles = context.assets.list("") ?: emptyArray()
            if (!assetFiles.contains(METADATA_FILE)) {
                throw Exception("Metadata file not found in assets. Available files: ${assetFiles.joinToString()}")
            }
            
            val json = context.assets.open(METADATA_FILE)
                .bufferedReader()
                .use { it.readText() }
            
            val jsonObject = JSONObject(json)
            
            val featureArray = jsonObject.getJSONArray("feature_list")
            val classArray = jsonObject.getJSONArray("classes")
            
            val features = mutableListOf<String>()
            for (i in 0 until featureArray.length()) {
                features.add(featureArray.getString(i))
            }
            
            val classes = mutableListOf<String>()
            for (i in 0 until classArray.length()) {
                classes.add(classArray.getString(i))
            }
            
            metadata = ModelMetadata(
                featureList = features,
                classes = classes,
                nFeatures = jsonObject.getInt("n_features"),
                nClasses = jsonObject.getInt("n_classes")
            )
            
            Log.d(TAG, "Metadata loaded: ${metadata.nFeatures} features, ${metadata.nClasses} classes")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading metadata: ${e.message}", e)
            throw e
        }
    }
    
    private fun buildFeatureIndexMap() {
        metadata.featureList.forEachIndexed { index, bssid ->
            featureIndexMap[bssid] = index
        }
    }
    
    private fun loadModelFile(modelPath: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelPath)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }
    
    fun predict(accessPoints: List<AccessPoint>): PredictionResult? {
        if (!isInitialized) {
            Log.e(TAG, "Model not initialized")
            return null
        }
        
        try {
            val features = buildFeatureVector(accessPoints)
            val probabilities = runInference(features)
            return processResults(probabilities, accessPoints.size)
        } catch (e: Exception) {
            Log.e(TAG, "Prediction failed", e)
            return null
        }
    }
    
    private fun buildFeatureVector(accessPoints: List<AccessPoint>): FloatArray {
        val features = FloatArray(metadata.nFeatures) { MISSING_RSSI }
        var matchedCount = 0
        
        accessPoints.forEach { ap ->
            val normalizedBssid = if (ap.bssid.endsWith(":")) {
                ap.bssid
            } else {
                "${ap.bssid}:"
            }
            
            val index = featureIndexMap[normalizedBssid]
            if (index != null) {
                features[index] = ap.rssi.toFloat()
                matchedCount++
            }
        }
        
        Log.d(TAG, "Feature vector built: $matchedCount/${accessPoints.size} APs matched")
        return features
    }
    
    private fun runInference(features: FloatArray): FloatArray {
        val inputBuffer = ByteBuffer.allocateDirect(features.size * 4).apply {
            order(ByteOrder.nativeOrder())
            features.forEach { putFloat(it) }
            rewind()
        }
        
        val outputBuffer = ByteBuffer.allocateDirect(metadata.nClasses * 4).apply {
            order(ByteOrder.nativeOrder())
        }
        
        interpreter?.run(inputBuffer, outputBuffer)
        
        outputBuffer.rewind()
        val probabilities = FloatArray(metadata.nClasses)
        outputBuffer.asFloatBuffer().get(probabilities)
        
        return probabilities
    }
    
    private fun processResults(probabilities: FloatArray, totalAPs: Int): PredictionResult {
        val maxIndex = probabilities.indices.maxByOrNull { probabilities[it] } ?: 0
        val predictedLocation = metadata.classes[maxIndex]
        val confidence = probabilities[maxIndex]
        
        val top3 = probabilities.indices
            .sortedByDescending { probabilities[it] }
            .take(3)
            .map { metadata.classes[it] to probabilities[it] }
        
        Log.d(TAG, "Prediction: $predictedLocation (${confidence * 100}%)")
        
        return PredictionResult(
            location = predictedLocation,
            confidence = confidence,
            top3 = top3,
            matchedAPs = calculateMatchedAPs(totalAPs),
            totalAPs = totalAPs,
            source = PredictionSource.LOCAL
        )
    }
    
    private fun calculateMatchedAPs(totalAPs: Int): Int {
        return minOf(totalAPs, metadata.nFeatures)
    }
    
    fun getModelInfo(): String {
        return if (isInitialized) {
            "Model: ${metadata.nFeatures} features, ${metadata.nClasses} locations"
        } else {
            "Model not initialized"
        }
    }
    
    fun close() {
        interpreter?.close()
        interpreter = null
        Log.d(TAG, "Model interpreter closed")
    }
}
