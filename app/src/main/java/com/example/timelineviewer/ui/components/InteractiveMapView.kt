package com.example.timelineviewer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.timelineviewer.data.model.RoutePoint
import com.example.timelineviewer.data.model.Stop
import com.example.timelineviewer.data.model.TransportMode
import com.example.timelineviewer.data.model.TransportSegment
import kotlin.math.cos
import kotlin.math.sin

enum class MapStyle {
    TERRAIN, SATELLITE
}

@Composable
fun InteractiveMapView(
    points: List<RoutePoint>,
    stops: List<Stop>,
    segments: List<TransportSegment>,
    currentPointIndex: Int = 0,
    isPlaying: Boolean = false,
    mapStyle: MapStyle = MapStyle.TERRAIN,
    showStops: Boolean = true,
    isExpanded: Boolean = false,
    onMapStyleToggle: () -> Unit = {},
    onToggleStops: () -> Unit = {},
    onToggleFullscreen: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var selectedStop by remember { mutableStateOf<Stop?>(null) }

    // Pulsing halo animation for stops and active traveler
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseRatio by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseRatio"
    )

    if (points.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Map,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "No route points available for map visualization",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    // Calculate Lat/Lng bounds
    val minLat = points.minOf { it.latitude }
    val maxLat = points.maxOf { it.latitude }
    val minLng = points.minOf { it.longitude }
    val maxLng = points.maxOf { it.longitude }

    val latSpan = (maxLat - minLat).coerceAtLeast(0.001)
    val lngSpan = (maxLng - minLng).coerceAtLeast(0.001)

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(if (mapStyle == MapStyle.SATELLITE) Color(0xFF1B2A4A) else Color(0xFFE2E8F0))
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    zoomScale = (zoomScale * zoom).coerceIn(0.5f, 5f)
                    panOffset += pan
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val padding = 80f

            fun projectToOffset(lat: Double, lng: Double): Offset {
                val normX = ((lng - minLng) / lngSpan).toFloat()
                val normY = (1f - ((lat - minLat) / latSpan)).toFloat()

                val rawX = padding + normX * (width - 2 * padding)
                val rawY = padding + normY * (height - 2 * padding)

                val centerX = width / 2f
                val centerY = height / 2f

                val scaledX = (rawX - centerX) * zoomScale + centerX + panOffset.x
                val scaledY = (rawY - centerY) * zoomScale + centerY + panOffset.y

                return Offset(scaledX, scaledY)
            }

            // 1. Draw Map Background Elements (Grids & Features)
            drawMapGrid(size, mapStyle, zoomScale, panOffset)

            // 2. Draw Transport Mode Route Polylines
            if (segments.isNotEmpty()) {
                segments.forEach { segment ->
                    val segPoints = points.filter { it.sequenceOrder in segment.startIndex..segment.endIndex }
                    if (segPoints.size >= 2) {
                        val path = Path()
                        val firstOffset = projectToOffset(segPoints.first().latitude, segPoints.first().longitude)
                        path.moveTo(firstOffset.x, firstOffset.y)

                        for (i in 1 until segPoints.size) {
                            val off = projectToOffset(segPoints[i].latitude, segPoints[i].longitude)
                            path.lineTo(off.x, off.y)
                        }

                        drawPath(
                            path = path,
                            color = Color(segment.mode.hexColor),
                            style = Stroke(
                                width = 8f * zoomScale.coerceAtLeast(1f),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }
                }
            } else {
                // Fallback single path
                val path = Path()
                val first = projectToOffset(points.first().latitude, points.first().longitude)
                path.moveTo(first.x, first.y)
                for (i in 1 until points.size) {
                    val off = projectToOffset(points[i].latitude, points[i].longitude)
                    path.lineTo(off.x, off.y)
                }
                drawPath(
                    path = path,
                    color = Color(0xFF2563EB),
                    style = Stroke(
                        width = 8f * zoomScale.coerceAtLeast(1f),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }

            // 3. Draw Stop Markers
            if (showStops) {
                stops.forEach { stop ->
                    val stopOffset = projectToOffset(stop.latitude, stop.longitude)

                    // Outer pulse
                    drawCircle(
                        color = Color(0xFFEF4444).copy(alpha = 0.25f),
                        radius = 18f * pulseRatio * zoomScale,
                        center = stopOffset
                    )

                    // Inner stop marker
                    drawCircle(
                        color = Color(0xFFEF4444),
                        radius = 10f * zoomScale,
                        center = stopOffset
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 4f * zoomScale,
                        center = stopOffset
                    )
                }
            }

            // 4. Draw Active Traveler Position Marker
            val activeIdx = currentPointIndex.coerceIn(0, points.size - 1)
            val activePt = points[activeIdx]
            val activeOffset = projectToOffset(activePt.latitude, activePt.longitude)

            // Halo glow
            drawCircle(
                color = Color(0xFFF59E0B).copy(alpha = 0.4f),
                radius = 24f * pulseRatio * zoomScale,
                center = activeOffset
            )
            // Outer golden ring
            drawCircle(
                color = Color(0xFFF59E0B),
                radius = 12f * zoomScale,
                center = activeOffset
            )
            // Center white dot
            drawCircle(
                color = Color.White,
                radius = 6f * zoomScale,
                center = activeOffset
            )

            // Bearing Direction Arrow
            val bearingRad = Math.toRadians(activePt.bearing.toDouble())
            val arrowLength = 22f * zoomScale
            val arrowEnd = Offset(
                activeOffset.x + (arrowLength * sin(bearingRad)).toFloat(),
                activeOffset.y - (arrowLength * cos(bearingRad)).toFloat()
            )
            drawLine(
                color = Color(0xFFF59E0B),
                start = activeOffset,
                end = arrowEnd,
                strokeWidth = 4f * zoomScale,
                cap = StrokeCap.Round
            )
        }

        // Map Control Overlay Buttons (Top Right)
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MapControlButton(
                icon = if (mapStyle == MapStyle.TERRAIN) Icons.Default.Public else Icons.Default.Map,
                contentDescription = "Toggle Map Style",
                onClick = onMapStyleToggle,
                testTag = "map_style_toggle"
            )
            MapControlButton(
                icon = if (showStops) Icons.Default.Place else Icons.Default.LocationOff,
                contentDescription = "Toggle Stops",
                onClick = onToggleStops,
                testTag = "toggle_stops"
            )
            MapControlButton(
                icon = Icons.Default.Add,
                contentDescription = "Zoom In",
                onClick = { zoomScale = (zoomScale * 1.25f).coerceAtMost(5f) },
                testTag = "zoom_in"
            )
            MapControlButton(
                icon = Icons.Default.Remove,
                contentDescription = "Zoom Out",
                onClick = { zoomScale = (zoomScale / 1.25f).coerceAtLeast(0.5f) },
                testTag = "zoom_out"
            )
            MapControlButton(
                icon = Icons.Default.MyLocation,
                contentDescription = "Reset Camera",
                onClick = {
                    zoomScale = 1f
                    panOffset = Offset.Zero
                },
                testTag = "reset_camera"
            )
            MapControlButton(
                icon = if (isExpanded) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                contentDescription = "Fullscreen",
                onClick = onToggleFullscreen,
                testTag = "toggle_fullscreen"
            )
        }

        // Active Location Badge Card (Top Left)
        val activePt = points[currentPointIndex.coerceIn(0, points.size - 1)]
        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFF59E0B))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "GPS: ${String.format("%.4f", activePt.latitude)}, ${String.format("%.4f", activePt.longitude)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Speed: ${String.format("%.1f", activePt.speedKmh)} km/h • Pt ${currentPointIndex + 1}/${points.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun MapControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        shadowElevation = 4.dp,
        modifier = Modifier
            .size(40.dp)
            .testTag(testTag)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

private fun DrawScope.drawMapGrid(size: Size, style: MapStyle, zoom: Float, pan: Offset) {
    val step = 80f * zoom
    val width = size.width
    val height = size.height

    val gridColor = if (style == MapStyle.SATELLITE) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)

    var x = (pan.x % step)
    while (x < width) {
        if (x >= 0) {
            drawLine(
                color = gridColor,
                start = Offset(x, 0f),
                end = Offset(x, height),
                strokeWidth = 1f
            )
        }
        x += step
    }

    var y = (pan.y % step)
    while (y < height) {
        if (y >= 0) {
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(width, y),
                strokeWidth = 1f
            )
        }
        y += step
    }
}
