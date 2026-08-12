package com.example.timelineviewer.ui.screens

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.timelineviewer.R
import com.example.timelineviewer.data.model.Journey
import com.example.timelineviewer.ui.components.JourneyCard
import com.example.timelineviewer.ui.components.TimelineImportDialog
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    journeys: List<Journey>,
    selectedIds: Set<Long>,
    searchQuery: String,
    isDarkTheme: Boolean,
    onSearchChange: (String) -> Unit,
    onToggleTheme: () -> Unit,
    onToggleSelect: (Long) -> Unit,
    onSelectAllToggle: (Boolean) -> Unit,
    onDeleteSelected: () -> Unit,
    onDeleteJourney: (Long) -> Unit,
    onJourneyClick: (Long) -> Unit,
    onImportJson: suspend (String, String) -> Boolean,
    onImportDocument: suspend (Uri, String) -> Boolean,
    onAddCustomJourney: suspend (String, String, Double, Double, Double, Double, List<String>) -> Long,
    modifier: Modifier = Modifier
) {
    var showImportDialog by remember { mutableStateOf(false) }
    val allSelected = journeys.isNotEmpty() && selectedIds.size == journeys.size
    val totalKm = remember(journeys) { journeys.sumOf { it.totalDistanceKm } }
    val totalStops = remember(journeys) { journeys.sumOf { it.stopCount } }
    val featuredJourney = remember(journeys) { journeys.maxByOrNull { it.totalDistanceKm } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "TMLN",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            letterSpacing = MaterialTheme.typography.titleLarge.letterSpacing
                        )
                        Text(
                            text = "Your journeys, brought back to life",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onToggleTheme,
                        modifier = Modifier.testTag("theme_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Toggle Theme"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showImportDialog = true },
                icon = { Icon(Icons.Default.CloudUpload, contentDescription = null) },
                text = { Text("Import history") },
                shape = RoundedCornerShape(16.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("fab_import_timeline")
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        if (journeys.isEmpty()) {
            EmptyJourneyLibrary(
                searchQuery = searchQuery,
                onImport = { showImportDialog = true },
                modifier = modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            )
        } else {
            LazyColumn(
                modifier = modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    JourneyLibraryHero(
                        journeyCount = journeys.size,
                        totalKm = totalKm,
                        totalStops = totalStops,
                        featuredJourneyTitle = featuredJourney?.title
                    )
                }

                item {
                    JourneySearchField(
                        searchQuery = searchQuery,
                        onSearchChange = onSearchChange
                    )
                }

                item {
                    LibrarySelectionToolbar(
                        journeyCount = journeys.size,
                        selectedCount = selectedIds.size,
                        allSelected = allSelected,
                        onSelectAllToggle = onSelectAllToggle,
                        onDeleteSelected = onDeleteSelected
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = if (searchQuery.isBlank()) "Your travel stories" else "Search results",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "Newest journeys first",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoStories,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "${journeys.size}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                items(journeys, key = { it.id }) { journey ->
                    JourneyCard(
                        journey = journey,
                        isSelected = selectedIds.contains(journey.id),
                        onSelectToggle = { onToggleSelect(journey.id) },
                        onClick = { onJourneyClick(journey.id) },
                        onDelete = { onDeleteJourney(journey.id) }
                    )
                }
            }
        }
    }

    if (showImportDialog) {
        TimelineImportDialog(
            onDismiss = { showImportDialog = false },
            onImportJson = onImportJson,
            onImportDocument = onImportDocument,
            onAddCustomJourney = onAddCustomJourney
        )
    }
}

@Composable
private fun JourneyLibraryHero(
    journeyCount: Int,
    totalKm: Double,
    totalStops: Int,
    featuredJourneyTitle: String?
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(208.dp)
            .clip(RoundedCornerShape(26.dp))
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_hero_banner_1785203219328),
            contentDescription = "Cinematic travel history",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.08f),
                            Color.Black.copy(alpha = 0.86f)
                        )
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                text = "A life in motion",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )
            Text(
                text = featuredJourneyTitle?.let { "Featured: $it" } ?: "Your personal travel archive",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.88f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(5.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeroMetric("$journeyCount", "journeys")
                HeroMetric(formatDistance(totalKm), "kilometres")
                HeroMetric("$totalStops", "stops")
            }
        }
    }
}

@Composable
private fun HeroMetric(value: String, label: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.White.copy(alpha = 0.16f)
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.78f)
            )
        }
    }
}

@Composable
private fun JourneySearchField(
    searchQuery: String,
    onSearchChange: (String) -> Unit
) {
    OutlinedTextField(
        value = searchQuery,
        onValueChange = onSearchChange,
        placeholder = { Text("Search places, routes, or moments") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (searchQuery.isNotEmpty()) {
                IconButton(onClick = { onSearchChange("") }) {
                    Icon(Icons.Default.Clear, contentDescription = "Clear search")
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("search_input")
    )
}

@Composable
private fun LibrarySelectionToolbar(
    journeyCount: Int,
    selectedCount: Int,
    allSelected: Boolean,
    onSelectAllToggle: (Boolean) -> Unit,
    onDeleteSelected: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selectedCount > 0) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (selectedCount > 0) Icons.Default.CheckCircle else Icons.Default.Tune,
                    contentDescription = null,
                    modifier = Modifier.size(17.dp),
                    tint = if (selectedCount > 0) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(7.dp))
                Text(
                    text = if (selectedCount > 0) "$selectedCount selected" else "$journeyCount journeys ready to explore",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selectedCount > 0) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = { onSelectAllToggle(!allSelected) },
                    modifier = Modifier.testTag("select_all_button")
                ) {
                    Text(if (allSelected) "Clear" else "Select")
                }
                AnimatedVisibility(visible = selectedCount > 0) {
                    IconButton(
                        onClick = onDeleteSelected,
                        modifier = Modifier.testTag("delete_selected_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete selected journeys",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyJourneyLibrary(
    searchQuery: String,
    onImport: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (searchQuery.isNotBlank()) Icons.Default.SearchOff else Icons.Default.Explore,
                        contentDescription = null,
                        modifier = Modifier.size(34.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Text(
                    text = if (searchQuery.isNotBlank()) "No stories found" else "Your journey library is waiting",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (searchQuery.isNotBlank()) {
                        "Try another place, route, or journey name."
                    } else {
                        "Import a Google Timeline export and turn your movement history into a map you can relive."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (searchQuery.isBlank()) {
                    Button(
                        onClick = onImport,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.testTag("empty_state_import_button")
                    ) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Import timeline history")
                    }
                }
            }
        }
    }
}

private fun formatDistance(distanceKm: Double): String = when {
    distanceKm >= 100 -> distanceKm.roundToInt().toString()
    else -> String.format(java.util.Locale.getDefault(), "%.1f", distanceKm)
}
