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
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

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

data class VoiceInstructionResult(
    val distanceAlongGeometry: Double,
    val announcement: String,
)

data class BannerInstructionResult(
    val distanceAlongGeometry: Double,
    val primaryText: String,
    val secondaryText: String? = null,
    val type: String? = null,
    val modifier: String? = null,
)

data class NavigationRouteResult(
    val path: List<GeoLocationDto>,
    val distanceMeters: Double,
    val durationSeconds: Double?,
    val routingStatus: String,
    val steps: List<NavigationStepResult>,
    val voiceInstructions: List<VoiceInstructionResult> = emptyList(),
    val bannerInstructions: List<BannerInstructionResult> = emptyList(),
    val congestion: List<String> = emptyList(),
    val durationAnnotations: List<Double> = emptyList(),
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

        val failedSegments = segmentDetails.count { it.routingStatus != "ready" }
        val routingStatus = when {
            failedSegments == 0 -> "ready"
            failedSegments == segmentDetails.size -> "fallback"
            else -> "partial"
        }

        RoadRouteResult(
            path = simplifyPath(mergedPath, routingProperties.mapbox.maxRenderPoints),
            distanceMeters = totalDistance,
            durationSeconds = if (hasDuration) totalDuration else null,
            routingStatus = routingStatus,
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
        destinationName: String? = null,
        avoidManeuverRadiusMeters: Int? = null,
    ): NavigationRouteResult = withContext(Dispatchers.IO) {
        val normalizedDestinationName = destinationName?.trim()?.takeIf { it.isNotEmpty() }
        val waypointNames = if (normalizedDestinationName != null) {
            listOf("", normalizedDestinationName)
        } else {
            null
        }
        val route = fetchPrimaryRoute(
            waypoints = listOf(from, to),
            steps = true,
            waypointNames = waypointNames,
            avoidManeuverRadiusMeters = avoidManeuverRadiusMeters,
        )
        mapRouteToNavigationResult(route)
    }

    suspend fun orderByNearestDuration(
        origin: GeoLocationDto,
        destinations: List<GeoLocationDto>,
    ): List<Int> = withContext(Dispatchers.IO) {
        if (destinations.isEmpty()) {
            return@withContext emptyList()
        }
        if (destinations.size == 1) {
            return@withContext listOf(0)
        }

        val remaining = destinations.indices.toMutableList()
        val ordered = mutableListOf<Int>()
        var current = origin

        while (remaining.isNotEmpty()) {
            val durations = fetchDurationsFromOrigin(
                origin = current,
                destinations = remaining.map { destinations[it] },
            )
            val nearestLocalIndex = durations.indices.minByOrNull { index ->
                durations[index] ?: Double.POSITIVE_INFINITY
            } ?: throw MapboxDirectionsException("Matrix no devolvio duraciones")

            if (durations[nearestLocalIndex] == null || durations[nearestLocalIndex]!!.isInfinite()) {
                throw MapboxDirectionsException("Matrix no pudo calcular duracion a destino")
            }

            val chosenGlobalIndex = remaining.removeAt(nearestLocalIndex)
            ordered.add(chosenGlobalIndex)
            current = destinations[chosenGlobalIndex]
        }

        ordered
    }

    private suspend fun fetchDurationsFromOrigin(
        origin: GeoLocationDto,
        destinations: List<GeoLocationDto>,
    ): List<Double?> {
        if (destinations.isEmpty()) {
            return emptyList()
        }

        val maxCoords = routingProperties.mapbox.matrixMaxCoordinates.coerceAtLeast(2)
        val maxDestinationsPerRequest = (maxCoords - 1).coerceAtLeast(1)
        val durations = MutableList<Double?>(destinations.size) { null }

        var offset = 0
        while (offset < destinations.size) {
            if (offset > 0) {
                delay(routingProperties.mapbox.batchDelayMs)
            }

            val end = minOf(offset + maxDestinationsPerRequest, destinations.size)
            val batch = destinations.subList(offset, end)
            val batchDurations = fetchMatrixDurations(origin, batch)
            for (index in batchDurations.indices) {
                durations[offset + index] = batchDurations[index]
            }
            offset = end
        }

        return durations
    }

    private suspend fun fetchMatrixDurations(
        origin: GeoLocationDto,
        destinations: List<GeoLocationDto>,
    ): List<Double?> {
        require(destinations.isNotEmpty()) { "Se requiere al menos un destino para Matrix" }

        val accessToken = resolveMapboxAccessToken()
        if (accessToken.isEmpty()) {
            throw MapboxDirectionsException("MAPBOX_ACCESS_TOKEN es obligatorio para Mapbox Matrix")
        }

        val coordinates = buildList {
            add(origin)
            addAll(destinations)
        }
        val destinationIndexes = destinations.indices.joinToString(";") { (it + 1).toString() }
        val mapbox = routingProperties.mapbox
        val coordinatePath = coordinates.joinToString(";") { "${it.longitude},${it.latitude}" }
        val baseUrl = mapbox.matrixBaseUrl.trimEnd('/')
        val query = buildString {
            append("annotations=duration")
            append("&sources=0")
            append("&destinations=").append(destinationIndexes)
            append("&access_token=").append(accessToken)
        }
        val url = "$baseUrl/${mapbox.profile}/$coordinatePath?$query"

        repeat(mapbox.maxRetries) { attempt ->
            try {
                val response = webClient.get()
                    .uri(URI.create(url))
                    .retrieve()
                    .bodyToMono(MapboxMatrixResponse::class.java)
                    .awaitSingle()

                if (!response.code.isNullOrBlank() && !response.code.equals("Ok", ignoreCase = true)) {
                    throw MapboxDirectionsException("Mapbox Matrix respondio code=${response.code}")
                }

                val row = response.durations?.firstOrNull()
                    ?: throw MapboxDirectionsException("Mapbox Matrix no devolvio duraciones")

                if (row.size != destinations.size) {
                    throw MapboxDirectionsException(
                        "Mapbox Matrix devolvio ${row.size} duraciones, se esperaban ${destinations.size}",
                    )
                }

                return row
            } catch (error: MapboxDirectionsException) {
                if (attempt + 1 >= mapbox.maxRetries) {
                    throw error
                }
            } catch (error: WebClientResponseException) {
                if (attempt + 1 >= mapbox.maxRetries) {
                    throw MapboxDirectionsException(
                        "Error HTTP de Mapbox Matrix: ${error.statusCode.value()}",
                        error,
                    )
                }
            } catch (error: Exception) {
                if (attempt + 1 >= mapbox.maxRetries) {
                    throw MapboxDirectionsException("Error al consultar Mapbox Matrix", error)
                }
            }

            delay(500L * (attempt + 1))
        }

        throw MapboxDirectionsException("Mapbox Matrix no respondio tras reintentos")
    }

    private suspend fun fetchValidatedStreetSegment(pair: IndexedRoutePair): RoadRouteSegmentResult {
        val directDistance = haversineMeters(
            pair.from.latitude,
            pair.from.longitude,
            pair.to.latitude,
            pair.to.longitude,
        )
        val fallbackSegment = RoadRouteSegmentResult(
            index = pair.index,
            from = pair.from,
            to = pair.to,
            path = listOf(pair.from, pair.to),
            distanceMeters = directDistance,
            durationSeconds = null,
            routingStatus = "fallback",
            fallbackReason = "straight_line",
        )

        return try {
            val route = fetchPrimaryRoute(listOf(pair.from, pair.to), steps = false)
            val result = mapRouteToRoadRouteResult(route)

            if (result.path.size < 2) {
                return fallbackSegment.copy(fallbackReason = "empty_path")
            }

            val maxExpectedDistance = maxOf(
                directDistance * MAX_ROAD_TO_DIRECT_RATIO,
                directDistance + MAX_ROAD_EXTRA_METERS,
            )
            if (directDistance > 0.0 && result.distanceMeters > maxExpectedDistance) {
                return fallbackSegment.copy(fallbackReason = "suspicious_distance")
            }

            RoadRouteSegmentResult(
                index = pair.index,
                from = pair.from,
                to = pair.to,
                path = result.path,
                distanceMeters = result.distanceMeters,
                durationSeconds = result.durationSeconds,
                routingStatus = "ready",
            )
        } catch (_: Exception) {
            fallbackSegment.copy(fallbackReason = "mapbox_error")
        }
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
        waypointNames: List<String>? = null,
        avoidManeuverRadiusMeters: Int? = null,
    ): MapboxDirectionsRoute {
        val response = fetchMapboxDirections(
            waypoints = waypoints,
            steps = steps,
            alternatives = alternatives,
            waypointNames = waypointNames,
            avoidManeuverRadiusMeters = avoidManeuverRadiusMeters,
        )
        return response.routes?.firstOrNull()
            ?: throw MapboxDirectionsException("Mapbox no devolvio ninguna ruta")
    }

    private suspend fun fetchMapboxDirections(
        waypoints: List<GeoLocationDto>,
        steps: Boolean,
        alternatives: Boolean = false,
        waypointNames: List<String>? = null,
        avoidManeuverRadiusMeters: Int? = null,
    ): MapboxDirectionsResponse {
        require(waypoints.size >= 2) { "Se requieren al menos 2 waypoints" }
        require(waypoints.size <= routingProperties.mapbox.maxWaypoints) {
            "Mapbox Directions admite maximo ${routingProperties.mapbox.maxWaypoints} waypoints"
        }

        val accessToken = resolveMapboxAccessToken()
        if (accessToken.isEmpty()) {
            throw MapboxDirectionsException("MAPBOX_ACCESS_TOKEN es obligatorio para Mapbox Directions")
        }

        repeat(routingProperties.mapbox.maxRetries) { attempt ->
            try {
                val response = requestMapboxDirections(
                    waypoints = waypoints,
                    steps = steps,
                    alternatives = alternatives,
                    accessToken = accessToken,
                    waypointNames = waypointNames,
                    avoidManeuverRadiusMeters = avoidManeuverRadiusMeters,
                )
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
                        "Error HTTP de Mapbox Directions: ${error.statusCode.value()} ${error.responseBodyAsString.take(240)}",
                        error,
                    )
                }
            } catch (error: Exception) {
                if (attempt + 1 >= routingProperties.mapbox.maxRetries) {
                    throw MapboxDirectionsException(
                        "Error al consultar Mapbox Directions: ${error.javaClass.simpleName}: ${error.message}",
                        error,
                    )
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
        waypointNames: List<String>?,
        avoidManeuverRadiusMeters: Int?,
    ): MapboxDirectionsResponse {
        val mapbox = routingProperties.mapbox
        val coordinatePath = waypoints.joinToString(";") { "${it.longitude},${it.latitude}" }
        val baseUrl = mapbox.baseUrl.trimEnd('/')
        val approachesValue = List(waypoints.size) { mapbox.approaches.trim().ifBlank { "curb" } }
            .joinToString(";")
        val query = buildString {
            append("geometries=geojson")
            append("&overview=full")
            append("&steps=").append(steps)
            append("&language=").append(encodeQuery(mapbox.language))
            append("&approaches=").append(encodeQuery(approachesValue))
            append("&annotations=").append(encodeQuery(mapbox.annotations))
            if (steps) {
                append("&voice_instructions=true")
                append("&banner_instructions=true")
                append("&voice_units=").append(encodeQuery(mapbox.voiceUnits))
                if (waypointNames != null && waypointNames.size == waypoints.size) {
                    val names = waypointNames.joinToString(";") { encodeWaypointName(it) }
                    append("&waypoint_names=").append(names)
                }
            }
            if (alternatives) {
                append("&alternatives=true")
            }
            if (avoidManeuverRadiusMeters != null && avoidManeuverRadiusMeters > 0) {
                val radius = avoidManeuverRadiusMeters.coerceIn(1, 1000)
                append("&avoid_maneuver_radius=").append(radius)
            }
            append("&access_token=").append(accessToken)
        }
        val url = "$baseUrl/${mapbox.profile}/$coordinatePath?$query"

        return webClient.get()
            .uri(URI.create(url))
            .retrieve()
            .bodyToMono(MapboxDirectionsResponse::class.java)
            .awaitSingle()
    }

    private fun resolveMapboxAccessToken(): String {
        val fromProperties = routingProperties.mapbox.accessToken.trim()
        if (fromProperties.isNotEmpty()) {
            return fromProperties
        }
        return System.getenv("MAPBOX_ACCESS_TOKEN")?.trim().orEmpty()
            .ifEmpty { System.getProperty("MAPBOX_ACCESS_TOKEN")?.trim().orEmpty() }
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
            voiceInstructions = mapVoiceInstructionsFromRoute(route),
            bannerInstructions = mapBannerInstructionsFromRoute(route),
            congestion = mapCongestionFromRoute(route),
            durationAnnotations = mapDurationAnnotationsFromRoute(route),
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

    private fun mapVoiceInstructionsFromRoute(route: MapboxDirectionsRoute): List<VoiceInstructionResult> {
        var stepStartDistance = 0.0
        val instructions = mutableListOf<VoiceInstructionResult>()
        for (leg in route.legs.orEmpty()) {
            for (step in leg.steps.orEmpty()) {
                val stepDistance = step.distance ?: 0.0
                for (instruction in step.voiceInstructions.orEmpty()) {
                    val announcement = instruction.announcement?.trim()?.takeIf { it.isNotEmpty() }
                        ?: continue
                    instructions.add(
                        VoiceInstructionResult(
                            distanceAlongGeometry = toAbsoluteDistanceAlongGeometry(
                                stepStartDistance = stepStartDistance,
                                stepDistance = stepDistance,
                                remainingAlongStep = instruction.distanceAlongGeometry ?: 0.0,
                            ),
                            announcement = announcement,
                        ),
                    )
                }
                stepStartDistance += stepDistance
            }
        }
        return instructions
    }

    private fun mapBannerInstructionsFromRoute(route: MapboxDirectionsRoute): List<BannerInstructionResult> {
        var stepStartDistance = 0.0
        val instructions = mutableListOf<BannerInstructionResult>()
        for (leg in route.legs.orEmpty()) {
            for (step in leg.steps.orEmpty()) {
                val stepDistance = step.distance ?: 0.0
                for (instruction in step.bannerInstructions.orEmpty()) {
                    val primaryText = instruction.primary?.text?.trim()?.takeIf { it.isNotEmpty() }
                        ?: continue
                    instructions.add(
                        BannerInstructionResult(
                            distanceAlongGeometry = toAbsoluteDistanceAlongGeometry(
                                stepStartDistance = stepStartDistance,
                                stepDistance = stepDistance,
                                remainingAlongStep = instruction.distanceAlongGeometry ?: 0.0,
                            ),
                            primaryText = primaryText,
                            secondaryText = instruction.secondary?.text?.trim()?.takeIf { it.isNotEmpty() },
                            type = instruction.primary?.type,
                            modifier = instruction.primary?.modifier,
                        ),
                    )
                }
                stepStartDistance += stepDistance
            }
        }
        return instructions
    }

    private fun mapCongestionFromRoute(route: MapboxDirectionsRoute): List<String> {
        return route.legs.orEmpty().flatMap { leg ->
            leg.annotation?.congestion.orEmpty().map { value -> value ?: "unknown" }
        }
    }

    private fun mapDurationAnnotationsFromRoute(route: MapboxDirectionsRoute): List<Double> {
        return route.legs.orEmpty().flatMap { leg ->
            leg.annotation?.duration.orEmpty().mapNotNull { value -> value }
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

        fun toAbsoluteDistanceAlongGeometry(
            stepStartDistance: Double,
            stepDistance: Double,
            remainingAlongStep: Double,
        ): Double {
            val clampedRemaining = remainingAlongStep.coerceIn(0.0, stepDistance.coerceAtLeast(0.0))
            val traveledInStep = (stepDistance - clampedRemaining).coerceAtLeast(0.0)
            return (stepStartDistance + traveledInStep).coerceAtLeast(0.0)
        }

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

        private fun encodeQuery(value: String): String {
            return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")
        }

        private fun encodeWaypointName(value: String): String {
            if (value.isEmpty()) {
                return ""
            }
            return encodeQuery(value.replace(";", ","))
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
    val annotation: MapboxDirectionsAnnotation? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class MapboxDirectionsAnnotation(
    val duration: List<Double?>? = null,
    val congestion: List<String?>? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class MapboxDirectionsStep(
    val distance: Double? = null,
    val duration: Double? = null,
    val name: String? = null,
    val ref: String? = null,
    val maneuver: MapboxDirectionsManeuver? = null,
    val voiceInstructions: List<MapboxVoiceInstruction>? = null,
    val bannerInstructions: List<MapboxBannerInstruction>? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class MapboxDirectionsManeuver(
    val type: String? = null,
    val modifier: String? = null,
    val instruction: String? = null,
    val location: List<Double>? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class MapboxVoiceInstruction(
    val distanceAlongGeometry: Double? = null,
    val announcement: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class MapboxBannerInstruction(
    val distanceAlongGeometry: Double? = null,
    val primary: MapboxBannerText? = null,
    val secondary: MapboxBannerText? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class MapboxBannerText(
    val text: String? = null,
    val type: String? = null,
    val modifier: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class MapboxMatrixResponse(
    val code: String? = null,
    val durations: List<List<Double?>>? = null,
)
