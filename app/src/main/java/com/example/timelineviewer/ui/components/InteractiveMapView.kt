package com.example.timelineviewer.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.timelineviewer.BuildConfig
import com.example.timelineviewer.data.model.RoutePoint
import com.example.timelineviewer.data.model.Stop
import com.example.timelineviewer.data.model.TransportSegment
import com.mapbox.geojson.Point
import com.mapbox.maps.dsl.cameraOptions
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.compose.annotation.generated.CircleAnnotation
import com.mapbox.maps.extension.compose.annotation.generated.PolylineAnnotation
import com.mapbox.maps.extension.compose.style.BooleanValue
import com.mapbox.maps.extension.compose.style.standard.LightPresetValue
import com.mapbox.maps.extension.compose.style.standard.MapboxStandardSatelliteStyle
import com.mapbox.maps.extension.compose.style.standard.MapboxStandardStyle
import com.mapbox.maps.extension.compose.style.standard.rememberStandardSatelliteStyleState
import com.mapbox.maps.extension.compose.style.standard.rememberStandardStyleState
import kotlin.math.abs

enum class MapStyle {
    CINEMATIC, SATELLITE
}

private enum class CameraMode {
    OVERVIEW, FOLLOW, ORBIT
}

/**
 * A real Mapbox-based map canvas that renders imported journey geometry on the 3D Mapbox Standard
 * basemap. It keeps all of the project-specific route, stop, and playback data local while Mapbox
 * supplies the interactive terrain, buildings, labels, and camera projection.
 */
