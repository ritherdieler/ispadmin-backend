package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.dto.GeoLocationDto
import com.dscorp.wispadmin.wispadmin.dto.SmartMapCollectionRouteDto
import com.dscorp.wispadmin.wispadmin.dto.SmartMapRoadRouteDto
import org.springframework.stereotype.Service

@Service
class SmartMapRoadRouteService(
    private val sectorValidationService: SectorValidationService,
    private val roadRoutingService: RoadRoutingService,
) {

    suspend fun buildRoadRoute(
        sector: String,
        origin: GeoLocationDto,
        destination: GeoLocationDto,
    ): SmartMapRoadRouteDto {
        sectorValidationService.requirePointsInsideSector(sector, origin, destination)
        val result = roadRoutingService.fetchRoute(listOf(origin, destination))
        return result.toDto(sector, origin, destination)
    }

    suspend fun enrichCollectionRoute(route: SmartMapCollectionRouteDto): SmartMapCollectionRouteDto {
        if (route.stopCount == 0 || route.path.size < 2) {
            return route
        }

        return try {
            val result = if (route.routeType == "sweep" && route.stops.size > 1) {
                enrichSweepRouteBySectorBlocks(route)
            } else {
                roadRoutingService.fetchRouteBySegments(route.path)
            }
            route.copy(
                roadPath = result.path,
                roadDistanceMeters = result.distanceMeters,
                roadDurationSeconds = result.durationSeconds,
                routingStatus = result.routingStatus,
                geometryGeoJson = RoadRoutingService.buildLineStringGeoJson(result.path),
            )
        } catch (_: Exception) {
            val fallbackPath = RoadRoutingService.simplifyPath(route.path, MAX_RENDER_POINTS)
            route.copy(
                roadPath = fallbackPath,
                roadDistanceMeters = null,
                roadDurationSeconds = null,
                routingStatus = "fallback",
                geometryGeoJson = RoadRoutingService.buildLineStringGeoJson(fallbackPath),
            )
        }
    }

    private suspend fun enrichSweepRouteBySectorBlocks(
        route: SmartMapCollectionRouteDto,
    ): RoadRouteResult {
        val sectorBlocks = mutableListOf<List<GeoLocationDto>>()
        var currentBlock = mutableListOf(route.startPoint)
        var currentSector = route.stops.firstOrNull()?.place?.trim()

        route.stops.forEach { stop ->
            val stopSector = stop.place?.trim()
            val activeSector = currentSector
            if (activeSector != null && stopSector != null && !sectorsEquivalent(activeSector, stopSector)) {
                if (currentBlock.size >= 2) {
                    sectorBlocks.add(currentBlock.toList())
                } else if (currentBlock.size == 1) {
                    sectorBlocks.add(listOf(currentBlock.first(), stop.location))
                }
                currentBlock = mutableListOf(currentBlock.lastOrNull() ?: route.startPoint)
                currentSector = stopSector
            } else if (currentSector == null && stopSector != null) {
                currentSector = stopSector
            }
            currentBlock.add(stop.location)
        }

        if (currentBlock.size >= 2) {
            sectorBlocks.add(currentBlock)
        } else if (currentBlock.size == 1 && route.stops.isNotEmpty()) {
            sectorBlocks.add(listOf(currentBlock.first(), route.stops.last().location))
        }

        if (sectorBlocks.isEmpty()) {
            return roadRoutingService.fetchRouteBySegments(route.path)
        }

        val mergedPath = mutableListOf<GeoLocationDto>()
        var totalDistance = 0.0
        var totalDuration = 0.0
        var hasDuration = true
        var failedBlocks = 0

        sectorBlocks.forEach { block ->
            val blockResult = roadRoutingService.fetchRouteBySegments(block)
            RoadRoutingService.appendPath(mergedPath, blockResult.path)
            totalDistance += blockResult.distanceMeters
            if (blockResult.durationSeconds != null) {
                totalDuration += blockResult.durationSeconds
            } else {
                hasDuration = false
            }
            if (blockResult.routingStatus == "fallback") {
                failedBlocks += 1
            }
        }

        val routingStatus = when {
            failedBlocks == 0 -> "ready"
            failedBlocks == sectorBlocks.size -> "fallback"
            else -> "partial"
        }

        return RoadRouteResult(
            path = RoadRoutingService.simplifyPath(mergedPath, MAX_RENDER_POINTS),
            distanceMeters = totalDistance,
            durationSeconds = if (hasDuration) totalDuration else null,
            routingStatus = routingStatus,
        )
    }

    private fun sectorsEquivalent(left: String, right: String): Boolean {
        return left.trim().lowercase() == right.trim().lowercase()
    }

    companion object {
        private const val MAX_RENDER_POINTS = 4_000
    }

    private fun RoadRouteResult.toDto(
        sector: String,
        origin: GeoLocationDto,
        destination: GeoLocationDto,
    ): SmartMapRoadRouteDto {
        return SmartMapRoadRouteDto(
            sectorName = sector.trim(),
            origin = origin,
            destination = destination,
            path = path,
            distanceMeters = distanceMeters,
            durationSeconds = durationSeconds,
            routingStatus = routingStatus,
            geometryGeoJson = RoadRoutingService.buildLineStringGeoJson(path),
        )
    }
}
