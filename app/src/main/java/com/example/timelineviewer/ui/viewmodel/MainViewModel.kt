package com.example.timelineviewer.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.timelineviewer.data.local.AppDatabase
import com.example.timelineviewer.data.model.Journey
import com.example.timelineviewer.data.model.JourneyDetailData
import com.example.timelineviewer.data.model.JourneyMetadataEditor
import com.example.timelineviewer.data.model.OfflineMapRegion
import com.example.timelineviewer.data.model.OfflineRegionStatus
import com.example.timelineviewer.data.repository.JourneyRepository
import com.example.timelineviewer.data.seed.SampleDataSeeder
import com.example.timelineviewer.data.service.OfflineMapPackManager
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

    val selectedJourneyIds = MutableStateFlow<Set<Long>>(emptySet())
    val searchQuery = MutableStateFlow("")
    val isDarkTheme = MutableStateFlow(true)

    private val _activeJourneyDetail = MutableStateFlow<JourneyDetailData?>(null)
    val activeJourneyDetail: StateFlow<JourneyDetailData?> = _activeJourneyDetail.asStateFlow()

    private val _activeOfflineMapRegion = MutableStateFlow<OfflineMapRegion?>(null)
    val activeOfflineMapRegion: StateFlow<OfflineMapRegion?> = _activeOfflineMapRegion.asStateFlow()

    // Playback state
    val isPlaying = MutableStateFlow(false)
    val isReliveMode = MutableStateFlow(false)
    val currentPointIndex = MutableStateFlow(0)
    val playbackSpeed = MutableStateFlow(1f)

    private var playbackJob: Job? = null
    val journeys: StateFlow<List<Journey>>

    init {
        val db = AppDatabase.getDatabase(app)
        repository = JourneyRepository(db)

        viewModelScope.launch {
            SampleDataSeeder.seedIfEmpty(db)
        }

        journeys = combine(repository.allJourneys, searchQuery) { list, query ->
            if (query.isBlank()) list
            else list.filter {
                it.title.contains(query, ignoreCase = true) ||
                    it.description.contains(query, ignoreCase = true)
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
            removeOfflinePacks(ids)
            repository.deleteJourneys(ids)
            selectedJourneyIds.value = emptySet()
            if (_activeJourneyDetail.value?.journey?.id in ids) {
                _activeJourneyDetail.value = null
                _activeOfflineMapRegion.value = null
            }
        }
    }

    fun deleteJourney(id: Long) {
        viewModelScope.launch {
            removeOfflinePacks(listOf(id))
            repository.deleteJourney(id)
            selectedJourneyIds.value = selectedJourneyIds.value - id
            if (_activeJourneyDetail.value?.journey?.id == id) {
                _activeJourneyDetail.value = null
                _activeOfflineMapRegion.value = null
            }
        }
    }

    fun deleteAllJourneys() {
        viewModelScope.launch {
            removeOfflinePacks(journeys.value.map { it.id })
            repository.deleteAllJourneys()
            selectedJourneyIds.value = emptySet()
            _activeJourneyDetail.value = null
            _activeOfflineMapRegion.value = null
        }
    }

    fun loadJourneyDetail(id: Long) {
        viewModelScope.launch {
            pausePlayback()
            isReliveMode.value = false
            _activeJourneyDetail.value = repository.getJourneyDetail(id)
            _activeOfflineMapRegion.value = repository.getOfflineMapRegion(id)
            currentPointIndex.value = 0
        }
    }

    fun clearActiveJourney() {
        pausePlayback()
        isReliveMode.value = false
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

    /** Starts a one-pass, full-screen replay at the first point and stops at the journey arrival. */
    fun beginReliveMode() {
        val detail = activeJourneyDetail.value ?: return
        if (detail.points.isEmpty()) return
        isReliveMode.value = true
        currentPointIndex.value = 0
        startPlayback()
    }

    fun endReliveMode() {
        pausePlayback()
        isReliveMode.value = false
    }

    private fun startPlayback() {
        val detail = activeJourneyDetail.value ?: return
        if (detail.points.isEmpty()) return
        val lastIndex = detail.points.lastIndex
        if (isReliveMode.value && currentPointIndex.value >= lastIndex) currentPointIndex.value = 0
        isPlaying.value = true
        playbackJob?.cancel()
        playbackJob = viewModelScope.launch {
            while (isPlaying.value) {
                val total = detail.points.size
                val next = currentPointIndex.value + 1
                if (next >= total) {
                    currentPointIndex.value = if (isReliveMode.value) total - 1 else 0
                    if (isReliveMode.value) isPlaying.value = false
                } else {
                    currentPointIndex.value = next
                }
                delay((200 / playbackSpeed.value).toLong().coerceAtLeast(20L))
            }
        }
    }

    fun pausePlayback() {
        isPlaying.value = false
        playbackJob?.cancel()
        playbackJob = null
    }

    fun seekToIndex(index: Int) {
        val total = activeJourneyDetail.value?.points?.size ?: 1
        currentPointIndex.value = index.coerceIn(0, total - 1)
    }

    fun setSpeed(speed: Float) {
        playbackSpeed.value = speed
        if (isPlaying.value) startPlayback()
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
