package com.example.indoormaps.ui.map

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Simplified map screen with background WiFi scanning
 * and single local/remote toggle
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    viewModel: MapViewModel = viewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val currentMap by viewModel.currentMap.collectAsState()
    val activeNode by viewModel.activeNode.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val useRemotePrediction by viewModel.useRemotePrediction.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val scannedAPCount by viewModel.scannedAPCount.collectAsState()
    
    // Auto-start scanning when entering map screen
    LaunchedEffect(Unit) {
        val locationViewModel = viewModel.getLocationViewModel()
        if (!isScanning && locationViewModel.hasPermissions()) {
            locationViewModel.startScanning()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            currentMap?.let { "${it.buildingId} - Floor ${it.floorId}" } 
                                ?: "Indoor Map"
                        )
                        if (isScanning) {
                            Text(
                                text = "$scannedAPCount APs detected",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0077FF),
                    titleContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                currentMap == null -> {
                    Text(
                        "Loading map...",
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    FloorMapView(
                        floorMap = currentMap!!,
                        activeNodeId = activeNode?.id,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            
            // Simple toggle button (only UI element)
            Card(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (useRemotePrediction) 
                        Color(0xFF764ba2) else Color(0xFF667eea)
                ),
                onClick = { viewModel.togglePredictionSource() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (useRemotePrediction) "Remote" else "Local",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "⇄",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            // Active location info (minimal, at bottom)
            activeNode?.let { node ->
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White
                    ),
                    elevation = CardDefaults.cardElevation(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = node.label,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (useRemotePrediction) "Remote API" else "Local TFLite",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (useRemotePrediction) 
                                    Color(0xFF764ba2) else Color(0xFF667eea)
                            )
                        }
                        
                        // Scanning indicator
                        if (isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                strokeWidth = 3.dp,
                                color = if (useRemotePrediction) 
                                    Color(0xFF764ba2) else Color(0xFF667eea)
                            )
                        }
                    }
                }
            }
            
            // Error message
            errorMessage?.let { message ->
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 80.dp, start = 16.dp, end = 16.dp),
                    action = {
                        TextButton(onClick = { viewModel.clearError() }) {
                            Text("OK")
                        }
                    }
                ) {
                    Text(message)
                }
            }
        }
    }
}
