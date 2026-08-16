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
    preferFollowCamera: Boolean = false,
    showControls: Boolean = true,
    showActiveBadge: Boolean = true,
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

    var cameraMode by remember(preferFollowCamera) {
        mutableStateOf(if (preferFollowCamera) CameraMode.FOLLOW else CameraMode.OVERVIEW)
    }
    val activePoint = points[currentPointIndex.coerceIn(0, points.lastIndex)]

    // During playback, Follow creates the cinematic "traveller camera" effect. The user can
    // deliberately select Overview or Orbit when they want a wider editorial framing.
    LaunchedEffect(currentPointIndex, isPlaying, cameraMode) {
        if (isPlaying && cameraMode == CameraMode.FOLLOW) {
            mapViewportState.setCameraOptions(
                cameraOptions {
                    center(Point.fromLngLat(activePoint.longitude, activePoint.latitude))
                    zoom(15.8)
                    pitch(58.0)
                    bearing(activePoint.bearing.toDouble())
                }
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(24.dp))
    ) {
        MapboxMap(
            modifier = Modifier.fillMaxSize(),
            mapViewportState = mapViewportState,
            style = {
                if (mapStyle == MapStyle.SATELLITE) {
                    MapboxStandardSatelliteStyle(
                        standardSatelliteStyleState = rememberStandardSatelliteStyleState {
                            configurationsState.apply {
                                lightPreset = LightPresetValue.DAY
                                showPlaceLabels = BooleanValue(false)
                                showRoadLabels = BooleanValue(true)
                                showPointOfInterestLabels = BooleanValue(false)
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
                                lightPreset = LightPresetValue.DAY
                                showPlaceLabels = BooleanValue(false)
                                showRoadLabels = BooleanValue(true)
                                showPointOfInterestLabels = BooleanValue(false)
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
                circleRadius = 12.0
                circleColor = Color(0x55F59E0B)
            }
            CircleAnnotation(point = traveller) {
                circleRadius = 6.5
                circleColor = Color(0xFFF59E0B)
            }
            CircleAnnotation(point = traveller) {
                circleRadius = 3.5
                circleColor = Color.White
            }
        }

        if (showControls) {
            MapControlStack(
                mapStyle = mapStyle,
                showStops = showStops,
                cameraMode = cameraMode,
                isExpanded = isExpanded,
                onMapStyleToggle = onMapStyleToggle,
                onToggleStops = onToggleStops,
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
                            zoom(15.8)
                            pitch(58.0)
                            bearing(activePoint.bearing.toDouble())
                        }
                    )
                },
                onOrbit = {
                    cameraMode = CameraMode.ORBIT
                    mapViewportState.easeTo(
                        cameraOptions {
                            center(Point.fromLngLat(activePoint.longitude, activePoint.latitude))
                            zoom(15.0)
                            pitch(58.0)
                            bearing((activePoint.bearing + 55f).toDouble())
                        }
                    )
                },
                onToggleFullscreen = onToggleFullscreen,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
            )
        }

        if (showActiveBadge) {
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
}

private const val MAX_RENDER_POINTS_PER_SEGMENT = 5_000

private data class PreparedRouteSegment(
    val points: List<Point>,
    val color: Color
)

@Composable
private fun RouteAnnotations(
    points: List<RoutePoint>,
    segments: List<TransportSegment>
) {
    // Route geometry is transformed once per loaded journey, rather than filtering and converting
    // all stored points again whenever a playback frame recomposes the map.
    val routeSegments = remember(points, segments) { prepareRouteSegments(points, segments) }

    routeSegments.forEach { segment ->
        PolylineAnnotation(points = segment.points) {
            lineColor = segment.color
            lineWidth = 6.0
        }
    }
}

private fun prepareRouteSegments(
    points: List<RoutePoint>,
    segments: List<TransportSegment>
): List<PreparedRouteSegment> {
    if (points.size < 2) return emptyList()
    val pointsBySequence = points.associateBy { it.sequenceOrder }
    val sourceSegments = if (segments.isEmpty()) {
        listOf(0 to (points.last().sequenceOrder.coerceAtLeast(1)) to Color(0xFF2563EB))
    } else {
        segments.map { segment ->
            (segment.startIndex to segment.endIndex) to Color(segment.mode.hexColor)
        }
    }

    return sourceSegments.mapNotNull { (range, color) ->
        val mapPoints = (range.first..range.second)
            .mapNotNull(pointsBySequence::get)
            .map { Point.fromLngLat(it.longitude, it.latitude) }
            .thinForRendering(MAX_RENDER_POINTS_PER_SEGMENT)
        if (mapPoints.size >= 2) PreparedRouteSegment(mapPoints, color) else null
    }
}

private fun List<Point>.thinForRendering(maxPoints: Int): List<Point> {
    if (size <= maxPoints) return this
    val stride = (size - 1).toDouble() / (maxPoints - 1).toDouble()
    return List(maxPoints) { index -> this[(index * stride).toInt().coerceAtMost(lastIndex)] }
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
    onOverview: () -> Unit,
    onFollow: () -> Unit,
    onOrbit: () -> Unit,
    onToggleFullscreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
        shadowElevation = 6.dp
    ) {
        Column(
            modifier = Modifier.padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
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
                    text = "Live location · point ${currentPointIndex + 1} of $pointCount",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${String.format("%.1f", activePoint.speedKmh)} km/h",
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
            .size(36.dp)
            .testTag(testTag)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(18.dp),
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
