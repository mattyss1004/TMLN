package com.example.timelineviewer.data.analysis

enum class ReplayStatus {
    IDLE,
    PLAYING,
    PAUSED_MANUALLY,
    PAUSED_AT_STOP,
    FINISHED
}

/**
 * Pure playback state for a local journey replay. It is intentionally independent from the map so
 * a route can only progress through an explicit transition, never through unrelated recomposition.
 */
data class ReplaySessionState(
    val status: ReplayStatus = ReplayStatus.IDLE,
    val currentPointIndex: Int = 0,
    val playbackSpeed: Float = 1f,
    val activeMoment: ReliveMoment? = null,
    val visitedStopPointIndexes: Set<Int> = emptySet()
)

object ReplaySessionReducer {
    fun begin(pointCount: Int, playbackSpeed: Float): ReplaySessionState {
        if (pointCount <= 0) return ReplaySessionState()
        return ReplaySessionState(
            status = ReplayStatus.PLAYING,
            currentPointIndex = 0,
            playbackSpeed = sanitizeSpeed(playbackSpeed)
        )
    }

    fun pause(state: ReplaySessionState): ReplaySessionState = when (state.status) {
        ReplayStatus.PLAYING -> state.copy(status = ReplayStatus.PAUSED_MANUALLY)
        else -> state
    }

    fun resume(state: ReplaySessionState): ReplaySessionState = when (state.status) {
        ReplayStatus.PAUSED_MANUALLY, ReplayStatus.PAUSED_AT_STOP -> state.copy(
            status = ReplayStatus.PLAYING,
            activeMoment = null
        )
        ReplayStatus.FINISHED -> state.copy(
            status = ReplayStatus.PLAYING,
            currentPointIndex = 0,
            activeMoment = null,
            visitedStopPointIndexes = emptySet()
        )
        else -> state
    }

    fun seek(state: ReplaySessionState, pointCount: Int, requestedIndex: Int): ReplaySessionState {
        if (pointCount <= 0) return ReplaySessionState()
        return state.copy(
            currentPointIndex = requestedIndex.coerceIn(0, pointCount - 1),
            activeMoment = null,
            status = if (state.status == ReplayStatus.FINISHED) ReplayStatus.PAUSED_MANUALLY else state.status
        )
    }

    fun setSpeed(state: ReplaySessionState, playbackSpeed: Float): ReplaySessionState =
        state.copy(playbackSpeed = sanitizeSpeed(playbackSpeed))

    /** Advances exactly one point. A not-yet-visited highlight creates a real stop gate. */
    fun advance(
        state: ReplaySessionState,
        pointCount: Int,
        highlightAtNextPoint: ReliveMoment?
    ): ReplaySessionState {
        if (state.status != ReplayStatus.PLAYING || pointCount <= 0) return state
        val lastIndex = pointCount - 1
        val nextIndex = (state.currentPointIndex + 1).coerceAtMost(lastIndex)
        if (nextIndex == lastIndex) {
            return state.copy(
                status = ReplayStatus.FINISHED,
                currentPointIndex = lastIndex,
                activeMoment = null
            )
        }
        val isNewHighlight = highlightAtNextPoint?.kind == ReliveMomentKind.HIGHLIGHT &&
            highlightAtNextPoint.pointIndex == nextIndex &&
            nextIndex !in state.visitedStopPointIndexes
        return if (isNewHighlight) {
            state.copy(
                status = ReplayStatus.PAUSED_AT_STOP,
                currentPointIndex = nextIndex,
                activeMoment = highlightAtNextPoint,
                visitedStopPointIndexes = state.visitedStopPointIndexes + nextIndex
            )
        } else {
            state.copy(currentPointIndex = nextIndex)
        }
    }

    private fun sanitizeSpeed(value: Float): Float = value.coerceIn(0.5f, 4f)
}
