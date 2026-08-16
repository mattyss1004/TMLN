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
import com.example.timelineviewer.data.analysis.JourneyIntelligence
import com.example.timelineviewer.data.analysis.TransportShare
import com.example.timelineviewer.data.model.JourneyDetailData
import com.example.timelineviewer.data.model.JourneyMetadataEditor
import com.example.timelineviewer.data.model.OfflineMapRegion
import com.example.timelineviewer.data.model.OfflineRegionStatus
import com.example.timelineviewer.data.model.Stop
import com.example.timelineviewer.ui.components.*
import com.example.timelineviewer.ui.map.JourneyCameraMode
import com.example.timelineviewer.ui.map.MapExperienceState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JourneyDetailScreen(
    detail: JourneyDetailData,
    isPlaying: Boolean,
    currentPointIndex: Int,
    playbackSpeed: Float,
    offlineMapRegion: OfflineMapRegion?,
    mapExperience: MapExperienceState,
    onCameraModeChange: (JourneyCameraMode) -> Unit,
    onMapStyleToggle: () -> Unit,
    onCycleSceneMood: () -> Unit,
    onToggleThreeD: () -> Unit,
    onToggleLabels: () -> Unit,
    onToggleStops: () -> Unit,
    onBackClick: () -> Unit,
    onPlayPauseToggle: () -> Unit,
    onSeekToIndex: (Int) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onOpenReliveMode: () -> Unit,
    onSaveJourneyMetadata: suspend (String, String) -> Boolean,
    onDownloadOffline: () -> Unit,
    onRemoveOffline: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFullscreen by remember { mutableStateOf(false) }
    var showVideoExportDialog by remember { mutableStateOf(false) }
    var showJourneyEditor by remember { mutableStateOf(false) }

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
                            onClick = onOpenReliveMode,
                            modifier = Modifier.testTag("open_relive_mode")
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Open Relive Mode")
                        }
                        IconButton(
                            onClick = { showJourneyEditor = true },
                            modifier = Modifier.testTag("open_journey_editor")
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit journey")
                        }
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
                    .weight(if (isFullscreen) 1f else 0.60f)
                    .padding(if (isFullscreen) 0.dp else 10.dp)
            ) {
                InteractiveMapView(
                    points = detail.points,
                    stops = detail.stops,
                    segments = detail.segments,
                    currentPointIndex = currentPointIndex,
                    isPlaying = isPlaying,
                    mapExperience = mapExperience,
                    isExpanded = isFullscreen,
                    onCameraModeChange = onCameraModeChange,
                    onMapStyleToggle = onMapStyleToggle,
                    onCycleSceneMood = onCycleSceneMood,
                    onToggleThreeD = onToggleThreeD,
                    onToggleLabels = onToggleLabels,
                    onToggleStops = onToggleStops,
                    onToggleFullscreen = { isFullscreen = !isFullscreen },
                    modifier = Modifier.fillMaxSize()
                )

                TransportLegend(
                    modes = detail.segments.map { it.mode },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(10.dp)
                )
            }

            if (!isFullscreen) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.40f),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
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
                        ReliveModeLaunchCard(onOpenReliveMode)
                    }

                    item {
                        OfflineMapPackCard(
                            region = offlineMapRegion,
                            onDownload = onDownloadOffline,
                            onRemove = onRemoveOffline
                        )
                    }

                    item {
                        JourneyBriefPanel(detail)
                    }

                    item {
                        JourneyChaptersPanel(detail)
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

    if (showJourneyEditor) {
        JourneyMetadataEditorDialog(
            initialTitle = detail.journey.title,
            initialDescription = detail.journey.description,
            onSave = onSaveJourneyMetadata,
            onDismiss = { showJourneyEditor = false }
        )
    }

    if (showVideoExportDialog) {
        VideoExportDialog(
            journeyTitle = detail.journey.title,
            onDismiss = { showVideoExportDialog = false }
        )
    }
}

