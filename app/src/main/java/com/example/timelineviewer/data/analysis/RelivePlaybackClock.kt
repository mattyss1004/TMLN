
import kotlin.math.ln

/**
 * Converts real gaps between recorded Timeline points into a short, readable replay cadence.
 * The original chronology is preserved while long real-world journeys remain practical to relive.
 */
object RelivePlaybackClock {
    private const val BASE_COMPRESSION = 500L
    private const val MIN_FRAME_DELAY_MS = 80L
    private const val MAX_FRAME_DELAY_MS = 800L

    fun delayForNextPoint(
        currentTimestamp: Long,
        nextTimestamp: Long,
        playbackSpeed: Float
    ): Long {
        val realGapMs = (nextTimestamp - currentTimestamp).coerceAtLeast(0L)
        
        // Logarithmic scaling for real gaps to prevent jarring jumps while preserving rhythm
        val scaledMs = if (realGapMs <= 1000L) {
            realGapMs.toFloat() / 3f
        } else {
            333f + (ln(realGapMs.toFloat() / 1000f) * 120f)
        }

        val effectiveSpeed = playbackSpeed.coerceIn(0.25f, 8.0f)
        val adjustedDelay = (scaledMs / effectiveSpeed).toLong()

        return adjustedDelay.coerceIn(MIN_FRAME_DELAY_MS, MAX_FRAME_DELAY_MS)
    }
}
