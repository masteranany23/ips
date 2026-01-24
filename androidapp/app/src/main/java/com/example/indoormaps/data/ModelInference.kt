package com.example.indoormaps.data

import android.content.Context
import android.util.Log
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * TensorFlow Lite model inference for WiFi positioning
 */
class ModelInference(private val context: Context) {
    
    companion object {
        private const val TAG = "ModelInference"
        private const val MODEL_FILE = "wifi_positioning.tflite"
        private const val METADATA_FILE = "model_metadata.json"
    }
    
    private var interpreter: Interpreter? = null
    private var featureList: List<String> = emptyList()
    private var classes: List<String> = emptyList()
    private var isInitialized = false
    
    init {
        try {
            Log.d(TAG, "========================================")
            Log.d(TAG, "Initializing ModelInference...")
            Log.d(TAG, "========================================")
            
            loadModel()
            loadMetadata()
            
            isInitialized = true
            Log.d(TAG, "✅ Model initialized successfully")
            Log.d(TAG, "  Features: ${featureList.size}")
            Log.d(TAG, "  Classes: ${classes.size}")
            Log.d(TAG, "========================================")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to initialize model", e)
            Log.e(TAG, "Error type: ${e.javaClass.simpleName}")
            Log.e(TAG, "Error message: ${e.message}")
            e.printStackTrace()
            isInitialized = false
        }
    }
    
    private fun loadModel() {
        try {
            Log.d(TAG, "Loading model from assets: $MODEL_FILE")
            
            // Check if file exists in assets
            val assetFiles = context.assets.list("")?.toList() ?: emptyList()
            Log.d(TAG, "Assets folder contains ${assetFiles.size} files:")
            assetFiles.take(10).forEach { Log.d(TAG, "  - $it") }
            
            if (!assetFiles.contains(MODEL_FILE)) {
                throw IllegalStateException("Model file not found in assets: $MODEL_FILE")
            }
            
            // Load model file
            val modelBuffer = loadModelFile()
            Log.d(TAG, "Model buffer loaded: ${modelBuffer.capacity()} bytes")
            
            // Create interpreter with options
            val options = Interpreter.Options().apply {
                setNumThreads(4)
                setUseNNAPI(false)  // Disable for compatibility
            }
            
            interpreter = Interpreter(modelBuffer, options)
            Log.d(TAG, "✓ TFLite interpreter created")
            
            // Log tensor details
            try {
                val inputTensor = interpreter!!.getInputTensor(0)
                val outputTensor = interpreter!!.getOutputTensor(0)
                
                Log.d(TAG, "Input tensor shape: ${inputTensor.shape().contentToString()}")
                Log.d(TAG, "Output tensor shape: ${outputTensor.shape().contentToString()}")
            } catch (e: Exception) {
                Log.w(TAG, "Could not get tensor info: ${e.message}")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load model", e)
            throw e
        }
    }
    
    private fun loadModelFile(): MappedByteBuffer {
        return context.assets.openFd(MODEL_FILE).use { fileDescriptor ->
            FileInputStream(fileDescriptor.fileDescriptor).use { inputStream ->
                val fileChannel = inputStream.channel
                val startOffset = fileDescriptor.startOffset
                val declaredLength = fileDescriptor.declaredLength
                fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
            }
        }
    }
    
    private fun loadMetadata() {
        try {
            Log.d(TAG, "Loading metadata from assets: $METADATA_FILE")
            
            val json = context.assets.open(METADATA_FILE).bufferedReader().use { it.readText() }
            Log.d(TAG, "Metadata JSON loaded (${json.length} chars)")
            
            val metadata = JSONObject(json)
            
            // Parse feature list
            val featuresArray = metadata.getJSONArray("feature_list")
            featureList = List(featuresArray.length()) { i ->
                featuresArray.getString(i)
            }
            Log.d(TAG, "✓ Loaded ${featureList.size} features")
            
            // Parse classes
            val classesArray = metadata.getJSONArray("classes")
            classes = List(classesArray.length()) { i ->
                classesArray.getString(i)
            }
            Log.d(TAG, "✓ Loaded ${classes.size} classes:")
            Log.d(TAG, "  $classes")
            
            // Log accuracy if available
            if (metadata.has("tflite_accuracy")) {
                Log.d(TAG, "Model accuracy: ${metadata.getString("tflite_accuracy")}")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load metadata", e)
            throw e
        }
    }
    
    fun predict(accessPoints: List<AccessPoint>): PredictionResult? {
        if (!isInitialized) {
            Log.e(TAG, "Cannot predict - model not initialized")
            return null
        }
        
        if (interpreter == null) {
            Log.e(TAG, "Cannot predict - interpreter is null")
            return null
        }
        
        try {
            // Build feature vector
            val inputVector = FloatArray(featureList.size) { -110f }
            
            var matchedAPs = 0
            accessPoints.forEach { ap ->
                val bssid = normalizeBssid(ap.bssid)
                val index = featureList.indexOf(bssid)
                if (index >= 0) {
                    inputVector[index] = ap.rssi.toFloat()
                    matchedAPs++
                }
            }
            
            Log.d(TAG, "Predicting: ${accessPoints.size} APs, matched $matchedAPs/${featureList.size} features")
            
            // Run inference
            val input = Array(1) { inputVector }
            val output = Array(1) { FloatArray(classes.size) }
            
            interpreter!!.run(input, output)
            
            // Get predictions
            val probabilities = output[0]
            val maxIndex = probabilities.indices.maxByOrNull { probabilities[it] } ?: 0
            val confidence = probabilities[maxIndex]
            val predictedClass = classes[maxIndex]
            
            // Get top 3
            val top3 = probabilities.indices
                .sortedByDescending { probabilities[it] }
                .take(3)
                .map { classes[it] to probabilities[it] }
            
            Log.d(TAG, "✓ Prediction: $predictedClass (${(confidence * 100).toInt()}%)")
            Log.d(TAG, "  Top 3: ${top3.map { "${it.first}:${(it.second*100).toInt()}%" }}")
            
            return PredictionResult(
                location = predictedClass,
                confidence = confidence,
                top3 = top3,
                matchedAPs = matchedAPs,
                totalAPs = accessPoints.size,
                source = PredictionSource.LOCAL
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Prediction failed", e)
            return null
        }
    }
    
    private fun normalizeBssid(bssid: String): String {
        var normalized = bssid.lowercase().replace("-", ":")
        if (!normalized.endsWith(":")) {
            normalized += ":"
        }
        return normalized
    }
    
    fun getModelInfo(): String {
        return if (isInitialized && classes.isNotEmpty()) {
            "Local TFLite: ${classes.size} locations (${featureList.size} features)"
        } else {
            "Local model: Not initialized ❌"
        }
    }
    
    fun close() {
        try {
            interpreter?.close()
            interpreter = null
            Log.d(TAG, "Model closed")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing model", e)
        }
    }
}
