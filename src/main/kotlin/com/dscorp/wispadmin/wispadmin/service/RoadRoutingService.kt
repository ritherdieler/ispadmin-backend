package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.config.SmartMapRoutingProperties
import com.dscorp.wispadmin.wispadmin.dto.GeoLocationDto
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException

data class RoadRouteResult(
    val path: List<GeoLocationDto>,
    val distanceMeters: Double,
    val durationSeconds: Double?,
    val routingStatus: String,
)

@Service
class RoadRoutingService(
    @Qualifier("osrmWebClient")
    private val webClient: WebClient,
    private val routingProperties: SmartMapRoutingProperties,
) {
    suspend fun fetchRoute(waypoints: List<GeoLocationDto>): RoadRouteResult = withContext(Dispatchers.IO) {
        if (waypoints.size < 2) {
            return@withContext RoadRouteResult(
                path = waypoints,
                distanceMeters = 0.0,
                durationSeconds = null,
                routingStatus = "ready",
            )
        }

        if (waypoints.size <= routingProperties.osrm.maxChunkSize) {
            return@withContext fetchSegment(waypoints) ?: fallbackResult(waypoints)
        }

        fetchRouteChunked(waypoints)
    }

    /**
     * Routes each consecutive stop pair (A→B, B→C, …) so every leg follows streets.
     * More reliable than multi-waypoint chunks for collection routes with many stops.
     */
    suspend fun fetchRouteBySegments(waypoints: List<GeoLocationDto>): RoadRouteResult = withContext(Dispatchers.IO) {
        if (waypoints.size < 2) {
            return@withContext RoadRouteResult(
                path = waypoints,
                distanceMeters = 0.0,
                durationSeconds = null,
                routingStatus = "ready",
            )
        }

        val segmentPairs = (0 until waypoints.size - 1).map { index ->
            listOf(waypoints[index], waypoints[index + 1])
        }

        val mergedPath = mutableListOf<GeoLocationDto>()
        var totalDistance = 0.0
        var totalDuration = 0.0
        var hasDuration = true
        var failedSegments = 0

        coroutineScope {
            var batchStart = 0
            while (batchStart < segmentPairs.size) {
                if (batchStart > 0) {
                    delay(routingProperties.osrm.batchDelayMs)
                }

                val batchEnd = minOf(batchStart + routingProperties.osrm.maxConcurrent, segmentPairs.size)
                val batch = segmentPairs.subList(batchStart, batchEnd)
                val results = batch.map { pair ->
                    async { fetchSegment(pair) }
                }.awaitAll()

                results.forEachIndexed { index, segment ->
                    val pair = batch[index]
                    if (segment != null) {
                        appendPath(mergedPath, segment.path)
                        totalDistance += segment.distanceMeters
                        if (segment.durationSeconds != null) {
                            totalDuration += segment.durationSeconds
                        } else {
                            hasDuration = false
                        }
                    } else {
                        failedSegments += 1
                        appendPath(mergedPath, pair)
                    }
                }

                batchStart = batchEnd
            }
        }

        if (mergedPath.isEmpty()) {
            return@withContext fallbackResult(waypoints)
        }

        val routingStatus = when {
            failedSegments == 0 -> "ready"
            failedSegments == segmentPairs.size -> "fallback"
            else -> "partial"
        }

        RoadRouteResult(
            path = simplifyPath(mergedPath, routingProperties.osrm.maxRenderPoints),
            distanceMeters = totalDistance,
            durationSeconds = if (hasDuration) totalDuration else null,
            routingStatus = routingStatus,
        )
    }

    suspend fun fetchRouteChunked(waypoints: List<GeoLocationDto>): RoadRouteResult = withContext(Dispatchers.IO) {
        if (waypoints.size < 2) {
            return@withContext RoadRouteResult(
                path = waypoints,
                distanceMeters = 0.0,
                durationSeconds = null,
                routingStatus = "ready",
            )
        }

        val chunks = splitWaypointsIntoChunks(waypoints, routingProperties.osrm.maxChunkSize)
        val mergedPath = mutableListOf<GeoLocationDto>()
        var totalDistance = 0.0
        var totalDuration = 0.0
        var hasDuration = true
        var failedSegments = 0

        coroutineScope {
            var batchStart = 0
            while (batchStart < chunks.size) {
                if (batchStart > 0) {
                    delay(routingProperties.osrm.batchDelayMs)
                }

                val batchEnd = minOf(batchStart + routingProperties.osrm.maxConcurrent, chunks.size)
                val batch = chunks.subList(batchStart, batchEnd)
                val results = batch.map { chunk ->
                    async { fetchSegment(chunk) }
                }.awaitAll()

                results.forEachIndexed { index, segment ->
                    val chunk = batch[index]
                    if (segment != null) {
                        appendPath(mergedPath, segment.path)
                        totalDistance += segment.distanceMeters
                        if (segment.durationSeconds != null) {
                            totalDuration += segment.durationSeconds
                        } else {
                            hasDuration = false
                        }
                    } else {
                        failedSegments += 1
                        appendPath(mergedPath, chunk)
                    }
                }

                batchStart = batchEnd
            }
        }

        if (mergedPath.isEmpty()) {
            return@withContext fallbackResult(waypoints)
        }

        val routingStatus = when {
            failedSegments == 0 -> "ready"
            failedSegments == chunks.size -> "fallback"
            else -> "partial"
        }

        RoadRouteResult(
            path = simplifyPath(mergedPath, routingProperties.osrm.maxRenderPoints),
            distanceMeters = totalDistance,
            durationSeconds = if (hasDuration) totalDuration else null,
            routingStatus = routingStatus,
        )
    }

    private suspend fun fetchSegment(waypoints: List<GeoLocationDto>): RoadRouteResult? {
        if (waypoints.size < 2) {
            return RoadRouteResult(
                path = waypoints,
                distanceMeters = 0.0,
                durationSeconds = null,
                routingStatus = "ready",
            )
        }

        repeat(routingProperties.osrm.maxRetries) { attempt ->
            val result = fetchSegmentOnce(waypoints)
            if (result != null) {
                return result
            }
            if (attempt + 1 < routingProperties.osrm.maxRetries) {
                delay(500L * (attempt + 1))
            }
        }

        return null
    }

    private suspend fun fetchSegmentOnce(waypoints: List<GeoLocationDto>): RoadRouteResult? {
        return try {
            val coordinatePath = waypoints.joinToString(";") { "${it.longitude},${it.latitude}" }
            val baseUrl = routingProperties.osrm.baseUrl.trimEnd('/')
            val url = "$baseUrl/route/v1/driving/$coordinatePath?overview=full&geometries=geojson&steps=false"

            val response = webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(OsrmRouteResponse::class.java)
                .awaitSingle()

            if (response.code != null && response.code != "Ok") {
                return null
            }

            val route = response.routes?.firstOrNull() ?: return null
            val coordinates = route.geometry?.coordinates ?: return null
            if (coordinates.isEmpty()) {
                return null
            }

            RoadRouteResult(
                path = coordinates.map { (longitude, latitude) ->
                    GeoLocationDto(latitude = latitude, longitude = longitude)
                },
                distanceMeters = route.distance ?: 0.0,
                durationSeconds = route.duration,
                routingStatus = "ready",
            )
        } catch (_: WebClientResponseException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun fallbackResult(waypoints: List<GeoLocationDto>): RoadRouteResult {
        return RoadRouteResult(
            path = simplifyPath(waypoints, routingProperties.osrm.maxRenderPoints),
            distanceMeters = 0.0,
            durationSeconds = null,
            routingStatus = "fallback",
        )
    }

    companion object {
        fun splitWaypointsIntoChunks(
            waypoints: List<GeoLocationDto>,
            chunkSize: Int,
        ): List<List<GeoLocationDto>> {
            if (waypoints.size <= 1) {
                return listOf(waypoints)
            }
            if (waypoints.size <= chunkSize) {
                return listOf(waypoints)
            }

            val chunks = mutableListOf<List<GeoLocationDto>>()
            var start = 0
            while (start < waypoints.size - 1) {
                val end = minOf(start + chunkSize - 1, waypoints.size - 1)
                chunks.add(waypoints.subList(start, end + 1))
                if (end >= waypoints.size - 1) {
                    break
                }
                start = end
            }
            return chunks
        }

        fun appendPath(target: MutableList<GeoLocationDto>, segment: List<GeoLocationDto>) {
            if (segment.isEmpty()) {
                return
            }
            if (target.isEmpty()) {
                target.addAll(segment)
                return
            }

            val last = target.last()
            val first = segment.first()
            val isDuplicate = kotlin.math.abs(last.latitude - first.latitude) < 1e-6
                && kotlin.math.abs(last.longitude - first.longitude) < 1e-6

            target.addAll(if (isDuplicate) segment.drop(1) else segment)
        }

        fun simplifyPath(path: List<GeoLocationDto>, maxPoints: Int): List<GeoLocationDto> {
            if (path.size <= maxPoints) {
                return path
            }

            val step = kotlin.math.ceil(path.size.toDouble() / maxPoints).toInt()
            val simplified = mutableListOf<GeoLocationDto>()
            var index = 0
            while (index < path.size) {
                simplified.add(path[index])
                index += step
            }

            val lastPoint = path.last()
            val tail = simplified.last()
            if (kotlin.math.abs(tail.latitude - lastPoint.latitude) > 1e-6
                || kotlin.math.abs(tail.longitude - lastPoint.longitude) > 1e-6
            ) {
                simplified.add(lastPoint)
            }

            return simplified
        }

        fun buildLineStringGeoJson(path: List<GeoLocationDto>): String {
            val coordinates = path.joinToString(",") { "[${it.longitude},${it.latitude}]" }
            return """{"type":"LineString","coordinates":[$coordinates]}"""
        }
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
private data class OsrmRouteResponse(
    val code: String? = null,
    val routes: List<OsrmRoute>? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class OsrmRoute(
    val distance: Double? = null,
    val duration: Double? = null,
    val geometry: OsrmGeometry? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class OsrmGeometry(
    val coordinates: List<List<Double>>? = null,
)
