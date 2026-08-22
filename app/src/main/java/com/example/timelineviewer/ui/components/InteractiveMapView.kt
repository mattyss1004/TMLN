
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.timelineviewer.BuildConfig
import com.example.timelineviewer.data.model.RoutePoint
import com.example.timelineviewer.data.model.Stop
import com.example.timelineviewer.data.model.TransportSegment
import com.example.timelineviewer.ui.map.CinematicCameraDirector
import com.example.timelineviewer.ui.map.JourneyBaseStyle
import com.example.timelineviewer.ui.map.JourneyCameraMode
import com.example.timelineviewer.ui.map.JourneySceneMood
import com.example.timelineviewer.ui.map.MapExperienceState
import com.mapbox.geojson.Point
import com.mapbox.maps.MapboxExperimental
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
import com.mapbox.maps.plugin.animation.MapAnimationOptions

/**
 * A real Mapbox-based map canvas that renders imported journey geometry on the 3D Mapbox Standard
 * basemap. It keeps all of the project-specific route, stop, and playback data local while Mapbox
 * supplies the interactive terrain, buildings, labels, and camera projection.
 */
@OptIn(MapboxExperimental::class)
@Composable
fun InteractiveMapView(
    points: List<RoutePoint>,
    stops: List<Stop>,
    segments: List<TransportSegment>,
    currentPointIndex: Int = 0,
    isPlaying: Boolean = false,
    playedPointIndex: Int? = null,
    mapExperience: MapExperienceState = MapExperienceState(),
    isExpanded: Boolean = false,
    showControls: Boolean = true,
    showActiveBadge: Boolean = true,
    onCameraModeChange: (JourneyCameraMode) -> Unit = {},
    onMapStyleToggle: () -> Unit = {},
    onCycleSceneMood: () -> Unit = {},
    onToggleThreeD: () -> Unit = {},
    onToggleLabels: () -> Unit = {},
    onToggleStops: () -> Unit = {},
    onToggleFullscreen: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (points.isEmpty()) {
        EmptyMapState(modifier)
        return
    }

    if (!BuildConfig.MAPBOX_ACCESS_TOKEN_CONFIGURED) {
        MapboxConfigurationRequired(modifier)
        return
    }

    val initialPose = remember(points) {
        CinematicCameraDirector.poseFor(JourneyCameraMode.OVERVIEW, points, 0)!!
    }
    val mapViewportState = rememberMapViewportState {
        setCameraOptions {
            center(Point.fromLngLat(initialPose.longitude, initialPose.latitude))
            zoom(initialPose.zoom)
            pitch(initialPose.pitch)
            bearing(initialPose.bearing)
        }
    }

    val safeIndex = currentPointIndex.coerceIn(0, points.lastIndex)
    val activePoint = points[safeIndex]
    val activePose = remember(points, safeIndex, mapExperience.cameraMode) {
        CinematicCameraDirector.poseFor(mapExperience.cameraMode, points, safeIndex)!!
    }

    // Dynamic ease duration to align camera tracking speed with replay pace
    val cameraEaseDuration = remember(isPlaying) { if (isPlaying) 180L else 320L }

    LaunchedEffect(safeIndex, mapExperience.cameraMode) {
        if (mapExperience.cameraMode != JourneyCameraMode.OVERVIEW) {
            mapViewportState.easeTo(
                cameraOptions = activePose.toMapboxCameraOptions(),
                animationOptions = MapAnimationOptions.mapAnimationOptions {
                    duration(cameraEaseDuration)
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
                key(
                    mapExperience.baseStyle,
                    mapExperience.sceneMood,
                    mapExperience.showThreeDObjects,
                    mapExperience.showLabels
                ) {
                    if (mapExperience.baseStyle == JourneyBaseStyle.SATELLITE) {
                        MapboxStandardSatelliteStyle(
                            standardSatelliteStyleState = rememberStandardSatelliteStyleState {
                                configurationsState.apply {
                                    lightPreset = mapExperience.sceneMood.toLightPreset()
                                    showPlaceLabels = BooleanValue(mapExperience.showLabels)
                                    showRoadLabels = BooleanValue(mapExperience.showLabels)
                                    showPointOfInterestLabels = BooleanValue(mapExperience.showLabels)
                                }
                            }
                        )
                    } else {
                        MapboxStandardStyle(
                            standardStyleState = rememberStandardStyleState {
                                configurationsState.apply {
                                    show3dObjects = BooleanValue(mapExperience.showThreeDObjects)
                                    lightPreset = mapExperience.sceneMood.toLightPreset()
                                    showPlaceLabels = BooleanValue(mapExperience.showLabels)
                                    showRoadLabels = BooleanValue(mapExperience.showLabels)
                                    showPointOfInterestLabels = BooleanValue(mapExperience.showLabels)
                                }
                            }
                        )
                    }
                }
            }
        ) {
            RouteAnnotations(
                points = points,
                segments = segments,
                playedPointIndex = (playedPointIndex ?: safeIndex).takeIf { mapExperience.progressiveRouteEnabled }
            )

            if (mapExperience.showStops) {
                stops.forEach { stop ->
                    StopAnnotation(stop)
                }
            }

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
                mapExperience = mapExperience,
                isExpanded = isExpanded,
                onCameraModeSelect = { mode ->
                    onCameraModeChange(mode)
                    val pose = CinematicCameraDirector.poseFor(mode, points, safeIndex)!!
                    mapViewportState.easeTo(
                        cameraOptions = pose.toMapboxCameraOptions(),
                        animationOptions = MapAnimationOptions.mapAnimationOptions {
                            duration(400L)
                        }
                    )
                },
                onMapStyleToggle = onMapStyleToggle,
                onCycleSceneMood = onCycleSceneMood,
                onToggleThreeD = onToggleThreeD,
                onToggleLabels = onToggleLabels,
                onToggleStops = onToggleStops,
                onToggleFullscreen = onToggleFullscreen,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
            )
        }

        if (showActiveBadge) {
            ActiveLocationBadge(
                activePoint = activePoint,
                currentPointIndex = safeIndex,
                pointCount = points.size,
                cameraMode = mapExperience.cameraMode,
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
    segments: List<TransportSegment>,
    playedPointIndex: Int?
) {
    val routeSegments = remember(points, segments) { prepareRouteSegments(points, segments) }
    val allRoutePoints = remember(points) {
        points.map { Point.fromLngLat(it.longitude, it.latitude) }
    }

    routeSegments.forEach { segment ->
        PolylineAnnotation(points = segment.points) {
            lineColor = segment.color
            lineWidth = if (playedPointIndex == null) 6.0 else 4.0
            lineOpacity = if (playedPointIndex == null) 1.0 else 0.24
        }
    }

    if (playedPointIndex != null) {
        val travelled = remember(allRoutePoints, playedPointIndex) {
            visibleRouteProgress(allRoutePoints, playedPointIndex)
        }
        if (travelled.size >= 2) {
            PolylineAnnotation(points = travelled) {
                lineColor = Color(0xFFF59E0B)
                lineWidth = 7.0
                lineOpacity = 0.96
            }
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

private const val MAX_PLAYED_ROUTE_POINTS = 800

private fun visibleRouteProgress(points: List<Point>, playedPointIndex: Int): List<Point> {
    if (points.isEmpty()) return emptyList()
    val inclusiveEnd = playedPointIndex.coerceIn(0, points.lastIndex)
    return points.subList(0, inclusiveEnd + 1).thinForRendering(MAX_PLAYED_ROUTE_POINTS)
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
    mapExperience: MapExperienceState,
    isExpanded: Boolean,
    onCameraModeSelect: (JourneyCameraMode) -> Unit,
    onMapStyleToggle: () -> Unit,
    onCycleSceneMood: () -> Unit,
    onToggleThreeD: () -> Unit,
    onToggleLabels: () -> Unit,
    onToggleStops: () -> Unit,
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
                icon = if (mapExperience.baseStyle == JourneyBaseStyle.STANDARD) Icons.Default.SatelliteAlt else Icons.Default.Map,
                contentDescription = "Toggle satellite or standard map basemap",
                onClick = onMapStyleToggle,
                selected = mapExperience.baseStyle == JourneyBaseStyle.SATELLITE,
                testTag = "map_style_toggle"
            )
            MapControlButton(
                icon = Icons.Default.AutoAwesome,
                contentDescription = "Cycle map lighting mood between day, dusk, night, dawn",
                onClick = onCycleSceneMood,
                selected = mapExperience.sceneMood != JourneySceneMood.DAY,
                testTag = "cycle_scene_mood"
            )
            MapControlButton(
                icon = Icons.Default.LocationCity,
                contentDescription = "Toggle 3D buildings and terrain objects",
                onClick = onToggleThreeD,
                selected = mapExperience.showThreeDObjects,
                testTag = "toggle_3d_objects"
            )
            MapControlButton(
                icon = if (mapExperience.showLabels) Icons.Default.Label else Icons.Default.LabelOff,
                contentDescription = "Toggle map place and street labels",
                onClick = onToggleLabels,
                selected = mapExperience.showLabels,
                testTag = "toggle_labels"
            )
            MapControlButton(
                icon = if (mapExperience.showStops) Icons.Default.Place else Icons.Default.LocationOff,
                contentDescription = "Toggle visibility of journey stop markers",
                onClick = onToggleStops,
                selected = mapExperience.showStops,
                testTag = "toggle_stops"
            )
            MapControlButton(
                icon = Icons.Default.Map,
                contentDescription = "Set camera mode to overall route overview",
                onClick = { onCameraModeSelect(JourneyCameraMode.OVERVIEW) },
                selected = mapExperience.cameraMode == JourneyCameraMode.OVERVIEW,
                testTag = "camera_overview"
            )
            MapControlButton(
                icon = Icons.Default.MyLocation,
                contentDescription = "Set camera mode to follow traveller position",
                onClick = { onCameraModeSelect(JourneyCameraMode.FOLLOW) },
                selected = mapExperience.cameraMode == JourneyCameraMode.FOLLOW,
                testTag = "camera_follow"
            )
            MapControlButton(
                icon = Icons.Default.AutoAwesome,
                contentDescription = "Set cinematic dynamic tracking camera mode",
                onClick = { onCameraModeSelect(JourneyCameraMode.CINEMA) },
                selected = mapExperience.cameraMode == JourneyCameraMode.CINEMA,
                testTag = "camera_cinema"
            )
            MapControlButton(
                icon = Icons.Default.Refresh,
                contentDescription = "Set orbiting dynamic camera mode",
                onClick = { onCameraModeSelect(JourneyCameraMode.ORBIT) },
                selected = mapExperience.cameraMode == JourneyCameraMode.ORBIT,
                testTag = "camera_orbit"
            )
            MapControlButton(
                icon = if (isExpanded) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                contentDescription = "Toggle fullscreen map expand mode",
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
    cameraMode: JourneyCameraMode,
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
                    text = cameraMode.label + " camera",
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
            .size(40.dp)
            .testTag(testTag)
            .semantics {
                this.contentDescription = contentDescription
                this.role = Role.Button
            }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

private fun com.example.timelineviewer.ui.map.JourneyCameraPose.toMapboxCameraOptions() = cameraOptions {
    center(Point.fromLngLat(this@toMapboxCameraOptions.longitude, this@toMapboxCameraOptions.latitude))
    zoom(this@toMapboxCameraOptions.zoom)
    pitch(this@toMapboxCameraOptions.pitch)
    bearing(this@toMapboxCameraOptions.bearing)
}

private fun JourneySceneMood.toLightPreset(): LightPresetValue = when (this) {
    JourneySceneMood.DAY -> LightPresetValue.DAY
    JourneySceneMood.DUSK -> LightPresetValue.DUSK
    JourneySceneMood.NIGHT -> LightPresetValue.NIGHT
    JourneySceneMood.DAWN -> LightPresetValue.DAWN
}
