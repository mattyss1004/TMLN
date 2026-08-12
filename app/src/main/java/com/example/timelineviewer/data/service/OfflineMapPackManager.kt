package com.example.timelineviewer.data.service

import android.content.Context
import com.example.timelineviewer.data.model.RoutePoint
import com.mapbox.bindgen.Value
import com.mapbox.common.MapboxOptions
import com.mapbox.common.NetworkRestriction
import com.mapbox.common.TileRegionLoadOptions
import com.mapbox.common.TileStore
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.maps.GlyphsRasterizationMode
import com.mapbox.maps.OfflineManager
import com.mapbox.maps.Style
import com.mapbox.maps.StylePackLoadOptions
import com.mapbox.maps.TilesetDescriptorOptions
import com.mapbox.maps.mapsOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Downloads only the Mapbox resources needed to revisit one journey. Each pack contains both
 * Standard styles used by TMLN and a line-shaped tile region following the route, avoiding broad
 * city or country downloads. The default Mapbox TileStore is shared with the map renderer.
 */
class OfflineMapPackManager(context: Context) {

    companion object {
        const val MIN_ZOOM = 6
        const val MAX_ZOOM = 15
        private const val REGION_VERSION = 1

        fun regionIdFor(journeyId: Long): String = "tmln-journey-$journeyId-v$REGION_VERSION"
    }

    private val pixelRatio = context.resources.displayMetrics.density
    private val offlineManager = OfflineManager()
    private val tileStore: TileStore = requireNotNull(MapboxOptions.mapsOptions.tileStore) {
        "Mapbox TileStore is unavailable. Configure the Mapbox access token before downloading maps."
    }

    suspend fun downloadJourney(
        journeyId: Long,
        routePoints: List<RoutePoint>,
        onProgress: (Float) -> Unit
    ) {
        require(routePoints.size >= 2) { "A journey needs at least two route points for offline download." }
        val regionId = regionIdFor(journeyId)
        val geometry = LineString.fromLngLats(routePoints.map { Point.fromLngLat(it.longitude, it.latitude) })
        val styleUris = listOf(Style.STANDARD, Style.STANDARD_SATELLITE)

        styleUris.forEachIndexed { index, styleUri ->
            val start = index * 0.15f
            val end = start + 0.15f
            downloadStylePack(styleUri, start, end, onProgress)
        }

        val descriptors = styleUris.map { styleUri ->
            offlineManager.createTilesetDescriptor(
                TilesetDescriptorOptions.Builder()
                    .styleURI(styleUri)
                    .pixelRatio(pixelRatio)
                    .minZoom(MIN_ZOOM)
                    .maxZoom(MAX_ZOOM)
                    .build()
            )
        }

        downloadTileRegion(
            regionId = regionId,
            geometry = geometry,
            descriptors = descriptors,
            onProgress = { tileProgress -> onProgress(0.3f + tileProgress * 0.7f) }
        )
    }

    fun removeJourney(journeyId: Long) {
        tileStore.removeTileRegion(regionIdFor(journeyId))
    }

    private suspend fun downloadStylePack(
        styleUri: String,
        progressStart: Float,
        progressEnd: Float,
        onProgress: (Float) -> Unit
    ) = suspendCancellableCoroutine { continuation ->
        val cancelable = offlineManager.loadStylePack(
            styleUri,
            StylePackLoadOptions.Builder()
                .glyphsRasterizationMode(GlyphsRasterizationMode.IDEOGRAPHS_RASTERIZED_LOCALLY)
                .metadata(Value("tmln-$styleUri"))
                .acceptExpired(false)
                .build(),
            { progress ->
                val total = progress.requiredResourceCount.coerceAtLeast(1L)
                val fraction = progress.completedResourceCount.toFloat() / total.toFloat()
                onProgress(progressStart + (progressEnd - progressStart) * fraction)
            },
            { result ->
                when {
                    result.value != null && continuation.isActive -> continuation.resume(Unit)
                    result.error != null && continuation.isActive -> continuation.resumeWithException(
                        IllegalStateException("Map style download failed: ${result.error}")
                    )
                }
            }
        )
        continuation.invokeOnCancellation { cancelable.cancel() }
    }

    private suspend fun downloadTileRegion(
        regionId: String,
        geometry: LineString,
        descriptors: List<com.mapbox.common.TilesetDescriptor>,
        onProgress: (Float) -> Unit
    ) = suspendCancellableCoroutine { continuation ->
        val cancelable = tileStore.loadTileRegion(
            regionId,
            TileRegionLoadOptions.Builder()
                .geometry(geometry)
                .descriptors(descriptors)
                .metadata(Value("tmln selected journey: $regionId"))
                .acceptExpired(false)
                .networkRestriction(NetworkRestriction.NONE)
                .build(),
            { progress ->
                val total = progress.requiredResourceCount.coerceAtLeast(1L)
                onProgress(progress.completedResourceCount.toFloat() / total.toFloat())
            },
            { result ->
                when {
                    result.value != null && continuation.isActive -> continuation.resume(Unit)
                    result.error != null && continuation.isActive -> continuation.resumeWithException(
                        IllegalStateException("Map tile download failed: ${result.error}")
                    )
                }
            }
        )
        continuation.invokeOnCancellation { cancelable.cancel() }
    }
}
