package com.vyora.core.location

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition

/**
 * Manages downloading and caching of OpenStreetMap vector tiles using MapLibre's OfflineManager.
 * Once tiles are downloaded, MapLibre's internal SQLite database cache (`cache.db`) automatically
 * serves them when offline without requiring cellular or internet connection.
 */
class OfflineMapTileManager(private val context: Context) {

    companion object {
        private const val TAG = "OfflineMapTileManager"
        const val DEFAULT_RADIUS_DEG = 0.045 // ~5km radius bounding box
    }

    data class DownloadProgress(
        val isDownloading: Boolean = false,
        val percentage: Int = 0,
        val completedResources: Long = 0,
        val totalResources: Long = 0,
        val isComplete: Boolean = false,
        val statusText: String = "Ready"
    )

    private val _downloadProgress = MutableStateFlow(DownloadProgress())
    val downloadProgress: StateFlow<DownloadProgress> = _downloadProgress.asStateFlow()

    private val offlineManager: OfflineManager = OfflineManager.getInstance(context)

    /**
     * Downloads an offline tile pyramid region surrounding the given coordinates.
     */
    fun cacheRegionAround(
        centerLat: Double,
        centerLng: Double,
        styleUrl: String,
        radiusDegrees: Double = DEFAULT_RADIUS_DEG,
        regionName: String = "Tactical Grid"
    ) {
        if (_downloadProgress.value.isDownloading) {
            Log.w(TAG, "Download already in progress")
            return
        }

        _downloadProgress.value = DownloadProgress(
            isDownloading = true,
            percentage = 0,
            statusText = "Calculating tile bounds..."
        )

        val bounds = LatLngBounds.Builder()
            .include(LatLng(centerLat + radiusDegrees, centerLng + radiusDegrees))
            .include(LatLng(centerLat - radiusDegrees, centerLng - radiusDegrees))
            .build()

        val pixelRatio = context.resources.displayMetrics.density
        // Tactical range zoom: min 11 (sub-district) to max 15 (tactical street level)
        val definition = OfflineTilePyramidRegionDefinition(
            styleUrl,
            bounds,
            11.0,
            15.0,
            pixelRatio,
            true
        )

        val metadataJson = JSONObject().apply {
            put("REGION_NAME", regionName)
            put("CENTER_LAT", centerLat)
            put("CENTER_LNG", centerLng)
            put("TIMESTAMP", System.currentTimeMillis())
        }
        val metadata = metadataJson.toString().toByteArray(Charsets.UTF_8)

        offlineManager.createOfflineRegion(
            definition,
            metadata,
            object : OfflineManager.CreateOfflineRegionCallback {
                override fun onCreate(offlineRegion: OfflineRegion) {
                    Log.i(TAG, "Offline region created: id=${offlineRegion.id}. Beginning tile caching.")
                    observeDownload(offlineRegion)
                }

                override fun onError(error: String) {
                    Log.e(TAG, "Failed to create offline region: $error")
                    _downloadProgress.value = DownloadProgress(
                        isDownloading = false,
                        statusText = "Cache Error: $error"
                    )
                }
            }
        )
    }

    private fun observeDownload(offlineRegion: OfflineRegion) {
        offlineRegion.setObserver(object : OfflineRegion.OfflineRegionObserver {
            override fun onStatusChanged(status: OfflineRegionStatus) {
                val percentage = if (status.requiredResourceCount > 0) {
                    (100.0 * status.completedResourceCount / status.requiredResourceCount).toInt()
                } else {
                    0
                }

                if (status.isComplete) {
                    Log.i(TAG, "Offline map tiles cached successfully (${status.completedResourceCount} tiles)")
                    _downloadProgress.value = DownloadProgress(
                        isDownloading = false,
                        percentage = 100,
                        completedResources = status.completedResourceCount,
                        totalResources = status.completedResourceCount,
                        isComplete = true,
                        statusText = "Offline Cache Ready (${status.completedResourceCount} tiles stored)"
                    )
                } else {
                    _downloadProgress.value = DownloadProgress(
                        isDownloading = true,
                        percentage = percentage,
                        completedResources = status.completedResourceCount,
                        totalResources = status.requiredResourceCount,
                        isComplete = false,
                        statusText = "Caching OSM Tiles: $percentage% (${status.completedResourceCount}/${status.requiredResourceCount})"
                    )
                }
            }

            override fun onError(error: OfflineRegionError) {
                Log.e(TAG, "Tile caching error: ${error.reason} - ${error.message}")
                _downloadProgress.value = DownloadProgress(
                    isDownloading = false,
                    statusText = "Error: ${error.message}"
                )
            }

            override fun mapboxTileCountLimitExceeded(limit: Long) {
                Log.w(TAG, "Tile count limit exceeded: $limit")
            }
        })

        offlineRegion.setDownloadState(OfflineRegion.STATE_ACTIVE)
    }
}