@Composable
private fun ReliveModeLaunchCard(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("relive_mode_card")
    ) {
        Row(
            modifier = Modifier.padding(15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f)) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.padding(9.dp).size(20.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Relive this journey",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "Watch the route draw itself, pause at real stops, then continue the story.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f)
                )
            }
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Open Relive Mode",
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun JourneyMetadataEditorDialog(
    initialTitle: String,
    initialDescription: String,
    onSave: suspend (String, String) -> Boolean,
    onDismiss: () -> Unit
) {
    var title by remember(initialTitle) { mutableStateOf(initialTitle) }
    var description by remember(initialDescription) { mutableStateOf(initialDescription) }
    var titleError by remember { mutableStateOf<String?>(null) }
    var descriptionError by remember { mutableStateOf<String?>(null) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        icon = {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { Text("Edit journey") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Refine the story in your library. Your route, stops, Journey Brief, and offline map remain unchanged.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        titleError = null
                        saveError = null
                    },
                    label = { Text("Journey title") },
                    singleLine = true,
                    enabled = !isSaving,
                    isError = titleError != null,
                    supportingText = {
                        Text(titleError ?: "${title.length}/${JourneyMetadataEditor.MAX_TITLE_LENGTH}")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_journey_title")
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = {
                        description = it
                        descriptionError = null
                        saveError = null
                    },
                    label = { Text("Your note about this journey") },
                    placeholder = { Text("What made this journey memorable?") },
                    minLines = 3,
                    maxLines = 6,
                    enabled = !isSaving,
                    isError = descriptionError != null,
                    supportingText = {
                        Text(descriptionError ?: "${description.length}/${JourneyMetadataEditor.MAX_DESCRIPTION_LENGTH}")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_journey_description")
                )
                saveError?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val validation = JourneyMetadataEditor.validate(title, description)
                    titleError = validation.titleError
                    descriptionError = validation.descriptionError
                    val metadata = validation.metadata ?: return@Button
                    coroutineScope.launch {
                        isSaving = true
                        val saved = onSave(metadata.title, metadata.description)
                        isSaving = false
                        if (saved) onDismiss() else saveError = "This journey could not be saved. Please try again."
                    }
                },
                enabled = !isSaving,
                modifier = Modifier.testTag("save_journey_metadata")
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("Save changes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) { Text("Cancel") }
        }
    )
}

@Composable
private fun OfflineMapPackCard(
    region: OfflineMapRegion?,
    onDownload: () -> Unit,
    onRemove: () -> Unit
) {
    val isDownloading = region?.status == OfflineRegionStatus.DOWNLOADING
    val isAvailable = region?.status == OfflineRegionStatus.AVAILABLE
    val isFailed = region?.status == OfflineRegionStatus.FAILED
    val headline = when {
        isDownloading -> "Preparing offline map…"
        isAvailable -> "Map available offline"
        isFailed -> "Offline map needs attention"
        else -> "Keep this journey offline"
    }
    val message = when {
        isDownloading -> "Downloading the map styles and a focused route corridor."
        isAvailable -> "This route and its map styles are stored for your next connection-free replay."
        isFailed -> region?.lastError ?: "The map pack could not be downloaded."
        else -> "Download a focused Mapbox corridor for reliable replay without a connection."
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (isAvailable) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Icon(
                    imageVector = if (isAvailable) Icons.Default.DownloadDone else Icons.Default.DownloadForOffline,
                    contentDescription = null,
                    tint = if (isAvailable) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.primary
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(headline, style = MaterialTheme.typography.labelLarge)
                    Text(
                        message,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (isDownloading) {
                LinearProgressIndicator(
                    progress = { region?.progress ?: 0f },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "${((region?.progress ?: 0f) * 100).toInt()}% downloaded",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isAvailable) {
                    OutlinedButton(onClick = onRemove) { Text("Remove offline pack") }
                } else {
                    Button(onClick = onDownload, enabled = !isDownloading) {
                        Text(if (isFailed) "Try again" else "Download for offline")
                    }
                }
            }
        }
    }
}

@Composable
private fun JourneyBriefPanel(detail: JourneyDetailData) {
    val brief = remember(detail) { JourneyIntelligence.build(detail) }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.padding(7.dp).size(16.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Journey brief",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = brief.dateLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = brief.headline,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = brief.narrative,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BriefMetric(
                    icon = Icons.Default.Route,
                    label = "Distance",
                    value = brief.distanceLabel,
                    modifier = Modifier.weight(1f)
                )
                BriefMetric(
                    icon = Icons.Default.Schedule,
                    label = "Duration",
                    value = brief.durationLabel,
                    modifier = Modifier.weight(1f)
                )
                BriefMetric(
                    icon = Icons.Default.Flag,
                    label = "Stops",
                    value = detail.stops.size.toString(),
                    modifier = Modifier.weight(1f)
                )
            }

            if (brief.transportMix.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "How you travelled",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    brief.transportMix.take(3).forEach { share -> TransportMixRow(share) }
                }
            }

            if (brief.highlights.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Story highlights",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    brief.highlights.take(3).forEach { highlight ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = Color(0xFFF59E0B)
                            )
                            Text(
                                text = highlight.name,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = JourneyIntelligence.formatDuration(highlight.durationSeconds),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BriefMetric(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
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
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun TransportMixRow(share: TransportShare) {
    val accent = Color(share.mode.hexColor)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = when (share.mode) {
                com.example.timelineviewer.data.model.TransportMode.WALKING -> Icons.Default.DirectionsWalk
                com.example.timelineviewer.data.model.TransportMode.CYCLING -> Icons.Default.DirectionsBike
                com.example.timelineviewer.data.model.TransportMode.DRIVING -> Icons.Default.DirectionsCar
                com.example.timelineviewer.data.model.TransportMode.TRANSIT -> Icons.Default.Train
                com.example.timelineviewer.data.model.TransportMode.UNKNOWN -> Icons.Default.Route
            },
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = accent
        )
        Text(
            text = share.mode.label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "${share.sharePercent}% · ${formatDistance(share.distanceKm)} km",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun JourneyChaptersPanel(detail: JourneyDetailData) {
    val chapters = remember(detail) { JourneyIntelligence.build(detail).chapters }
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Timeline,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Journey chapters",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            chapters.forEachIndexed { index, chapter ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (chapter.isHighlight) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.primaryContainer
                            }
                        ) {
                            Icon(
                                imageVector = when {
                                    index == 0 -> Icons.Default.PlayArrow
                                    index == chapters.lastIndex -> Icons.Default.Flag
                                    chapter.isHighlight -> Icons.Default.Star
                                    else -> Icons.Default.Place
                                },
                                contentDescription = null,
                                modifier = Modifier.padding(5.dp).size(14.dp),
                                tint = if (chapter.isHighlight) {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                }
                            )
                        }
                        if (index != chapters.lastIndex) {
                            Box(
                                modifier = Modifier
                                    .width(2.dp)
                                    .height(18.dp)
                                    .background(MaterialTheme.colorScheme.outlineVariant)
                            )
                        }
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(bottom = if (index == chapters.lastIndex) 0.dp else 8.dp)
                    ) {
                        Text(
                            text = chapter.title,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = chapter.subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
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
