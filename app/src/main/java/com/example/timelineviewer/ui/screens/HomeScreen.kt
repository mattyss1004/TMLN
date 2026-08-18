package com.example.timelineviewer.ui.screens

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.timelineviewer.data.analysis.MemoryLibraryFilter
import com.example.timelineviewer.data.analysis.MemoryLibraryOrganizer
import com.example.timelineviewer.data.analysis.MemoryLibrarySort
import com.example.timelineviewer.data.model.Journey
import com.example.timelineviewer.data.model.TransportMode
import com.example.timelineviewer.ui.components.JourneyCard
import com.example.timelineviewer.ui.components.TimelineImportDialog

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
    onJourneyClick: (Long) -> Unit,
    onImportJson: suspend (String, String) -> Boolean,
    onImportDocument: suspend (Uri, String) -> Boolean,
    onAddCustomJourney: suspend (String, String, Double, Double, Double, Double, List<String>) -> Long,
    modifier: Modifier = Modifier
) {
    var showImportDialog by remember { mutableStateOf(false) }
    var isSelectionMode by rememberSaveable { mutableStateOf(false) }
    val allSelected = journeys.isNotEmpty() && selectedIds.size == journeys.size
    val favoriteCount = remember(journeys) { journeys.count { it.isFavorite } }
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
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
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

                if (isSelectionMode) {
                    item {
                        LibrarySelectionToolbar(
                            selectedCount = selectedIds.size,
                            allSelected = allSelected,
                            onSelectAllToggle = onSelectAllToggle,
                            onDeleteSelected = {
                                onDeleteSelected()
                                isSelectionMode = false
                            }
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isSelectionMode) {
                                    "Select journeys"
                                } else {
                                    when {
                                        memoryLibraryFilter.favoritesOnly -> "Favorite memories"
                                        memoryLibraryFilter.query.isNotBlank() -> "Search results"
                                        else -> "Your journeys"
                                    }
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = if (isSelectionMode) {
                                    "${selectedIds.size} selected · Tap a card to select"
                                } else {
                                    "${journeys.size} journeys · $favoriteCount favorites"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(
                            onClick = {
                                if (isSelectionMode) {
                                    onSelectAllToggle(false)
                                    isSelectionMode = false
                                } else {
                                    isSelectionMode = true
                                }
                            },
                            modifier = Modifier.testTag("toggle_selection_mode_button")
                        ) {
                            Text(if (isSelectionMode) "Done" else "Select")
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
                            isSelectionMode = isSelectionMode,
                            onSelectToggle = { onToggleSelect(journey.id) },
                            onToggleFavorite = { onToggleJourneyFavorite(journey.id) },
                            onClick = { onJourneyClick(journey.id) }
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
private fun JourneySearchField(
    searchQuery: String,
    onSearchChange: (String) -> Unit
) {
    OutlinedTextField(
        value = searchQuery,
        onValueChange = onSearchChange,
        placeholder = { Text("Search journeys") },
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
    var transportExpanded by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = filter.favoritesOnly,
                onClick = onToggleFavoritesFilter,
                label = { Text("Favorites", maxLines = 1) },
                leadingIcon = if (filter.favoritesOnly) {
                    { Icon(Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(15.dp)) }
                } else null,
                modifier = Modifier.weight(1f)
            )
            Box(modifier = Modifier.weight(1f)) {
                FilterChip(
                    selected = filter.transportMode != null,
                    onClick = { transportExpanded = true },
                    label = { Text(filter.transportMode?.label ?: "All routes", maxLines = 1) },
                    leadingIcon = { Icon(Icons.Default.Directions, contentDescription = null, modifier = Modifier.size(15.dp)) },
                    trailingIcon = { Icon(Icons.Default.ExpandMore, contentDescription = null, modifier = Modifier.size(15.dp)) },
                    modifier = Modifier.fillMaxWidth()
                )
                DropdownMenu(
                    expanded = transportExpanded,
                    onDismissRequest = { transportExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("All routes") },
                        onClick = {
                            onTransportFilterChange(null)
                            transportExpanded = false
                        },
                        leadingIcon = if (filter.transportMode == null) {
                            { Icon(Icons.Default.Check, contentDescription = null) }
                        } else null
                    )
                    TransportMode.entries.filter { it != TransportMode.UNKNOWN }.forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(mode.label) },
                            onClick = {
                                onTransportFilterChange(mode)
                                transportExpanded = false
                            },
                            leadingIcon = if (filter.transportMode == mode) {
                                { Icon(Icons.Default.Check, contentDescription = null) }
                            } else null
                        )
                    }
                }
            }
            Box {
                IconButton(
                    onClick = { sortExpanded = true },
                    modifier = Modifier.testTag("sort_button")
                ) {
                    Icon(Icons.Default.Sort, contentDescription = "Sort: ${filter.sort.label}")
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
    }
}

@Composable
private fun LibrarySelectionToolbar(
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
                    text = "$selectedCount selected",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selectedCount > 0) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = { onSelectAllToggle(!allSelected) },
                    modifier = Modifier.testTag("select_all_button")
                ) {
                    Text(if (allSelected) "Clear all" else "Select all")
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
