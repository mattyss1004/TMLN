package com.example.timelineviewer.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.timelineviewer.data.analysis.JourneyTransportSummary
import com.example.timelineviewer.data.model.Journey
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A journey is presented as a travel story preview: where it happened, its most characteristic
 * mode of travel, and the three facts that make it worth opening on the map.
 */
@Composable
fun JourneyCard(
    journey: Journey,
    transportSummary: JourneyTransportSummary,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onSelectToggle: () -> Unit,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFormatter = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }
    val yearFormatter = remember { SimpleDateFormat("yyyy", Locale.getDefault()) }
    val formattedDuration = remember(journey.totalDurationSeconds) { formatDuration(journey.totalDurationSeconds) }
    val modeColor = Color(transportSummary.primaryMode.hexColor)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = if (isSelectionMode) onSelectToggle else onClick)
            .testTag("journey_card_${journey.id}"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 5.dp else 1.dp),
        border = if (isSelected) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            JourneyStoryTile(
                date = dateFormatter.format(Date(journey.startTime)),
                year = yearFormatter.format(Date(journey.startTime)),
                modeColor = modeColor,
                coverPhotoPath = journey.coverPhotoPath,
                modifier = Modifier.size(width = 72.dp, height = 104.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = journey.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (journey.description.isNotBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = journey.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (isSelectionMode) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { onSelectToggle() },
                            modifier = Modifier.testTag("journey_checkbox_${journey.id}")
                        )
                    } else {
                        IconButton(
                            onClick = onToggleFavorite,
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("favorite_journey_${journey.id}")
                        ) {
                            Icon(
                                imageVector = if (journey.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = if (journey.isFavorite) "Remove from favorites" else "Add to favorites",
                                tint = if (journey.isFavorite) Color(0xFFE11D48) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TransportSummaryPill(transportSummary)
                    journey.highlightPlaceName?.takeIf { it.isNotBlank() }?.let { highlight ->
                        HighlightPill(highlight)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    JourneyStat(Icons.Default.Straighten, "${formatDistance(journey.totalDistanceKm)} km")
                    JourneyStat(Icons.Default.Schedule, formattedDuration)
                    JourneyStat(Icons.Default.Place, "${journey.stopCount} stops")
                }
            }

            if (!isSelectionMode) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = "Open Journey",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun JourneyStoryTile(
    date: String,
    year: String,
    modeColor: Color,
    coverPhotoPath: String?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.verticalGradient(
                    listOf(modeColor.copy(alpha = 0.95f), modeColor.copy(alpha = 0.42f))
                )
            )
    ) {
        if (!coverPhotoPath.isNullOrBlank()) {
            AsyncImage(
                model = File(coverPhotoPath),
                contentDescription = "Cover for $date memory",
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.60f))))
            )
        } else {
            Icon(
                imageVector = Icons.Default.Explore,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.24f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(48.dp)
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp)
        ) {
            Text(
                text = date.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White
            )
            Text(
                text = year,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.82f)
            )
        }
    }
}

@Composable
private fun TransportSummaryPill(summary: JourneyTransportSummary) {
    val primaryColor = Color(summary.primaryMode.hexColor)
    Surface(
        shape = RoundedCornerShape(50),
        color = primaryColor.copy(alpha = 0.14f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            summary.modes.take(3).forEach { mode ->
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color(mode.hexColor))
                )
            }
            Text(
                text = summary.label,
                style = MaterialTheme.typography.labelSmall,
                color = primaryColor
            )
        }
    }
}

@Composable
private fun HighlightPill(highlight: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                text = highlight,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun JourneyStat(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(13.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatDuration(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return when {
        hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
        hours > 0 -> "${hours}h"
        else -> "${minutes.coerceAtLeast(1)}m"
    }
}

private fun formatDistance(distanceKm: Double): String =
    if (distanceKm >= 100) String.format(Locale.getDefault(), "%.0f", distanceKm)
    else String.format(Locale.getDefault(), "%.1f", distanceKm)
