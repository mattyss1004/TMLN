package com.example.timelineviewer.data.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

/**
 * Owns only copies in app-private storage. The original picker URI is never persisted, requested
 * again, or uploaded. Re-encoding as JPEG also omits source EXIF metadata such as camera details.
 */
class JourneyCoverPhotoStore(context: Context) {
    private val resolver = context.contentResolver
    private val coverDirectory = File(context.filesDir, COVER_DIRECTORY).apply { mkdirs() }

    suspend fun copyFrom(uri: Uri, journeyId: Long): String = withContext(Dispatchers.IO) {
        require(coverDirectory.exists() || coverDirectory.mkdirs()) { "TMLN could not prepare private cover storage." }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            ?: throw IllegalArgumentException("The selected photo could not be opened.")

        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Please choose a supported image file." }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw IllegalArgumentException("The selected photo could not be decoded.")

        val scaled = scaleWithinBounds(decoded)
        if (scaled !== decoded) decoded.recycle()
        val output = File(coverDirectory, "journey_${journeyId}_${System.currentTimeMillis()}.jpg")
        val temporary = File(coverDirectory, "${output.name}.partial")
        try {
            FileOutputStream(temporary).use { stream ->
                check(scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)) {
                    "TMLN could not save the private cover image."
                }
            }
            check(temporary.renameTo(output)) { "TMLN could not finalize the cover image." }
            output.absolutePath
        } catch (exception: Exception) {
            temporary.delete()
            output.delete()
            throw exception
        } finally {
            scaled.recycle()
        }
    }

    fun delete(path: String?) {
        val file = path?.let(::File) ?: return
        runCatching {
            val trustedRoot = coverDirectory.canonicalPath + File.separator
            if (file.canonicalPath.startsWith(trustedRoot)) file.delete()
        }
    }

    fun deleteAll(paths: Collection<String>) = paths.forEach(::delete)

    private fun sampleSizeFor(width: Int, height: Int): Int {
        var sampleSize = 1
        var currentWidth = width
        var currentHeight = height
        while (currentWidth / 2 >= MAX_EDGE_PX || currentHeight / 2 >= MAX_EDGE_PX) {
            sampleSize *= 2
            currentWidth /= 2
            currentHeight /= 2
        }
        return sampleSize
    }

    private fun scaleWithinBounds(bitmap: Bitmap): Bitmap {
        val largestEdge = max(bitmap.width, bitmap.height)
        if (largestEdge <= MAX_EDGE_PX) return bitmap
        val multiplier = MAX_EDGE_PX.toFloat() / largestEdge.toFloat()
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * multiplier).toInt().coerceAtLeast(1),
            (bitmap.height * multiplier).toInt().coerceAtLeast(1),
            true
        )
    }

    private companion object {
        const val COVER_DIRECTORY = "memory-covers"
        const val MAX_EDGE_PX = 1_600
        const val JPEG_QUALITY = 88
    }
}
