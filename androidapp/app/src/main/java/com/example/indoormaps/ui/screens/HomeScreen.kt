package com.example.indoormaps.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.indoormaps.data.PredictionResult
import com.example.indoormaps.data.ScanState
import com.example.indoormaps.ui.LocationViewModel

@Composable
fun HomeScreen(
    viewModel: LocationViewModel,
    onRequestPermissions: () -> Unit,
    onNavigateToMap: () -> Unit = {} // Add navigation callback
) {
    val localPrediction by viewModel.localPrediction.collectAsState()
    val remotePrediction by viewModel.remotePrediction.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val scannedAPs by viewModel.scannedAPCount.collectAsState()
    val scanState by viewModel.scanState.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val modelInfo by viewModel.modelInfo.collectAsState()
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF667eea),
                        Color(0xFF764ba2)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "WiFi Indoor Positioning",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 16.dp)
            )
            
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.9f)
                )
            ) {
                Text(
                    text = modelInfo,
                    fontSize = 12.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(12.dp),
                    textAlign = TextAlign.Center
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            StatusCard(isScanning, scannedAPs, scanState)
            
            Spacer(modifier = Modifier.height(16.dp))
            
            PredictionCard("Local Prediction", localPrediction, Color(0xFF667eea))
            
            Spacer(modifier = Modifier.height(16.dp))
            
            PredictionCard("Remote Prediction", remotePrediction, Color(0xFF764ba2))
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Add View on Map button
            if (localPrediction != null || remotePrediction != null) {
                Button(
                    onClick = onNavigateToMap,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00C853)
                    )
                ) {
                    Text(
                        "📍 View on Map",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            ControlButtons(
                isScanning,
                viewModel.hasPermissions(),
                { viewModel.startScanning() },
                { viewModel.stopScanning() },
                onRequestPermissions,
                { viewModel.testApiConnection() }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        // Fixed error message display
        errorMessage?.let { msg ->
            Snackbar(
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                action = {
                    TextButton(onClick = { viewModel.clearError() }) {
                        Text("OK", color = Color.White)
                    }
                }
            ) {
                Text(text = msg)
            }
        }
    }
}

@Composable
fun StatusCard(isScanning: Boolean, scannedAPs: Int, scanState: ScanState) {
    val statusColor by animateColorAsState(
        targetValue = when {
            scanState is ScanState.Error -> Color(0xFFE53935)
            isScanning -> Color(0xFF4CAF50)
            else -> Color.Gray
        }
    )
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when {
                            scanState is ScanState.Error -> "Error"
                            !isScanning -> "Ready"
                            scannedAPs > 0 -> "Live Updates" // Changed from "Scanning Active"
                            else -> "Initializing..."
                        },
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = when {
                            scannedAPs > 0 -> "$scannedAPs networks • ~3-5s refresh" // Show update rate
                            isScanning -> "Searching..."
                            else -> "Tap Start"
                        },
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                }
                
                Box(
                    modifier = Modifier.size(48.dp).clip(CircleShape)
                        .background(statusColor.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            color = statusColor,
                            strokeWidth = 3.dp
                        )
                    } else {
                        Box(
                            modifier = Modifier.size(16.dp).clip(CircleShape)
                                .background(statusColor)
                        )
                    }
                }
            }
            
            // Show error details with helpful instructions
            if (scanState is ScanState.Error) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = scanState.message,
                    fontSize = 12.sp,
                    color = Color(0xFFE53935),
                    modifier = Modifier.fillMaxWidth()
                )
                
                // Show hint for location
                if (scanState.message.contains("Location", ignoreCase = true)) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "→ Open Settings → Location → Turn ON",
                        fontSize = 11.sp,
                        color = Color(0xFFFF6B6B),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun PredictionCard(title: String, prediction: PredictionResult?, color: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = color),
        elevation = CardDefaults.cardElevation(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = title, fontSize = 14.sp, color = Color.White.copy(alpha = 0.8f))
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = prediction?.location ?: "---",
                fontSize = 42.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            val confidence = prediction?.confidence ?: 0f
            Text(
                text = "${(confidence * 100).toInt()}%",
                fontSize = 20.sp,
                color = Color.White.copy(alpha = 0.9f)
            )
            Spacer(modifier = Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { confidence },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.3f)
            )
            
            // Fixed top3 display
            prediction?.top3?.takeIf { it.isNotEmpty() }?.let { topList ->
                Spacer(modifier = Modifier.height(20.dp))
                Divider(color = Color.White.copy(alpha = 0.3f))
                Spacer(modifier = Modifier.height(12.dp))
                Text("Top 3", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.height(12.dp))
                topList.forEachIndexed { index, pair ->
                    val (loc, prob) = pair
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("${index + 1}. $loc", color = Color.White, fontSize = 16.sp)
                        Text("${(prob * 100).toInt()}%", color = Color.White, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun ControlButtons(
    isScanning: Boolean,
    hasPermissions: Boolean,
    onStartScanning: () -> Unit,
    onStopScanning: () -> Unit,
    onRequestPermissions: () -> Unit,
    onTestApi: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (!hasPermissions) {
            Button(
                onClick = onRequestPermissions,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B6B))
            ) {
                Text("Grant Permissions", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onStartScanning,
                    enabled = !isScanning,
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50),
                        disabledContainerColor = Color.Gray
                    )
                ) {
                    Text("Start", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = onStopScanning,
                    enabled = isScanning,
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE53935),
                        disabledContainerColor = Color.Gray
                    )
                ) {
                    Text("Stop", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = onTestApi,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) {
                Text("Test API", fontSize = 14.sp)
            }
        }
    }
}
