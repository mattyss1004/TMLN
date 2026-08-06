package com.example.timelineviewer.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.timelineviewer.data.local.AppDatabase
import com.example.timelineviewer.data.model.Journey
import com.example.timelineviewer.data.model.JourneyDetailData
import com.example.timelineviewer.data.repository.JourneyRepository
import com.example.timelineviewer.data.seed.SampleDataSeeder
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: JourneyRepository

    val selectedJourneyIds = MutableStateFlow<Set<Long>>(emptySet())
    val searchQuery = MutableStateFlow("")
    val isDarkTheme = MutableStateFlow(true)

    private val _activeJourneyDetail = MutableStateFlow<JourneyDetailData?>(null)
    val activeJourneyDetail: StateFlow<JourneyDetailData?> = _activeJourneyDetail.asStateFlow()

    // Playback state
    val isPlaying = MutableStateFlow(false)
    val currentPointIndex = MutableStateFlow(0)
    val playbackSpeed = MutableStateFlow(1f)

    private var playbackJob: Job? = null

    val journeys: StateFlow<List<Journey>>

    init {
        val db = AppDatabase.getDatabase(application)
        repository = JourneyRepository(db)

        // Seed initial sample journeys if DB is empty
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
        val current = selectedJourneyIds.value.toMutableSet()
        if (current.contains(id)) {
            current.remove(id)
        } else {
            current.add(id)
        }
        selectedJourneyIds.value = current
    }

    fun selectAllJourneys(selectAll: Boolean) {
        if (selectAll) {
            selectedJourneyIds.value = journeys.value.map { it.id }.toSet()
        } else {
            selectedJourneyIds.value = emptySet()
        }
    }

    fun deleteSelectedJourneys() {
        viewModelScope.launch {
            val ids = selectedJourneyIds.value.toList()
            repository.deleteJourneys(ids)
            selectedJourneyIds.value = emptySet()
            if (_activeJourneyDetail.value?.journey?.id in ids) {
                _activeJourneyDetail.value = null
            }
        }
    }

    fun deleteJourney(id: Long) {
        viewModelScope.launch {
            repository.deleteJourney(id)
            val set = selectedJourneyIds.value.toMutableSet()
            set.remove(id)
            selectedJourneyIds.value = set
            if (_activeJourneyDetail.value?.journey?.id == id) {
                _activeJourneyDetail.value = null
            }
        }
    }

    fun deleteAllJourneys() {
        viewModelScope.launch {
            repository.deleteAllJourneys()
            selectedJourneyIds.value = emptySet()
            _activeJourneyDetail.value = null
        }
    }

    fun loadJourneyDetail(id: Long) {
        viewModelScope.launch {
            pausePlayback()
            val detail = repository.getJourneyDetail(id)
            _activeJourneyDetail.value = detail
            currentPointIndex.value = 0
        }
    }

    fun clearActiveJourney() {
        pausePlayback()
        _activeJourneyDetail.value = null
    }

    // Playback control functions
    fun togglePlayPause() {
        if (isPlaying.value) {
            pausePlayback()
        } else {
            startPlayback()
        }
    }

    private fun startPlayback() {
        val detail = activeJourneyDetail.value ?: return
        if (detail.points.isEmpty()) return

        isPlaying.value = true
        playbackJob?.cancel()

        playbackJob = viewModelScope.launch {
            while (isPlaying.value) {
                val total = detail.points.size
                val next = currentPointIndex.value + 1
                if (next >= total) {
                    currentPointIndex.value = 0
                } else {
                    currentPointIndex.value = next
                }
                val delayMs = (200 / playbackSpeed.value).toLong().coerceAtLeast(20L)
                delay(delayMs)
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
        if (isPlaying.value) {
            startPlayback() // restart loop with new speed
        }
    }

    suspend fun importTimelineJson(jsonString: String, title: String): Boolean {
        return repository.importTimelineJson(jsonString, title)
    }

    suspend fun addCustomJourney(
        title: String,
        description: String,
        startLat: Double,
        startLng: Double,
        endLat: Double,
        endLng: Double,
        stopNames: List<String>
    ): Long {
        return repository.addCustomJourney(title, description, startLat, startLng, endLat, endLng, stopNames)
    }
}
