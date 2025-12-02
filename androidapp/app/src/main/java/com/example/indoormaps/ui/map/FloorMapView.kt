package com.example.indoormaps.ui.map

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.example.indoormaps.data.map.FloorMap
import com.example.indoormaps.data.map.LocationNode
import kotlin.math.min
import kotlin.math.sqrt

// Color palette
private val PrimaryColor = Color(0xFF0077FF)
private val AccentColor = Color(0xFFFF5722)
private val HighlightColor = Color(0xFF00C853)
private val BackgroundColor = Color(0xFFF7FAFC)
private val TextColor = Color(0xFF1F2937)
private val EdgeColor = Color(0xFFCCCCCC)
private val CornerColor = Color(0xFF666666)

/**
 * Renders a floor map on canvas with nodes, edges, and active node highlighting
 */
@Composable
fun FloorMapView(
    floorMap: FloorMap,
    activeNodeId: String?,
    modifier: Modifier = Modifier,
    onNodeTap: (LocationNode) -> Unit = {}
) {
    // Camera offset for centering
    var cameraOffset by remember { mutableStateOf(Offset.Zero) }
    
    // Animate active node pulsing
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    
    // Precompute node positions
    val nodePositions = remember { mutableMapOf<String, Offset>() }
    
    Canvas(
        modifier = modifier
            .pointerInput(floorMap) {
                detectTapGestures { tapOffset ->
                    // Find tapped node
                    val threshold = 60f // pixels
                    nodePositions.entries.firstOrNull { (_, pos) ->
                        val dx = pos.x - tapOffset.x
                        val dy = pos.y - tapOffset.y
                        sqrt(dx * dx + dy * dy) < threshold
                    }?.let { (nodeId, _) ->
                        floorMap.findNode(nodeId)?.let(onNodeTap)
                    }
                }
            }
    ) {
        // Compute scale using correct formula
        val sx = size.width / floorMap.width
        val sy = size.height / floorMap.height
        val scale = min(sx, sy)
        val extraX = (size.width - floorMap.width * scale) / 2f
        val extraY = (size.height - floorMap.height * scale) / 2f
        
        // Helper to convert logical coords to pixels
        fun toPx(node: LocationNode): Offset {
            val px = node.x * scale + extraX + cameraOffset.x
            val py = node.y * scale + extraY + cameraOffset.y
            return Offset(px, py)
        }
        
        // Precompute all node positions
        nodePositions.clear()
        floorMap.nodes.forEach { node ->
            nodePositions[node.id] = toPx(node)
        }
        
        withTransform({}) {
            // Draw background
            drawRect(BackgroundColor)
            
            // Draw edges first (behind nodes)
            floorMap.edges.forEach { edge ->
                val fromPos = nodePositions[edge.from]
                val toPos = nodePositions[edge.to]
                if (fromPos != null && toPos != null) {
                    // Check if this is a triangle edge (corner to corner)
                    val isTriangleEdge = edge.from.contains("CORNER") && edge.to.contains("CORNER")
                    
                    drawLine(
                        color = if (isTriangleEdge) CornerColor else EdgeColor,
                        start = fromPos,
                        end = toPos,
                        strokeWidth = if (isTriangleEdge) 4.dp.toPx() else 2.dp.toPx()
                    )
                }
            }
            
            // Draw nodes
            floorMap.nodes.forEach { node ->
                val pos = nodePositions[node.id] ?: return@forEach
                val isActive = node.id == activeNodeId
                val isCorner = node.id.contains("CORNER")
                val isRoom = node.id.contains("ROOM")
                val isOAT = node.id.contains("OAT")
                
                val nodeColor = when {
                    isActive -> HighlightColor
                    isCorner -> CornerColor
                    isOAT -> AccentColor
                    isRoom -> PrimaryColor
                    else -> Color.LightGray
                }
                
                val baseRadius = when {
                    isCorner -> 8.dp.toPx()
                    isOAT -> 16.dp.toPx()
                    isRoom -> 14.dp.toPx()
                    else -> 10.dp.toPx()
                }
                
                val radius = if (isActive) baseRadius * pulseScale else baseRadius
                
                // Glow ring for active node
                if (isActive) {
                    drawCircle(
                        color = HighlightColor.copy(alpha = 0.3f),
                        radius = radius + 15.dp.toPx(),
                        center = pos
                    )
                    drawCircle(
                        color = HighlightColor.copy(alpha = 0.2f),
                        radius = radius + 25.dp.toPx(),
                        center = pos
                    )
                }
                
                // Main node circle
                drawCircle(
                    color = nodeColor,
                    radius = radius,
                    center = pos
                )
                
                // Inner circle for contrast
                if (isRoom || isOAT) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.3f),
                        radius = radius * 0.6f,
                        center = pos
                    )
                }
                
                // Draw labels for active node and rooms
                if (isActive || isRoom || isOAT) {
                    drawContext.canvas.nativeCanvas.apply {
                        val paint = android.graphics.Paint().apply {
                            color = android.graphics.Color.parseColor(
                                if (isActive) "#1F2937" else "#666666"
                            )
                            textSize = if (isActive) 32f else 26f
                            textAlign = android.graphics.Paint.Align.CENTER
                            isFakeBoldText = isActive
                        }
                        
                        // Draw label above node
                        val labelY = pos.y - radius - 15.dp.toPx()
                        drawText(
                            node.label,
                            pos.x,
                            labelY,
                            paint
                        )
                        
                        // Draw ID below for active node
                        if (isActive) {
                            paint.textSize = 20f
                            paint.color = android.graphics.Color.parseColor("#999999")
                            paint.isFakeBoldText = false
                            drawText(
                                node.id,
                                pos.x,
                                pos.y + radius + 35.dp.toPx(),
                                paint
                            )
                        }
                    }
                }
            }
        }
    }
    
    // Center camera on active node when it changes
    LaunchedEffect(activeNodeId) {
        if (activeNodeId != null) {
            // Camera centering can be implemented here
            // For now, keeping offset at zero
            cameraOffset = Offset.Zero
        }
    }
}
