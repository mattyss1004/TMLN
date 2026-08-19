package com.example.timelineviewer.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.timelineviewer.data.analysis.MemoryLibraryFilter
import com.example.timelineviewer.data.analysis.MemoryLibraryOrganizer
import com.example.timelineviewer.data.analysis.MemoryLibrarySort
import com.example.timelineviewer.data.analysis.JourneyLibraryItem
import com.example.timelineviewer.data.analysis.JourneyTransportSummarizer
import com.example.timelineviewer.data.analysis.RelivePlaybackClock
import com.example.timelineviewer.data.analysis.ReliveMoment
import com.example.timelineviewer.data.analysis.ReliveMomentKind
import com.example.timelineviewer.data.analysis.RelivePlanner
import com.example.timelineviewer.data.analysis.ReplaySessionReducer
import com.example.timelineviewer.data.analysis.ReplaySessionState
import com.example.timelineviewer.data.analysis.ReplayStatus
import com.example.timelineviewer.data.local.AppDatabase
import com.example.timelineviewer.data.model.Journey
import com.example.timelineviewer.data.model.JourneyDetailData
import com.example.timelineviewer.data.model.JourneyMetadataEditor
import com.example.timelineviewer.data.model.OfflineMapRegion
import com.example.timelineviewer.data.model.OfflineRegionStatus
import com.example.timelineviewer.data.model.TransportMode
import com.example.timelineviewer.data.repository.JourneyRepository
import com.example.timelineviewer.data.seed.SampleDataSeeder
import com.example.timelineviewer.data.service.JourneyCoverPhotoStore
import com.example.timelineviewer.data.service.OfflineMapPackManager
import com.example.timelineviewer.ui.map.JourneyCameraMode
import com.example.timelineviewer.ui.map.MapExperienceReducer
import com.example.timelineviewer.ui.map.MapExperienceState
import com.mapbox.maps.Style
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(private val app: Application) : AndroidViewModel(app) {

    private val repository: JourneyRepository
    private val offlineMapPackManager by lazy { OfflineMapPackManager(app) }
    private val coverPhotoStore by lazy { JourneyCoverPhotoStore(app) }

    val selectedJourneyIds = MutableStateFlow<Set<Long>>(emptySet())
    val memoryLibraryFilter = MutableStateFlow(MemoryLibraryFilter())
    val isDarkTheme = MutableStateFlow(true)

    private val _activeJourneyDetail = MutableStateFlow<JourneyDetailData?>(null)
    val activeJourneyDetail: StateFlow<JourneyDetailData?> = _activeJourneyDetail.asStateFlow()

    private val _activeOfflineMapRegion = MutableStateFlow<OfflineMapRegion?>(null)
    val activeOfflineMapRegion: StateFlow<OfflineMapRegion?> = _activeOfflineMapRegion.asStateFlow()

    // Map and playback state. Legacy UI flows remain derived from these contracts until the
    // journey-detail controls are migrated to consume the richer state directly.
    private val _mapExperience = MutableStateFlow(MapExperienceReducer.forJourneyDetail())
    val mapExperience: StateFlow<MapExperienceState> = _mapExperience.asStateFlow()

    private val _replaySession = MutableStateFlow(ReplaySessionState())
    val replaySession: StateFlow<ReplaySessionState> = _replaySession.asStateFlow()
    val isPlaying = MutableStateFlow(false)
    val isReliveMode = MutableStateFlow(false)
    private val _reliveStopMoment = MutableStateFlow<ReliveMoment?>(null)
    val reliveStopMoment: StateFlow<ReliveMoment?> = _reliveStopMoment.asStateFlow()
    val currentPointIndex = MutableStateFlow(0)
    val playbackSpeed = MutableStateFlow(1f)

    private var playbackJob: Job? = null
    val journeys: StateFlow<List<Journey>>
    val libraryItems: StateFlow<List<JourneyLibraryItem>>

    init {
        val db = AppDatabase.getDatabase(app)
        repository = JourneyRepository(db)

        viewModelScope.launch {
            SampleDataSeeder.seedIfEmpty(db)
        }

        journeys = combine(repository.allJourneys, memoryLibraryFilter) { list, filter ->
            MemoryLibraryOrganizer.apply(list, filter)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        libraryItems = combine(journeys, repository.allTransportSegments) { visibleJourneys, segments ->
            val segmentsByJourney = segments.groupBy { it.journeyId }
            visibleJourneys.map { journey ->
                JourneyLibraryItem(
                    journey = journey,
                    transportSummary = JourneyTransportSummarizer.fromSegments(
                        segments = segmentsByJourney[journey.id].orEmpty(),
                        fallbackMode = journey.dominantMode
                    )
                )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    }

    fun toggleTheme() {
        isDarkTheme.value = !isDarkTheme.value
    }

    fun setMemoryLibraryQuery(query: String) {
        memoryLibraryFilter.value = memoryLibraryFilter.value.copy(query = query)
    }

    fun toggleMemoryLibraryFavorites() {
        memoryLibraryFilter.value = memoryLibraryFilter.value.copy(
            favoritesOnly = !memoryLibraryFilter.value.favoritesOnly
        )
    }

    fun setMemoryLibraryTransportMode(mode: TransportMode?) {
        memoryLibraryFilter.value = memoryLibraryFilter.value.copy(transportMode = mode)
    }

    fun setMemoryLibrarySort(sort: MemoryLibrarySort) {
        memoryLibraryFilter.value = memoryLibraryFilter.value.copy(sort = sort)
    }

    fun toggleJourneyFavorite(id: Long) {
        viewModelScope.launch {
            val journey = journeys.value.firstOrNull { it.id == id } ?: return@launch
            repository.updateJourneyFavorite(id, !journey.isFavorite)
        }
    }

    fun toggleJourneySelection(id: Long) {
        selectedJourneyIds.value = selectedJourneyIds.value.toMutableSet().apply {
            if (!add(id)) remove(id)
        }
    }

    fun selectAllJourneys(selectAll: Boolean) {
        selectedJourneyIds.value = if (selectAll) journeys.value.map { it.id }.toSet() else emptySet()
    }

    fun deleteSelectedJourneys() {
        viewModelScope.launch {
            val ids = selectedJourneyIds.value.toList()
            val covers = ids.mapNotNull { repository.getJourneyCoverPath(it) }
            removeOfflinePacks(ids)
            repository.deleteJourneys(ids)
            coverPhotoStore.deleteAll(covers)
            selectedJourneyIds.value = emptySet()
            if (_activeJourneyDetail.value?.journey?.id in ids) {
                _activeJourneyDetail.value = null
                _activeOfflineMapRegion.value = null
            }
        }
    }

    fun deleteJourney(id: Long) {
        viewModelScope.launch {
            val coverPath = repository.getJourneyCoverPath(id)
            removeOfflinePacks(listOf(id))
            repository.deleteJourney(id)
            coverPhotoStore.delete(coverPath)
            selectedJourneyIds.value = selectedJourneyIds.value - id
            if (_activeJourneyDetail.value?.journey?.id == id) {
                _activeJourneyDetail.value = null
                _activeOfflineMapRegion.value = null
            }
        }
    }

    fun deleteAllJourneys() {
        viewModelScope.launch {
            val coverPaths = repository.getAllJourneyCoverPaths()
            removeOfflinePacks(journeys.value.map { it.id })
            repository.deleteAllJourneys()
            coverPhotoStore.deleteAll(coverPaths)
            selectedJourneyIds.value = emptySet()
            _activeJourneyDetail.value = null
            _activeOfflineMapRegion.value = null
        }
    }

    fun loadJourneyDetail(id: Long) {
        viewModelScope.launch {
            pausePlayback()
            isReliveMode.value = false
            _mapExperience.value = MapExperienceReducer.forJourneyDetail()
            publishReplay(ReplaySessionState())
            _activeJourneyDetail.value = repository.getJourneyDetail(id)
            _activeOfflineMapRegion.value = repository.getOfflineMapRegion(id)
        }
    }

    fun clearActiveJourney() {
        pausePlayback()
        isReliveMode.value = false
        _mapExperience.value = MapExperienceReducer.forJourneyDetail()
        publishReplay(ReplaySessionState())
        _activeJourneyDetail.value = null
        _activeOfflineMapRegion.value = null
    }

    /** Saves a title and description without replacing the route, stops, or offline map pack. */
    suspend fun updateActiveJourneyMetadata(title: String, description: String): Boolean {
        val detail = _activeJourneyDetail.value ?: return false
        val metadata = JourneyMetadataEditor.validate(title, description).metadata ?: return false
        val saved = repository.updateJourneyMetadata(detail.journey.id, metadata)
        if (saved) {
            _activeJourneyDetail.value = detail.copy(
                journey = detail.journey.copy(
                    title = metadata.title,
                    description = metadata.description
                )
            )
        }
        return saved
    }

    fun toggleActiveJourneyFavorite() {
        val detail = _activeJourneyDetail.value ?: return
        viewModelScope.launch {
            val nextValue = !detail.journey.isFavorite
            if (repository.updateJourneyFavorite(detail.journey.id, nextValue)) {
                _activeJourneyDetail.value = detail.copy(journey = detail.journey.copy(isFavorite = nextValue))
            }
        }
    }

    /**
     * Copies a chosen picture into private app storage before saving its path. If persistence
     * fails, the new copy is removed; the previous cover stays intact.
     */
    suspend fun setActiveJourneyCover(uri: Uri): Boolean {
        val detail = _activeJourneyDetail.value ?: return false
        val previousPath = detail.journey.coverPhotoPath
        val newPath = runCatching { coverPhotoStore.copyFrom(uri, detail.journey.id) }.getOrElse { return false }
        val saved = repository.updateJourneyCover(detail.journey.id, newPath, System.currentTimeMillis())
        if (!saved) {
            coverPhotoStore.delete(newPath)
            return false
        }
        if (previousPath != newPath) coverPhotoStore.delete(previousPath)
        _activeJourneyDetail.value = detail.copy(
            journey = detail.journey.copy(coverPhotoPath = newPath, coverUpdatedAt = System.currentTimeMillis())
        )
        return true
    }

    fun removeActiveJourneyCover() {
        val detail = _activeJourneyDetail.value ?: return
        val previousPath = detail.journey.coverPhotoPath ?: return
        viewModelScope.launch {
            if (repository.updateJourneyCover(detail.journey.id, null, null)) {
                coverPhotoStore.delete(previousPath)
                _activeJourneyDetail.value = detail.copy(
                    journey = detail.journey.copy(coverPhotoPath = null, coverUpdatedAt = null)
                )
            }
        }
    }

    fun downloadActiveJourneyForOfflineUse() {
        val detail = activeJourneyDetail.value ?: return
        if (detail.points.size < 2) return
        viewModelScope.launch {
            val regionId = OfflineMapPackManager.regionIdFor(detail.journey.id)
            val downloading = OfflineMapRegion(
                journeyId = detail.journey.id,
                regionId = regionId,
                status = OfflineRegionStatus.DOWNLOADING,
                progress = 0f,
                styleUri = Style.STANDARD,
                minZoom = OfflineMapPackManager.MIN_ZOOM,
                maxZoom = OfflineMapPackManager.MAX_ZOOM
            )
            _activeOfflineMapRegion.value = downloading
            repository.upsertOfflineMapRegion(downloading)

            try {
                offlineMapPackManager.downloadJourney(detail.journey.id, detail.points) { progress ->
                    _activeOfflineMapRegion.value = downloading.copy(progress = progress)
                }
                val available = downloading.copy(
                    status = OfflineRegionStatus.AVAILABLE,
                    progress = 1f,
                    downloadedAt = System.currentTimeMillis(),
                    lastError = null
                )
                _activeOfflineMapRegion.value = available
                repository.upsertOfflineMapRegion(available)
            } catch (exception: Exception) {
                val failed = downloading.copy(
                    status = OfflineRegionStatus.FAILED,
                    lastError = exception.message ?: "Offline download failed"
                )
                _activeOfflineMapRegion.value = failed
                repository.upsertOfflineMapRegion(failed)
            }
        }
    }

    fun removeActiveJourneyOfflinePack() {
        val detail = activeJourneyDetail.value ?: return
        viewModelScope.launch {
            runCatching { offlineMapPackManager.removeJourney(detail.journey.id) }
            repository.deleteOfflineMapRegion(detail.journey.id)
            _activeOfflineMapRegion.value = null
        }
    }

    private fun removeOfflinePacks(ids: List<Long>) {
        ids.forEach { id -> runCatching { offlineMapPackManager.removeJourney(id) } }
    }

    fun togglePlayPause() {
        if (isPlaying.value) pausePlayback() else startPlayback()
    }

    /** Starts a one-pass, full-screen replay at the first point and stops at journey arrival. */
    fun beginReliveMode() {
        val detail = activeJourneyDetail.value ?: return
        if (detail.points.isEmpty()) return
        isReliveMode.value = true
        _mapExperience.value = MapExperienceReducer.forRelive()
        publishReplay(ReplaySessionReducer.begin(detail.points.size, playbackSpeed.value))
        launchPlayback(detail)
    }

    fun endReliveMode() {
        pausePlayback()
        isReliveMode.value = false
        _mapExperience.value = MapExperienceReducer.forJourneyDetail()
    }

    fun selectMapCamera(mode: JourneyCameraMode) {
        _mapExperience.value = MapExperienceReducer.selectCamera(_mapExperience.value, mode)
    }

    fun toggleMapBaseStyle() {
        _mapExperience.value = MapExperienceReducer.toggleBaseStyle(_mapExperience.value)
    }

    fun cycleMapSceneMood() {
        _mapExperience.value = MapExperienceReducer.nextMood(_mapExperience.value)
    }

    fun toggleMapThreeDObjects() {
        _mapExperience.value = MapExperienceReducer.toggleThreeDObjects(_mapExperience.value)
    }

    fun toggleMapLabels() {
        _mapExperience.value = MapExperienceReducer.toggleLabels(_mapExperience.value)
    }

    fun toggleMapStops() {
        _mapExperience.value = MapExperienceReducer.toggleStops(_mapExperience.value)
    }

    private fun startPlayback() {
        val detail = activeJourneyDetail.value ?: return
        if (detail.points.isEmpty()) return
        val session = when (_replaySession.value.status) {
            ReplayStatus.IDLE -> ReplaySessionReducer.begin(detail.points.size, playbackSpeed.value)
            else -> ReplaySessionReducer.resume(_replaySession.value)
        }
        publishReplay(session)
        launchPlayback(detail)
    }

    private fun launchPlayback(detail: JourneyDetailData) {
        val highlightByPointIndex = if (isReliveMode.value) {
            RelivePlanner.moments(detail)
                .filter { it.kind == ReliveMomentKind.HIGHLIGHT }
                .associateBy { it.pointIndex }
        } else {
            emptyMap()
        }
        playbackJob?.cancel()
        playbackJob = viewModelScope.launch {
            while (_replaySession.value.status == ReplayStatus.PLAYING) {
                val before = _replaySession.value
                val nextIndex = (before.currentPointIndex + 1).coerceAtMost(detail.points.lastIndex)
                val nextSession = ReplaySessionReducer.advance(
                    state = before,
                    pointCount = detail.points.size,
                    highlightAtNextPoint = highlightByPointIndex[nextIndex]
                )
                publishReplay(nextSession)
                if (nextSession.status != ReplayStatus.PLAYING) break
                val replayDelay = if (isReliveMode.value) {
                    RelivePlaybackClock.delayForNextPoint(
                        currentTimestamp = detail.points[before.currentPointIndex].timestamp,
                        nextTimestamp = detail.points[nextSession.currentPointIndex].timestamp,
                        playbackSpeed = nextSession.playbackSpeed
                    )
                } else {
                    (200 / nextSession.playbackSpeed).toLong().coerceAtLeast(20L)
                }
                delay(replayDelay)
            }
        }
    }

    fun pausePlayback() {
        publishReplay(ReplaySessionReducer.pause(_replaySession.value))
        playbackJob?.cancel()
        playbackJob = null
    }

    fun seekToIndex(index: Int) {
        val total = activeJourneyDetail.value?.points?.size ?: 1
        publishReplay(ReplaySessionReducer.seek(_replaySession.value, total, index))
    }

    fun setSpeed(speed: Float) {
        publishReplay(ReplaySessionReducer.setSpeed(_replaySession.value, speed))
        if (isPlaying.value) startPlayback()
    }

    private fun publishReplay(session: ReplaySessionState) {
        _replaySession.value = session
        currentPointIndex.value = session.currentPointIndex
        playbackSpeed.value = session.playbackSpeed
        isPlaying.value = session.status == ReplayStatus.PLAYING
        _reliveStopMoment.value = session.activeMoment.takeIf { session.status == ReplayStatus.PAUSED_AT_STOP }
    }

    suspend fun importTimelineJson(jsonString: String, title: String): Boolean =
        repository.importTimelineJson(jsonString, title)

    /** Opens a JSON/GeoJSON document as a stream so imports do not duplicate a large file in UI memory. */
    suspend fun importTimelineDocument(uri: Uri, title: String): Boolean = withContext(Dispatchers.IO) {
        app.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
            repository.importTimelineReader(reader, title)
        } ?: false
    }

    suspend fun addCustomJourney(
        title: String,
        description: String,
        startLat: Double,
        startLng: Double,
        endLat: Double,
        endLng: Double,
        stopNames: List<String>
    ): Long = repository.addCustomJourney(title, description, startLat, startLng, endLat, endLng, stopNames)
}
