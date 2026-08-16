package com.example.timelineviewer

import android.os.Bundle
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.timelineviewer.ui.screens.HomeScreen
import com.example.timelineviewer.ui.screens.JourneyDetailScreen
import com.example.timelineviewer.ui.screens.ReliveModeScreen
import com.example.timelineviewer.ui.theme.TimelineViewerTheme
import com.example.timelineviewer.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val isDarkTheme by viewModel.isDarkTheme.collectAsStateWithLifecycle()
            val journeys by viewModel.journeys.collectAsStateWithLifecycle()
            val selectedIds by viewModel.selectedJourneyIds.collectAsStateWithLifecycle()
            val memoryLibraryFilter by viewModel.memoryLibraryFilter.collectAsStateWithLifecycle()

            val activeDetail by viewModel.activeJourneyDetail.collectAsStateWithLifecycle()
            val activeOfflineMapRegion by viewModel.activeOfflineMapRegion.collectAsStateWithLifecycle()
            val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
            val isReliveMode by viewModel.isReliveMode.collectAsStateWithLifecycle()
            val mapExperience by viewModel.mapExperience.collectAsStateWithLifecycle()
            val reliveStopMoment by viewModel.reliveStopMoment.collectAsStateWithLifecycle()
            val currentPointIndex by viewModel.currentPointIndex.collectAsStateWithLifecycle()
            val playbackSpeed by viewModel.playbackSpeed.collectAsStateWithLifecycle()

            TimelineViewerTheme(darkTheme = isDarkTheme, dynamicColor = false) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (activeDetail != null) {
                        if (isReliveMode) {
                            ReliveModeScreen(
                                detail = activeDetail!!,
                                isPlaying = isPlaying,
                                currentPointIndex = currentPointIndex,
                                playbackSpeed = playbackSpeed,
                                stopMoment = reliveStopMoment,
                                mapExperience = mapExperience,
                                onCameraModeChange = { viewModel.selectMapCamera(it) },
                                onMapStyleToggle = { viewModel.toggleMapBaseStyle() },
                                onCycleSceneMood = { viewModel.cycleMapSceneMood() },
                                onToggleThreeD = { viewModel.toggleMapThreeDObjects() },
                                onToggleLabels = { viewModel.toggleMapLabels() },
                                onToggleStops = { viewModel.toggleMapStops() },
                                onPlayPauseToggle = { viewModel.togglePlayPause() },
                                onSeekToIndex = { viewModel.seekToIndex(it) },
                                onSpeedChange = { viewModel.setSpeed(it) },
                                onExit = { viewModel.endReliveMode() }
                            )
                        } else {
                            JourneyDetailScreen(
                                detail = activeDetail!!,
                                isPlaying = isPlaying,
                                currentPointIndex = currentPointIndex,
                                playbackSpeed = playbackSpeed,
                                offlineMapRegion = activeOfflineMapRegion,
                                mapExperience = mapExperience,
                                onCameraModeChange = { viewModel.selectMapCamera(it) },
                                onMapStyleToggle = { viewModel.toggleMapBaseStyle() },
                                onCycleSceneMood = { viewModel.cycleMapSceneMood() },
                                onToggleThreeD = { viewModel.toggleMapThreeDObjects() },
                                onToggleLabels = { viewModel.toggleMapLabels() },
                                onToggleStops = { viewModel.toggleMapStops() },
                                onBackClick = { viewModel.clearActiveJourney() },
                                onPlayPauseToggle = { viewModel.togglePlayPause() },
                                onSeekToIndex = { viewModel.seekToIndex(it) },
                                onSpeedChange = { viewModel.setSpeed(it) },
                                onOpenReliveMode = { viewModel.beginReliveMode() },
                                onToggleFavorite = { viewModel.toggleActiveJourneyFavorite() },
                                onSetJourneyCover = { uri -> viewModel.setActiveJourneyCover(uri) },
                                onRemoveJourneyCover = { viewModel.removeActiveJourneyCover() },
                                onSaveJourneyMetadata = { title, description ->
                                    viewModel.updateActiveJourneyMetadata(title, description)
                                },
                                onDownloadOffline = { viewModel.downloadActiveJourneyForOfflineUse() },
                                onRemoveOffline = { viewModel.removeActiveJourneyOfflinePack() }
                            )
                        }
                    } else {
                        HomeScreen(
                            journeys = journeys,
                            selectedIds = selectedIds,
                            memoryLibraryFilter = memoryLibraryFilter,
                            isDarkTheme = isDarkTheme,
                            onSearchChange = { viewModel.setMemoryLibraryQuery(it) },
                            onToggleFavoritesFilter = { viewModel.toggleMemoryLibraryFavorites() },
                            onTransportFilterChange = { viewModel.setMemoryLibraryTransportMode(it) },
                            onSortChange = { viewModel.setMemoryLibrarySort(it) },
                            onToggleJourneyFavorite = { viewModel.toggleJourneyFavorite(it) },
                            onToggleTheme = { viewModel.toggleTheme() },
                            onToggleSelect = { viewModel.toggleJourneySelection(it) },
                            onSelectAllToggle = { viewModel.selectAllJourneys(it) },
                            onDeleteSelected = { viewModel.deleteSelectedJourneys() },
                            onDeleteJourney = { viewModel.deleteJourney(it) },
                            onJourneyClick = { viewModel.loadJourneyDetail(it) },
                            onImportJson = { json, title -> viewModel.importTimelineJson(json, title) },
                            onImportDocument = { uri: Uri, title -> viewModel.importTimelineDocument(uri, title) },
                            onAddCustomJourney = { title, desc, sLat, sLng, eLat, eLng, stops ->
                                viewModel.addCustomJourney(title, desc, sLat, sLng, eLat, eLng, stops)
                            }
                        )
                    }
                }
            }
        }
    }
}
