package com.example.indoormaps

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.indoormaps.navigation.AppNavigation
import com.example.indoormaps.ui.theme.IndoorMapsTheme

class MainActivity : ComponentActivity() {
    
    companion object {
        private const val TAG = "MainActivity"
    }
    
    // Permission launcher
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            // Permissions granted - can start scanning
        } else {
            // Permissions denied - show explanation
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Log.d(TAG, "========================================")
        Log.d(TAG, "Indoor Maps App Starting...")
        Log.d(TAG, "========================================")
        
        // Check assets
        try {
            val assets = assets.list("")?.toList() ?: emptyList()
            Log.d(TAG, "Assets folder contains ${assets.size} files:")
            assets.forEach { Log.d(TAG, "  - $it") }
            
            val hasModel = assets.contains("wifi_positioning.tflite")
            val hasMetadata = assets.contains("model_metadata.json")
            
            Log.d(TAG, "Has TFLite model: $hasModel")
            Log.d(TAG, "Has metadata: $hasMetadata")
            
            if (!hasModel) {
                Log.e(TAG, "⚠️ TFLite model NOT FOUND in assets!")
            }
            if (!hasMetadata) {
                Log.e(TAG, "⚠️ Metadata NOT FOUND in assets!")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking assets", e)
        }
        
        setContent {
            IndoorMapsTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Use navigation instead of single screen
                    AppNavigation(
                        onRequestPermissions = { requestPermissions() }
                    )
                }
            }
        }
    }
    
    private fun requestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE
        )
        
        // Add notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        
        permissionLauncher.launch(permissions.toTypedArray())
    }
}