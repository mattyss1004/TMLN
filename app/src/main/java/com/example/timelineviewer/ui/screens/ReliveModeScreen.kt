package com.example.timelineviewer.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.timelineviewer.data.analysis.ReliveMoment
import com.example.timelineviewer.data.analysis.ReliveMomentKind
import com.example.timelineviewer.data.analysis.RelivePlanner
import com.example.timelineviewer.data.model.JourneyDetailData
import com.example.timelineviewer.ui.components.InteractiveMapView
import com.example.timelineviewer.ui.map.JourneyBaseStyle
import com.example.timelineviewer.ui.map.JourneyCameraMode
import com.example.timelineviewer.ui.map.MapExperienceState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A distraction-free, one-pass replay that reuses only data held in the local journey record.
 */
@Composable
fun ReliveModeScreen(
    detail: JourneyDetailData,
    isPlaying: Boolean,
    currentPointIndex: Int,
    playbackSpeed: Float,
    stopMoment: ReliveMoment?,
    mapExperience: MapExperienceState,
    onCameraModeChange: (JourneyCameraMode) -> Unit,
    onMapStyleToggle: () -> Unit,
    onCycleSceneMood: () -> Unit,
    onToggleThreeD: () -> Unit,
    onToggleLabels: () -> Unit,
    onToggleStops: () -> Unit,
    onPlayPauseToggle: () -> Unit,
    onSeekToIndex: (Int) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val moments = remember(detail) { RelivePlanner.moments(detail) }
    val activeMoment = remember(moments, currentPointIndex) {
        RelivePlanner.activeMoment(moments, currentPointIndex)
    }
    val nextMoment = remember(moments, currentPointIndex) {
        RelivePlanner.nextMoment(moments, currentPointIndex)
    }
    val currentTimestamp = detail.points.getOrNull(currentPointIndex.coerceIn(0, detail.points.lastIndex.coerceAtLeast(0)))?.timestamp ?: 0L
    val timeFormatter = remember { SimpleDateFormat("EEE · HH:mm", Locale.getDefault()) }
    val safeTotal = detail.points.size.coerceAtLeast(1)
    val progress = if (safeTotal > 1) currentPointIndex.coerceIn(0, safeTotal - 1).toFloat() / (safeTotal - 1).toFloat() else 0f

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF09121F))
    ) {
        InteractiveMapView(
            points = detail.points,
            stops = detail.stops,
            segments = detail.segments,
            currentPointIndex = currentPointIndex,
            isPlaying = isPlaying,
            playedPointIndex = currentPointIndex,
            mapExperience = mapExperience,
            isExpanded = true,
            showControls = false,
            showActiveBadge = false,
            onCameraModeChange = onCameraModeChange,
            onMapStyleToggle = onMapStyleToggle,
            onCycleSceneMood = onCycleSceneMood,
            onToggleThreeD = onToggleThreeD,
            onToggleLabels = onToggleLabels,
            onToggleStops = onToggleStops,
            modifier = Modifier.fillMaxSize()
        )

        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 18.dp, start = 16.dp, end = 16.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.padding(6.dp).size(16.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Relive mode",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = detail.journey.title,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = onExit,
                    modifier = Modifier.testTag("close_relive_mode")
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close Relive Mode")
                }
            }
        }

        ReliveMapControlDeck(
            mapExperience = mapExperience,
            onCameraModeChange = onCameraModeChange,
            onMapStyleToggle = onMapStyleToggle,
            onCycleSceneMood = onCycleSceneMood,
            onToggleThreeD = onToggleThreeD,
            onToggleLabels = onToggleLabels,
            onToggleStops = onToggleStops,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 86.dp, start = 12.dp, end = 12.dp)
        )

        if (stopMoment != null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
                shadowElevation = 14.dp
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.padding(8.dp).size(20.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    Text(
                        text = stopMoment.title,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stopMoment.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Replay is paused here. Continue when you are ready to move on.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = onPlayPauseToggle,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("continue_relive_after_stop")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Continue journey")
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AnimatedContent(
                targetState = activeMoment,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(140)) },
                label = "reliveMoment"
            ) { moment ->
                ReliveMomentCard(
                    moment = moment,
                    currentTimestamp = currentTimestamp,
                    formattedTime = currentTimestamp.takeIf { it > 0 }?.let { timeFormatter.format(Date(it)) } ?: "Awaiting timeline"
                )
            }

            if (moments.size > 2) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp)
                ) {
                    items(moments, key = { "${it.kind}-${it.pointIndex}" }) { moment ->
                        ReliveMomentChip(
                            moment = moment,
                            selected = moment.pointIndex == activeMoment?.pointIndex,
                            onClick = { onSeekToIndex(moment.pointIndex) }
                        )
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                shadowElevation = 10.dp
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ReliveProgress(progress = progress, isPlaying = isPlaying)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Surface(
                            onClick = onPlayPauseToggle,
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(50.dp)
                                .testTag("relive_play_pause")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause relive" else "Play relive",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (isPlaying) "Reliving in motion" else "Paused on this moment",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${(progress * 100).toInt()}% of the route",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Surface(
                            onClick = { nextMoment?.let { onSeekToIndex(it.pointIndex) } },
                            enabled = nextMoment != null,
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier
                                .size(50.dp)
                                .testTag("relive_next_moment")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (nextMoment == null) Icons.Default.FastForward else Icons.Default.SkipNext,
                                    contentDescription = "Next journey moment",
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                    RelivePaceRow(playbackSpeed, onSpeedChange)
                }
            }
        }
    }
}

