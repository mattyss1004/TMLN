package com.example.timelineviewer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.timelineviewer.data.model.JourneyDetailData
import com.example.timelineviewer.data.model.Stop
import com.example.timelineviewer.ui.components.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JourneyDetailScreen(
    detail: JourneyDetailData,
    isPlaying: Boolean,
    currentPointIndex: Int,
    playbackSpeed: Float,
    onBackClick: () -> Unit,
    onPlayPauseToggle: () -> Unit,
    onSeekToIndex: (Int) -> Unit,
    onSpeedChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var mapStyle by remember { mutableStateOf(MapStyle.CINEMATIC) }
    var showStops by remember { mutableStateOf(true) }
    var isFullscreen by remember { mutableStateOf(false) }
    var showVideoExportDialog by remember { mutableStateOf(false) }

    val currentPoint = remember(detail.points, currentPointIndex) {
        detail.points.getOrNull(currentPointIndex.coerceIn(0, (detail.points.size - 1).coerceAtLeast(0)))
    }
    val dateFormatter = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }

    Scaffold(
        topBar = {
            if (!isFullscreen) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = detail.journey.title,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${dateFormatter.format(Date(detail.journey.startTime))} · ${formatDistance(detail.journey.totalDistanceKm)} km",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onBackClick,
                            modifier = Modifier.testTag("back_button")
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { showVideoExportDialog = true },
                            modifier = Modifier.testTag("open_video_export")
                        ) {
                            Icon(Icons.Default.Videocam, contentDescription = "Export video")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(if (isFullscreen) PaddingValues(0.dp) else paddingValues)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(if (isFullscreen) 1f else 0.54f)
                    .padding(if (isFullscreen) 0.dp else 12.dp)
            ) {
                InteractiveMapView(
                    points = detail.points,
                    stops = detail.stops,
                    segments = detail.segments,
                    currentPointIndex = currentPointIndex,
                    isPlaying = isPlaying,
                    mapStyle = mapStyle,
                    showStops = showStops,
                    isExpanded = isFullscreen,
                    onMapStyleToggle = {
                        mapStyle = if (mapStyle == MapStyle.CINEMATIC) MapStyle.SATELLITE else MapStyle.CINEMATIC
                    },
                    onToggleStops = { showStops = !showStops },
                    onToggleFullscreen = { isFullscreen = !isFullscreen },
                    modifier = Modifier.fillMaxSize()
                )

                TransportLegend(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp)
                )
            }

            if (!isFullscreen) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.46f),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        PlaybackControls(
                            isPlaying = isPlaying,
                            currentIndex = currentPointIndex,
                            totalPoints = detail.points.size,
                            currentTimestamp = currentPoint?.timestamp ?: 0L,
                            playbackSpeed = playbackSpeed,
                            onPlayPauseToggle = onPlayPauseToggle,
                            onSeekToIndex = onSeekToIndex,
                            onSkipToStart = { onSeekToIndex(0) },
                            onSkipToEnd = { onSeekToIndex((detail.points.size - 1).coerceAtLeast(0)) },
                            onSpeedChange = onSpeedChange
                        )
                    }

                    item {
                        JourneyStorySummary(detail)
                    }

                    if (detail.stops.isNotEmpty()) {
                        item {
                            IdentifiedStopsPanel(detail.stops)
                        }
                    }
                }
            }
        }
    }

    if (showVideoExportDialog) {
        VideoExportDialog(
            journeyTitle = detail.journey.title,
            onDismiss = { showVideoExportDialog = false }
        )
    }
}

@Composable
private fun JourneyStorySummary(detail: JourneyDetailData) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Journey at a glance",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StoryFact(
                    icon = Icons.Default.Directions,
                    label = "Main mode",
                    value = detail.journey.dominantMode.label
                )
                StoryFact(
                    icon = Icons.Default.Speed,
                    label = "Peak speed",
                    value = "${formatDistance(detail.journey.maxSpeedKmh)} km/h"
                )
                StoryFact(
                    icon = Icons.Default.Flag,
                    label = "Highlights",
                    value = "${detail.stops.count { it.importanceScore >= 80 }}"
                )
            }
            detail.journey.highlightPlaceName?.takeIf { it.isNotBlank() }?.let { place ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "Story highlight: $place",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun StoryFact(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Column(modifier = Modifier.widthIn(min = 72.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun IdentifiedStopsPanel(stops: List<Stop>) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Stops along the way",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "${stops.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            stops.sortedByDescending { it.importanceScore }.take(5).forEach { stop ->
                StopStoryRow(stop)
            }
        }
    }
}

@Composable
private fun StopStoryRow(stop: Stop) {
    val accent = if (stop.importanceScore >= 80) Color(0xFFF43F5E) else MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (stop.importanceScore >= 80) Icons.Default.Star else Icons.Default.Place,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = accent
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stop.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${stop.category} · ${formatDuration(stop.durationSeconds)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (stop.importanceScore >= 80) {
            Text(
                text = "Highlight",
                style = MaterialTheme.typography.labelSmall,
                color = accent
            )
        }
    }
}

private fun formatDistance(value: Double): String = if (value >= 100) {
    String.format(Locale.getDefault(), "%.0f", value)
} else {
    String.format(Locale.getDefault(), "%.1f", value)
}

private fun formatDuration(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes.coerceAtLeast(1)} min"
}