@Composable
fun InteractiveMapView(
    points: List<RoutePoint>,
    stops: List<Stop>,
    segments: List<TransportSegment>,
    currentPointIndex: Int = 0,
    isPlaying: Boolean = false,
    mapStyle: MapStyle = MapStyle.CINEMATIC,
    showStops: Boolean = true,
    isExpanded: Boolean = false,
    onMapStyleToggle: () -> Unit = {},
    onToggleStops: () -> Unit = {},
    onToggleFullscreen: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (points.isEmpty()) {
        EmptyMapState(modifier)
        return
    }

    // No token is embedded in source control. Until a personal public token is supplied in
    // local.properties, keep the app operational and present a clear setup path.
    if (!BuildConfig.MAPBOX_ACCESS_TOKEN_CONFIGURED) {
        MapboxConfigurationRequired(modifier)
        return
    }

    val routeCenter = remember(points) { routeCenter(points) }
    val overviewZoom = remember(points) { routeOverviewZoom(points) }
    val mapViewportState = rememberMapViewportState {
        setCameraOptions {
            center(routeCenter)
            zoom(overviewZoom)
            pitch(52.0)
            bearing(0.0)
        }
    }

    var cameraMode by remember { mutableStateOf(CameraMode.OVERVIEW) }
    val activePoint = points[currentPointIndex.coerceIn(0, points.lastIndex)]

    // During playback, Follow creates the cinematic "traveller camera" effect. The user can
    // deliberately select Overview or Orbit when they want a wider editorial framing.
    LaunchedEffect(currentPointIndex, isPlaying, cameraMode) {
        if (isPlaying && cameraMode == CameraMode.FOLLOW) {
            mapViewportState.easeTo(
                cameraOptions {
                    center(Point.fromLngLat(activePoint.longitude, activePoint.latitude))
                    zoom(16.2)
                    pitch(64.0)
                    bearing(activePoint.bearing.toDouble())
                }
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
    ) {
        MapboxMap(
            modifier = Modifier.fillMaxSize(),
            mapViewportState = mapViewportState,
            style = {
                if (mapStyle == MapStyle.SATELLITE) {
                    MapboxStandardSatelliteStyle(
                        standardSatelliteStyleState = rememberStandardSatelliteStyleState {
                            configurationsState.apply {
                                lightPreset = LightPresetValue.DUSK
                                showPlaceLabels = BooleanValue(true)
                                showRoadLabels = BooleanValue(true)
                                showPointOfInterestLabels = BooleanValue(true)
                            }
                        }
                    )
                } else {
                    MapboxStandardStyle(
                        standardStyleState = rememberStandardStyleState {
                            configurationsState.apply {
                                // Standard supports 3D building geometry, enhanced by the pitched
                                // camera used for overview, follow, and orbit modes.
                                show3dObjects = BooleanValue(true)
                                lightPreset = LightPresetValue.DUSK
                                showPlaceLabels = BooleanValue(true)
                                showRoadLabels = BooleanValue(true)
                                showPointOfInterestLabels = BooleanValue(true)
                            }
                        }
                    )
                }
            }
        ) {
            RouteAnnotations(
                points = points,
                segments = segments
            )

            if (showStops) {
                stops.forEach { stop ->
                    StopAnnotation(stop)
                }
            }

            // The active traveller is rendered as a two-ring marker so that it remains visible
            // over satellite imagery, dark map styling, and dense streets.
            val traveller = Point.fromLngLat(activePoint.longitude, activePoint.latitude)
            CircleAnnotation(point = traveller) {
                circleRadius = 15.0
                circleColor = Color(0x55F59E0B)
            }
            CircleAnnotation(point = traveller) {
                circleRadius = 8.0
                circleColor = Color(0xFFF59E0B)
            }
            CircleAnnotation(point = traveller) {
                circleRadius = 3.5
                circleColor = Color.White
            }
        }

        MapControlStack(
            mapStyle = mapStyle,
            showStops = showStops,
            cameraMode = cameraMode,
            isExpanded = isExpanded,
            onMapStyleToggle = onMapStyleToggle,
            onToggleStops = onToggleStops,
            onZoomIn = {
                mapViewportState.easeTo(
                    cameraOptions { zoom((mapViewportState.cameraState.zoom + 1.0).coerceAtMost(20.0)) }
                )
            },
            onZoomOut = {
                mapViewportState.easeTo(
                    cameraOptions { zoom((mapViewportState.cameraState.zoom - 1.0).coerceAtLeast(1.0)) }
                )
            },
            onOverview = {
                cameraMode = CameraMode.OVERVIEW
                mapViewportState.easeTo(
                    cameraOptions {
                        center(routeCenter)
                        zoom(overviewZoom)
                        pitch(52.0)
                        bearing(0.0)
                    }
                )
            },
            onFollow = {
                cameraMode = CameraMode.FOLLOW
                mapViewportState.easeTo(
                    cameraOptions {
                        center(Point.fromLngLat(activePoint.longitude, activePoint.latitude))
                        zoom(16.2)
                        pitch(64.0)
                        bearing(activePoint.bearing.toDouble())
                    }
                )
            },
            onOrbit = {
                cameraMode = CameraMode.ORBIT
                mapViewportState.easeTo(
                    cameraOptions {
                        center(Point.fromLngLat(activePoint.longitude, activePoint.latitude))
                        zoom(15.4)
                        pitch(70.0)
                        bearing((activePoint.bearing + 55f).toDouble())
                    }
                )
            },
            onToggleFullscreen = onToggleFullscreen,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
        )

        ActiveLocationBadge(
            activePoint = activePoint,
            currentPointIndex = currentPointIndex,
            pointCount = points.size,
            cameraMode = cameraMode,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
        )
    }
}

@Composable
private fun RouteAnnotations(
    points: List<RoutePoint>,
    segments: List<TransportSegment>
) {
    val routeSegments = remember(points, segments) {
        if (segments.isEmpty()) {
            listOf(points to Color(0xFF2563EB))
        } else {
            segments.mapNotNull { segment ->
                val segmentPoints = points.filter { it.sequenceOrder in segment.startIndex..segment.endIndex }
                if (segmentPoints.size >= 2) segmentPoints to Color(segment.mode.hexColor) else null
            }
        }
    }

    routeSegments.forEach { (segmentPoints, color) ->
        PolylineAnnotation(
            points = segmentPoints.map { Point.fromLngLat(it.longitude, it.latitude) }
        ) {
            lineColor = color
            lineWidth = 6.0
        }
    }
}

@Composable
private fun StopAnnotation(stop: Stop) {
    val point = Point.fromLngLat(stop.longitude, stop.latitude)
    val highlightColor = if (stop.importanceScore >= 80) Color(0xFFF43F5E) else Color(0xFFEF4444)

    CircleAnnotation(point = point) {
        circleRadius = if (stop.importanceScore >= 80) 11.0 else 8.0
        circleColor = highlightColor.copy(alpha = 0.28f)
    }
    CircleAnnotation(point = point) {
        circleRadius = if (stop.importanceScore >= 80) 6.5 else 5.0
        circleColor = highlightColor
    }
    CircleAnnotation(point = point) {
        circleRadius = 2.0
        circleColor = Color.White
    }
}

@Composable
private fun MapControlStack(
    mapStyle: MapStyle,
    showStops: Boolean,
    cameraMode: CameraMode,
    isExpanded: Boolean,
    onMapStyleToggle: () -> Unit,
    onToggleStops: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onOverview: () -> Unit,
    onFollow: () -> Unit,
    onOrbit: () -> Unit,
    onToggleFullscreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MapControlButton(
            icon = if (mapStyle == MapStyle.CINEMATIC) Icons.Default.SatelliteAlt else Icons.Default.Map,
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
            onClick = onZoomIn,
            testTag = "zoom_in"
        )
        MapControlButton(
            icon = Icons.Default.Remove,
            contentDescription = "Zoom Out",
            onClick = onZoomOut,
            testTag = "zoom_out"
        )
        MapControlButton(
            icon = Icons.Default.Map,
            contentDescription = "Route Overview",
            onClick = onOverview,
            selected = cameraMode == CameraMode.OVERVIEW,
            testTag = "camera_overview"
        )
        MapControlButton(
            icon = Icons.Default.MyLocation,
            contentDescription = "Follow Traveller",
            onClick = onFollow,
            selected = cameraMode == CameraMode.FOLLOW,
            testTag = "camera_follow"
        )
        MapControlButton(
            icon = Icons.Default.Refresh,
            contentDescription = "Orbit Traveller",
            onClick = onOrbit,
            selected = cameraMode == CameraMode.ORBIT,
            testTag = "camera_orbit"
        )
        MapControlButton(
            icon = if (isExpanded) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
            contentDescription = "Fullscreen",
            onClick = onToggleFullscreen,
            testTag = "toggle_fullscreen"
        )
    }
}

@Composable
private fun ActiveLocationBadge(
    activePoint: RoutePoint,
    currentPointIndex: Int,
    pointCount: Int,
    cameraMode: CameraMode,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
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
                    text = cameraMode.name.lowercase().replaceFirstChar { it.uppercase() } + " camera",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "${String.format("%.4f", activePoint.latitude)}, ${String.format("%.4f", activePoint.longitude)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${String.format("%.1f", activePoint.speedKmh)} km/h · ${currentPointIndex + 1}/$pointCount",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MapboxConfigurationRequired(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.padding(24.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 2.dp
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Map,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text("Mapbox needs your personal access token", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Add MAPBOX_ACCESS_TOKEN=pk.… to local.properties, then rebuild. The token stays on your device and is not committed to Git.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EmptyMapState(modifier: Modifier = Modifier) {
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
}

@Composable
private fun MapControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    testTag: String,
    selected: Boolean = false
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
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
                tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

private fun routeCenter(points: List<RoutePoint>): Point = Point.fromLngLat(
    points.map { it.longitude }.average(),
    points.map { it.latitude }.average()
)

private fun routeOverviewZoom(points: List<RoutePoint>): Double {
    val latSpan = points.maxOf { it.latitude } - points.minOf { it.latitude }
    val lonSpan = points.maxOf { it.longitude } - points.minOf { it.longitude }
    val span = maxOf(abs(latSpan), abs(lonSpan))
    return when {
        span < 0.003 -> 15.5
        span < 0.01 -> 14.0
        span < 0.04 -> 12.5
        span < 0.12 -> 11.0
        span < 0.4 -> 9.5
        span < 1.2 -> 8.0
        span < 4.0 -> 6.5
        else -> 5.0
    }
}
