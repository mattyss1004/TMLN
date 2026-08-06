package com.example.timelineviewer.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.*

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
    val dateFormatter = remember { SimpleDateFormat("MMM dd, yyyy • HH:mm:ss", Locale.getDefault()) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Timestamp and Progress Counter
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (currentTimestamp > 0) dateFormatter.format(Date(currentTimestamp)) else "Time: --:--",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "${currentIndex + 1} / $totalPoints pts",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Slider Scrubber
            Slider(
                value = currentIndex.toFloat(),
                onValueChange = { onSeekToIndex(it.toInt()) },
                valueRange = 0f..(totalPoints - 1).coerceAtLeast(1).toFloat(),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("playback_slider")
            )

            // Playback Control Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onSkipToStart,
                    modifier = Modifier.testTag("skip_to_start")
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Skip to Start",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                IconButton(
                    onClick = { onSeekToIndex((currentIndex - 5).coerceAtLeast(0)) },
                    modifier = Modifier.testTag("skip_back_5")
                ) {
                    Icon(
                        imageVector = Icons.Default.FastRewind,
                        contentDescription = "Rewind 5 points",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Play / Pause FAB
                FloatingActionButton(
                    onClick = onPlayPauseToggle,
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .size(56.dp)
                        .testTag("play_pause_button")
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(28.dp)
                    )
                }

                IconButton(
                    onClick = { onSeekToIndex((currentIndex + 5).coerceAtMost(totalPoints - 1)) },
                    modifier = Modifier.testTag("skip_forward_5")
                ) {
                    Icon(
                        imageVector = Icons.Default.FastForward,
                        contentDescription = "Forward 5 points",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                IconButton(
                    onClick = onSkipToEnd,
                    modifier = Modifier.testTag("skip_to_end")
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Skip to End",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Playback Speed Selector Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Speed:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp)
                )

                listOf(0.5f, 1.0f, 1.5f, 2.0f, 4.0f).forEach { speed ->
                    FilterChip(
                        selected = playbackSpeed == speed,
                        onClick = { onSpeedChange(speed) },
                        label = { Text("${speed}x", style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .testTag("speed_chip_${speed}")
                    )
                }
            }
        }
    }
}
