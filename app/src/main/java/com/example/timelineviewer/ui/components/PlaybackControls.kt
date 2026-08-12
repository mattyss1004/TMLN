package com.example.timelineviewer.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Timeline playback is designed as a compact editing deck: orient the traveller in time, scrub the
 * story, play it, then choose the pace. All gestures and shortcuts remain available from one area.
 */
@Composable
fun PlaybackControls(
    isPlaying: Boolean,
    currentIndex: Int,
    totalPoints: Int,
    currentTimestamp: Long,
    playbackSpeed: Float,
    onPlayPauseToggle: () -> Unit,
    onSeekToIndex: (Int) -> Unit,
    onSkipToStart: () -> Unit,
    onSkipToEnd: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFormatter = remember { SimpleDateFormat("EEE, MMM d", Locale.getDefault()) }
    val timeFormatter = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val safeTotalPoints = totalPoints.coerceAtLeast(1)
    val safeIndex = currentIndex.coerceIn(0, safeTotalPoints - 1)
    val progress = if (safeTotalPoints > 1) safeIndex.toFloat() / (safeTotalPoints - 1).toFloat() else 0f

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PlaybackHeader(
                isPlaying = isPlaying,
                currentTimestamp = currentTimestamp,
                dateFormatter = dateFormatter,
                timeFormatter = timeFormatter,
                safeIndex = safeIndex,
                safeTotalPoints = safeTotalPoints
            )

            TimelineScrubber(
                progress = progress,
                safeIndex = safeIndex,
                safeTotalPoints = safeTotalPoints,
                onSeekToIndex = onSeekToIndex
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlaybackIconButton(
                    icon = Icons.Default.SkipPrevious,
                    description = "Skip to Start",
                    onClick = onSkipToStart,
                    testTag = "skip_to_start"
                )
                PlaybackIconButton(
                    icon = Icons.Default.FastRewind,
                    description = "Rewind 5 points",
                    onClick = { onSeekToIndex((safeIndex - 5).coerceAtLeast(0)) },
                    testTag = "skip_back_5"
                )

                FloatingActionButton(
                    onClick = onPlayPauseToggle,
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .size(58.dp)
                        .testTag("play_pause_button")
                ) {
                    AnimatedContent(
                        targetState = isPlaying,
                        transitionSpec = { fadeIn(tween(120)) togetherWith fadeOut(tween(90)) },
                        label = "playPauseIcon"
                    ) { playing ->
                        Icon(
                            imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (playing) "Pause" else "Play",
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }

                PlaybackIconButton(
                    icon = Icons.Default.FastForward,
                    description = "Forward 5 points",
                    onClick = { onSeekToIndex((safeIndex + 5).coerceAtMost(safeTotalPoints - 1)) },
                    testTag = "skip_forward_5"
                )
                PlaybackIconButton(
                    icon = Icons.Default.SkipNext,
                    description = "Skip to End",
                    onClick = onSkipToEnd,
                    testTag = "skip_to_end"
                )
            }

            PlaybackSpeedRow(
                playbackSpeed = playbackSpeed,
                onSpeedChange = onSpeedChange
            )
        }
    }
}

@Composable
private fun PlaybackHeader(
    isPlaying: Boolean,
    currentTimestamp: Long,
    dateFormatter: SimpleDateFormat,
    timeFormatter: SimpleDateFormat,
    safeIndex: Int,
    safeTotalPoints: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.MotionPhotosOn else Icons.Default.Timeline,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Column {
                Text(
                    text = if (isPlaying) "Reliving journey" else "Timeline playback",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                val date = currentTimestamp.takeIf { it > 0 }?.let { dateFormatter.format(Date(it)) } ?: "Awaiting timeline"
                val time = currentTimestamp.takeIf { it > 0 }?.let { timeFormatter.format(Date(it)) } ?: "--:--:--"
                Text(
                    text = "$date · $time",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Text(
                text = "${safeIndex + 1} / $safeTotalPoints",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun TimelineScrubber(
    progress: Float,
    safeIndex: Int,
    safeTotalPoints: Int,
    onSeekToIndex: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Slider(
            value = safeIndex.toFloat(),
            onValueChange = { onSeekToIndex(it.toInt()) },
            valueRange = 0f..(safeTotalPoints - 1).toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("playback_slider")
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "START",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
            )
            Text(
                text = "${(progress * 100).toInt()}% EXPLORED",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "END",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
            )
        }
    }
}

@Composable
private fun PlaybackIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
        modifier = Modifier
            .size(42.dp)
            .testTag(testTag)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                modifier = Modifier.size(21.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun PlaybackSpeedRow(
    playbackSpeed: Float,
    onSpeedChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Speed,
            contentDescription = null,
            modifier = Modifier.size(17.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Pace",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(38.dp)
        )
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            listOf(0.5f, 1.0f, 1.5f, 2.0f, 4.0f).forEach { speed ->
                FilterChip(
                    selected = playbackSpeed == speed,
                    onClick = { onSpeedChange(speed) },
                    label = { Text("${speed}×", style = MaterialTheme.typography.labelSmall) },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("speed_chip_${speed}")
                )
            }
        }
    }
}