@Composable
private fun ReliveMapControlDeck(
    mapExperience: MapExperienceState,
    onCameraModeChange: (JourneyCameraMode) -> Unit,
    onMapStyleToggle: () -> Unit,
    onCycleSceneMood: () -> Unit,
    onToggleThreeD: () -> Unit,
    onToggleLabels: () -> Unit,
    onToggleStops: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                JourneyCameraMode.entries.forEach { mode ->
                    FilterChip(
                        selected = mapExperience.cameraMode == mode,
                        onClick = { onCameraModeChange(mode) },
                        label = { Text(mode.label, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(horizontal = 1.dp)
            ) {
                item {
                    FilterChip(
                        selected = mapExperience.baseStyle == JourneyBaseStyle.SATELLITE,
                        onClick = onMapStyleToggle,
                        label = { Text(if (mapExperience.baseStyle == JourneyBaseStyle.SATELLITE) "Satellite" else "Map", style = MaterialTheme.typography.labelSmall) }
                    )
                }
                item {
                    FilterChip(
                        selected = mapExperience.showThreeDObjects,
                        onClick = onToggleThreeD,
                        label = { Text("3D", style = MaterialTheme.typography.labelSmall) }
                    )
                }
                item {
                    FilterChip(
                        selected = mapExperience.showStops,
                        onClick = onToggleStops,
                        label = { Text("Stops", style = MaterialTheme.typography.labelSmall) }
                    )
                }
                item {
                    FilterChip(
                        selected = mapExperience.showLabels,
                        onClick = onToggleLabels,
                        label = { Text("Labels", style = MaterialTheme.typography.labelSmall) }
                    )
                }
                item {
                    FilterChip(
                        selected = mapExperience.sceneMood != com.example.timelineviewer.ui.map.JourneySceneMood.DAY,
                        onClick = onCycleSceneMood,
                        label = { Text(mapExperience.sceneMood.label, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ReliveMomentCard(moment: ReliveMoment?, currentTimestamp: Long, formattedTime: String) {
    val title = moment?.title ?: "Setting out"
    val subtitle = moment?.subtitle ?: "The journey begins here"
    val accent = when (moment?.kind) {
        ReliveMomentKind.HIGHLIGHT -> Color(0xFFF59E0B)
        ReliveMomentKind.ARRIVAL -> Color(0xFF10B981)
        else -> MaterialTheme.colorScheme.primary
    }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        shadowElevation = 10.dp
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = formattedTime,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ReliveMomentChip(moment: ReliveMoment, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
        modifier = Modifier.testTag("relive_moment_${moment.pointIndex}")
    ) {
        Text(
            text = moment.title,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun ReliveProgress(progress: Float, isPlaying: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        androidx.compose.material3.LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
            color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("DEPARTURE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("ARRIVAL", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RelivePaceRow(playbackSpeed: Float, onSpeedChange: (Float) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Pace",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        listOf(0.5f, 1f, 2f).forEach { speed ->
            Button(
                onClick = { onSpeedChange(speed) },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                shape = RoundedCornerShape(10.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = if (playbackSpeed == speed) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (playbackSpeed == speed) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.testTag("relive_speed_$speed")
            ) {
                Text("${speed}×", style = MaterialTheme.typography.labelSmall)
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = "Local replay",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
