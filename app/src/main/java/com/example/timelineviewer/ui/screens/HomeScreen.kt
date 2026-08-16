package com.example.timelineviewer.ui.screens

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import com.example.timelineviewer.data.analysis.MemoryLibraryFilter
import com.example.timelineviewer.data.analysis.MemoryLibraryOrganizer
import com.example.timelineviewer.data.analysis.MemoryLibrarySort
import com.example.timelineviewer.data.model.Journey
import com.example.timelineviewer.data.model.TransportMode
import com.example.timelineviewer.ui.components.JourneyCard
import com.example.timelineviewer.ui.components.TimelineImportDialog
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    journeys: List<Journey>,
    selectedIds: Set<Long>,
    memoryLibraryFilter: MemoryLibraryFilter,
    isDarkTheme: Boolean,
    onSearchChange: (String) -> Unit,
    onToggleFavoritesFilter: () -> Unit,
    onTransportFilterChange: (TransportMode?) -> Unit,
    onSortChange: (MemoryLibrarySort) -> Unit,
    onToggleJourneyFavorite: (Long) -> Unit,
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
    val favoriteCount = remember(journeys) { journeys.count { it.isFavorite } }
    val featuredJourney = remember(journeys) { journeys.maxByOrNull { it.totalDistanceKm } }
    val archiveSections = remember(journeys) { MemoryLibraryOrganizer.sections(journeys) }

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
                searchQuery = memoryLibraryFilter.query,
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
                        searchQuery = memoryLibraryFilter.query,
                        onSearchChange = onSearchChange
                    )
                }

                item {
                    MemoryLibraryControls(
                        filter = memoryLibraryFilter,
                        onToggleFavoritesFilter = onToggleFavoritesFilter,
                        onTransportFilterChange = onTransportFilterChange,
                        onSortChange = onSortChange
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
                                text = when {
                                    memoryLibraryFilter.favoritesOnly -> "Favorite memories"
                                    memoryLibraryFilter.query.isNotBlank() -> "Search results"
                                    else -> "Your travel stories"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "${memoryLibraryFilter.sort.label} · $favoriteCount favorites in view",
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

                archiveSections.forEach { section ->
                    item(key = "section_${section.title}") {
                        Text(
                            text = section.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                        )
                    }
                    items(section.journeys, key = { it.id }) { journey ->
                        JourneyCard(
                            journey = journey,
                            isSelected = selectedIds.contains(journey.id),
                            onSelectToggle = { onToggleSelect(journey.id) },
                            onToggleFavorite = { onToggleJourneyFavorite(journey.id) },
                            onClick = { onJourneyClick(journey.id) },
                            onDelete = { onDeleteJourney(journey.id) }
                        )
                    }
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
private fun MemoryLibraryControls(
    filter: MemoryLibraryFilter,
    onToggleFavoritesFilter: () -> Unit,
    onTransportFilterChange: (TransportMode?) -> Unit,
    onSortChange: (MemoryLibrarySort) -> Unit
) {
    var sortExpanded by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Archive view",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Box {
                    TextButton(onClick = { sortExpanded = true }) {
                        Icon(Icons.Default.Sort, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(filter.sort.label)
                    }
                    DropdownMenu(expanded = sortExpanded, onDismissRequest = { sortExpanded = false }) {
                        MemoryLibrarySort.entries.forEach { sort ->
                            DropdownMenuItem(
                                text = { Text(sort.label) },
                                onClick = {
                                    onSortChange(sort)
                                    sortExpanded = false
                                },
                                leadingIcon = if (filter.sort == sort) {
                                    { Icon(Icons.Default.Check, contentDescription = null) }
                                } else null
                            )
                        }
                    }
                }
            }
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                item {
                    FilterChip(
                        selected = filter.favoritesOnly,
                        onClick = onToggleFavoritesFilter,
                        label = { Text("Favorites") },
                        leadingIcon = if (filter.favoritesOnly) {
                            { Icon(Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(15.dp)) }
                        } else null
                    )
                }
                item {
                    FilterChip(
                        selected = filter.transportMode == null,
                        onClick = { onTransportFilterChange(null) },
                        label = { Text("All routes") }
                    )
                }
                items(TransportMode.entries.filter { it != TransportMode.UNKNOWN }) { mode ->
                    FilterChip(
                        selected = filter.transportMode == mode,
                        onClick = { onTransportFilterChange(mode) },
                        label = { Text(mode.label) }
                    )
                }
            }
        }
    }
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
