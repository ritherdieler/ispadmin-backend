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
    val segments: List<RoadRouteSegmentResult> = emptyList(),
)

data class RoadRouteSegmentResult(
    val index: Int,
    val from: GeoLocationDto,
    val to: GeoLocationDto,
    val path: List<GeoLocationDto>,
    val distanceMeters: Double,
    val durationSeconds: Double?,
    val routingStatus: String,
    val fallbackReason: String? = null,
)

data class NavigationStepResult(
    val instruction: String,
    val maneuverType: String,
    val modifier: String?,
    val distanceMeters: Double,
    val durationSeconds: Double?,
    val location: GeoLocationDto,
    val streetName: String? = null,
)

data class NavigationRouteResult(
    val path: List<GeoLocationDto>,
    val distanceMeters: Double,
    val durationSeconds: Double?,
    val routingStatus: String,
    val steps: List<NavigationStepResult>,
)

@Service
class RoadRoutingService(
    @Qualifier("mapboxWebClient")
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

        if (waypoints.size <= routingProperties.mapbox.maxWaypoints) {
            return@withContext mapRouteToRoadRouteResult(
                fetchPrimaryRoute(waypoints, steps = false),
            )
        }

        fetchRouteChunked(waypoints)
    }

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
            IndexedRoutePair(index, waypoints[index], waypoints[index + 1])
        }

        val mergedPath = mutableListOf<GeoLocationDto>()
        val segmentDetails = mutableListOf<RoadRouteSegmentResult>()
        var totalDistance = 0.0
        var totalDuration = 0.0
        var hasDuration = true

        coroutineScope {
            var batchStart = 0
            while (batchStart < segmentPairs.size) {
                if (batchStart > 0) {
                    delay(routingProperties.mapbox.batchDelayMs)
                }

                val batchEnd = minOf(batchStart + routingProperties.mapbox.maxConcurrent, segmentPairs.size)
                val batch = segmentPairs.subList(batchStart, batchEnd)
                val results = batch.map { pair ->
                    async { fetchValidatedStreetSegment(pair) }
                }.awaitAll()

                results.forEach { segment ->
                    appendPath(mergedPath, segment.path)
                    totalDistance += segment.distanceMeters
                    segmentDetails.add(segment)
                    if (segment.durationSeconds != null) {
                        totalDuration += segment.durationSeconds
                    } else {
                        hasDuration = false
                    }
                }

                batchStart = batchEnd
            }
        }

        RoadRouteResult(
            path = simplifyPath(mergedPath, routingProperties.mapbox.maxRenderPoints),
            distanceMeters = totalDistance,
            durationSeconds = if (hasDuration) totalDuration else null,
            routingStatus = "ready",
            segments = segmentDetails,
        )
    }

    suspend fun fetchRouteAlternatives(
        from: GeoLocationDto,
        to: GeoLocationDto,
        count: Int = 3,
    ): List<RoadRouteResult> = withContext(Dispatchers.IO) {
        val safeCount = count.coerceIn(1, 3)
        val response = fetchMapboxDirections(
            waypoints = listOf(from, to),
            steps = false,
            alternatives = true,
        )

        val routes = response.routes.orEmpty()
            .take(safeCount)
            .map { mapRouteToRoadRouteResult(it) }

        if (routes.isEmpty()) {
            throw MapboxDirectionsException("Mapbox no devolvio rutas alternativas")
        }

        routes
    }

    suspend fun fetchRouteWithSteps(
        from: GeoLocationDto,
        to: GeoLocationDto,
    ): NavigationRouteResult = withContext(Dispatchers.IO) {
        val route = fetchPrimaryRoute(listOf(from, to), steps = true)
        mapRouteToNavigationResult(route)
    }

    private suspend fun fetchValidatedStreetSegment(pair: IndexedRoutePair): RoadRouteSegmentResult {
        val directDistance = haversineMeters(
            pair.from.latitude,
            pair.from.longitude,
            pair.to.latitude,
            pair.to.longitude,
        )
        val route = fetchPrimaryRoute(listOf(pair.from, pair.to), steps = false)
        val result = mapRouteToRoadRouteResult(route)

        if (result.path.size < 2) {
            throw MapboxDirectionsException("Mapbox devolvio un tramo vacio (${pair.index})")
        }

        val maxExpectedDistance = maxOf(directDistance * MAX_ROAD_TO_DIRECT_RATIO, directDistance + MAX_ROAD_EXTRA_METERS)
        if (directDistance > 0.0 && result.distanceMeters > maxExpectedDistance) {
            throw MapboxDirectionsException("Distancia por calles sospechosa en tramo ${pair.index + 1}")
        }

        return RoadRouteSegmentResult(
            index = pair.index,
            from = pair.from,
            to = pair.to,
            path = result.path,
            distanceMeters = result.distanceMeters,
            durationSeconds = result.durationSeconds,
            routingStatus = "ready",
        )
    }

    private suspend fun fetchRouteChunked(waypoints: List<GeoLocationDto>): RoadRouteResult = withContext(Dispatchers.IO) {
        val chunks = splitWaypointsIntoChunks(waypoints, routingProperties.mapbox.maxWaypoints)
        val mergedPath = mutableListOf<GeoLocationDto>()
        var totalDistance = 0.0
        var totalDuration = 0.0
        var hasDuration = true

        coroutineScope {
            var batchStart = 0
            while (batchStart < chunks.size) {
                if (batchStart > 0) {
                    delay(routingProperties.mapbox.batchDelayMs)
                }

                val batchEnd = minOf(batchStart + routingProperties.mapbox.maxConcurrent, chunks.size)
                val batch = chunks.subList(batchStart, batchEnd)
                val results = batch.map { chunk ->
                    async {
                        mapRouteToRoadRouteResult(fetchPrimaryRoute(chunk, steps = false))
                    }
                }.awaitAll()

                results.forEach { segment ->
                    appendPath(mergedPath, segment.path)
                    totalDistance += segment.distanceMeters
                    if (segment.durationSeconds != null) {
                        totalDuration += segment.durationSeconds
                    } else {
                        hasDuration = false
                    }
                }

                batchStart = batchEnd
            }
        }

        RoadRouteResult(
            path = simplifyPath(mergedPath, routingProperties.mapbox.maxRenderPoints),
            distanceMeters = totalDistance,
            durationSeconds = if (hasDuration) totalDuration else null,
            routingStatus = "ready",
        )
    }

    private suspend fun fetchPrimaryRoute(
        waypoints: List<GeoLocationDto>,
        steps: Boolean,
        alternatives: Boolean = false,
    ): MapboxDirectionsRoute {
        val response = fetchMapboxDirections(
            waypoints = waypoints,
            steps = steps,
            alternatives = alternatives,
        )
        return response.routes?.firstOrNull()
            ?: throw MapboxDirectionsException("Mapbox no devolvio ninguna ruta")
    }

    private suspend fun fetchMapboxDirections(
        waypoints: List<GeoLocationDto>,
        steps: Boolean,
        alternatives: Boolean = false,
    ): MapboxDirectionsResponse {
        require(waypoints.size >= 2) { "Se requieren al menos 2 waypoints" }
        require(waypoints.size <= routingProperties.mapbox.maxWaypoints) {
            "Mapbox Directions admite maximo ${routingProperties.mapbox.maxWaypoints} waypoints"
        }

        val accessToken = routingProperties.mapbox.accessToken.trim()
        if (accessToken.isEmpty()) {
            throw MapboxDirectionsException("MAPBOX_ACCESS_TOKEN es obligatorio para Mapbox Directions")
        }

        repeat(routingProperties.mapbox.maxRetries) { attempt ->
            try {
                val response = requestMapboxDirections(waypoints, steps, alternatives, accessToken)
                if (!response.code.isNullOrBlank() && !response.code.equals("Ok", ignoreCase = true)) {
                    throw MapboxDirectionsException("Mapbox respondio code=${response.code}")
                }
                return response
            } catch (error: MapboxDirectionsException) {
                if (attempt + 1 >= routingProperties.mapbox.maxRetries) {
                    throw error
                }
            } catch (error: WebClientResponseException) {
                if (attempt + 1 >= routingProperties.mapbox.maxRetries) {
                    throw MapboxDirectionsException(
                        "Error HTTP de Mapbox Directions: ${error.statusCode.value()}",
                        error,
                    )
                }
            } catch (error: Exception) {
                if (attempt + 1 >= routingProperties.mapbox.maxRetries) {
                    throw MapboxDirectionsException("Error al consultar Mapbox Directions", error)
                }
            }

            delay(500L * (attempt + 1))
        }

        throw MapboxDirectionsException("Mapbox Directions no respondio tras reintentos")
    }

    private suspend fun requestMapboxDirections(
        waypoints: List<GeoLocationDto>,
        steps: Boolean,
        alternatives: Boolean,
        accessToken: String,
    ): MapboxDirectionsResponse {
        val mapbox = routingProperties.mapbox
        val coordinatePath = waypoints.joinToString(";") { "${it.longitude},${it.latitude}" }
        val baseUrl = mapbox.baseUrl.trimEnd('/')
        val query = buildString {
            append("geometries=geojson")
            append("&overview=full")
            append("&steps=").append(steps)
            append("&language=").append(mapbox.language)
            if (alternatives) {
                append("&alternatives=true")
            }
            append("&access_token=").append(accessToken)
        }
        val url = "$baseUrl/${mapbox.profile}/$coordinatePath?$query"

        return webClient.get()
            .uri(url)
            .retrieve()
            .bodyToMono(MapboxDirectionsResponse::class.java)
            .awaitSingle()
    }

    private fun mapRouteToRoadRouteResult(route: MapboxDirectionsRoute): RoadRouteResult {
        val path = mapRoutePath(route)
        return RoadRouteResult(
            path = simplifyPath(path, routingProperties.mapbox.maxRenderPoints),
            distanceMeters = route.distance ?: 0.0,
            durationSeconds = route.duration,
            routingStatus = "ready",
        )
    }

    private fun mapRouteToNavigationResult(route: MapboxDirectionsRoute): NavigationRouteResult {
        val path = mapRoutePath(route)
        val steps = mapStepsFromRoute(route)
        if (steps.isEmpty()) {
            throw MapboxDirectionsException("Mapbox no devolvio pasos de navegacion")
        }

        return NavigationRouteResult(
            path = simplifyPath(path, routingProperties.mapbox.maxRenderPoints),
            distanceMeters = route.distance ?: 0.0,
            durationSeconds = route.duration,
            routingStatus = "ready",
            steps = steps,
        )
    }

    private fun mapRoutePath(route: MapboxDirectionsRoute): List<GeoLocationDto> {
        val coordinates = route.geometry?.coordinates ?: throw MapboxDirectionsException("Mapbox no devolvio geometria")
        if (coordinates.isEmpty()) {
            throw MapboxDirectionsException("Mapbox devolvio geometria vacia")
        }

        return coordinates.mapNotNull { coords ->
            if (coords.size < 2) {
                return@mapNotNull null
            }
            GeoLocationDto(latitude = coords[1], longitude = coords[0])
        }.ifEmpty {
            throw MapboxDirectionsException("Mapbox devolvio coordenadas invalidas")
        }
    }

    private fun mapStepsFromRoute(route: MapboxDirectionsRoute): List<NavigationStepResult> {
        return route.legs.orEmpty().flatMap { leg ->
            leg.steps.orEmpty().mapNotNull { step -> mapMapboxStep(step) }
        }
    }

    private fun mapMapboxStep(step: MapboxDirectionsStep): NavigationStepResult? {
        val maneuver = step.maneuver ?: return null
        val locationCoords = maneuver.location ?: return null
        if (locationCoords.size < 2) {
            return null
        }

        val instruction = maneuver.instruction?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val streetName = step.name?.trim()?.takeIf { it.isNotEmpty() }
            ?: step.ref?.trim()?.takeIf { it.isNotEmpty() }

        return NavigationStepResult(
            instruction = instruction,
            maneuverType = maneuver.type ?: "continue",
            modifier = maneuver.modifier,
            distanceMeters = step.distance ?: 0.0,
            durationSeconds = step.duration,
            location = GeoLocationDto(
                latitude = locationCoords[1],
                longitude = locationCoords[0],
            ),
            streetName = streetName,
        )
    }

    companion object {
        private const val MAX_ROAD_TO_DIRECT_RATIO = 6.0
        private const val MAX_ROAD_EXTRA_METERS = 2500.0

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

        private fun haversineMeters(
            lat1: Double,
            lon1: Double,
            lat2: Double,
            lon2: Double,
        ): Double {
            val earthRadiusMeters = 6371000.0
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
            return earthRadiusMeters * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        }
    }
}

private data class IndexedRoutePair(
    val index: Int,
    val from: GeoLocationDto,
    val to: GeoLocationDto,
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class MapboxDirectionsResponse(
    val code: String? = null,
    val routes: List<MapboxDirectionsRoute>? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class MapboxDirectionsRoute(
    val distance: Double? = null,
    val duration: Double? = null,
    val geometry: MapboxDirectionsGeometry? = null,
    val legs: List<MapboxDirectionsLeg>? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class MapboxDirectionsGeometry(
    val coordinates: List<List<Double>>? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class MapboxDirectionsLeg(
    val steps: List<MapboxDirectionsStep>? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class MapboxDirectionsStep(
    val distance: Double? = null,
    val duration: Double? = null,
    val name: String? = null,
    val ref: String? = null,
    val maneuver: MapboxDirectionsManeuver? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class MapboxDirectionsManeuver(
    val type: String? = null,
    val modifier: String? = null,
    val instruction: String? = null,
    val location: List<Double>? = null,
)
