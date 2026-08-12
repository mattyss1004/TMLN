package com.example.timelineviewer.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.timelineviewer.data.model.JourneyDetailData
import com.example.timelineviewer.ui.components.*
import java.text.SimpleDateFormat
import java.util.*

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
        if (detail.points.isNotEmpty()) detail.points[currentPointIndex.coerceIn(0, detail.points.size - 1)] else null
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
                                maxLines = 1
                            )
                            Text(
                                text = "${dateFormatter.format(Date(detail.journey.startTime))} • ${detail.journey.totalDistanceKm} km",
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
                        Button(
                            onClick = { showVideoExportDialog = true },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .testTag("open_video_export")
                        ) {
                            Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Export", style = MaterialTheme.typography.labelSmall)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(if (isFullscreen) PaddingValues(0.dp) else paddingValues)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Interactive Map Component
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(if (isFullscreen) 1f else 0.65f)
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

                    // Overlay Legend
                    TransportLegend(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(12.dp)
                    )
                }

                // Playback Controls & Timeline Statistics
                if (!isFullscreen) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.35f)
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
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

                        // Stops / Waypoints List Card
                        if (detail.stops.isNotEmpty()) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                tonalElevation = 2.dp
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = "Identified Stops (${detail.stops.size}):",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    LazyColumn(
                                        modifier = Modifier.heightIn(max = 80.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        items(detail.stops) { stop ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        imageVector = Icons.Default.Place,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(12.dp),
                                                        tint = MaterialTheme.colorScheme.error
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = stop.name,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                Text(
                                                    text = "${stop.durationSeconds / 60} min",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
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
