package com.example.timelineviewer.data.analysis

/**
 * Converts real gaps between recorded Timeline points into a short, readable replay cadence.
 * The original chronology is preserved while long real-world journeys remain practical to relive.
 */
object RelivePlaybackClock {
    private const val TIMELINE_COMPRESSION = 700L
    private const val MIN_FRAME_DELAY_MS = 110L
    private const val MAX_FRAME_DELAY_MS = 1_100L

    fun delayForNextPoint(
        currentTimestamp: Long,
        nextTimestamp: Long,
        playbackSpeed: Float
    ): Long {
        val realGapMs = (nextTimestamp - currentTimestamp).coerceAtLeast(0L)
        val compressed = (realGapMs / TIMELINE_COMPRESSION)
            .coerceIn(MIN_FRAME_DELAY_MS, MAX_FRAME_DELAY_MS)
        return (compressed / playbackSpeed.coerceAtLeast(0.1f)).toLong()
            .coerceAtLeast(MIN_FRAME_DELAY_MS / 2)
    }
}
