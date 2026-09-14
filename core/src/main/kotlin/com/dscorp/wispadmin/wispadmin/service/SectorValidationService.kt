package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.dto.GeoLocationDto
import com.dscorp.wispadmin.wispadmin.exception.SmartMapSectorValidationException
import com.dscorp.wispadmin.wispadmin.repository.PlaceRepository
import org.locationtech.jts.geom.Coordinate
import org.springframework.stereotype.Service

@Service
class SectorValidationService(
    private val placeRepository: PlaceRepository,
) {
    fun requirePointsInsideSector(
        sector: String,
        origin: GeoLocationDto,
        destination: GeoLocationDto,
    ) {
        val normalizedSector = sector.trim()
        requireValidCoordinates(origin, "origen", normalizedSector)
        requireValidCoordinates(destination, "destino", normalizedSector)
        requireSectorWithPolygon(normalizedSector)

        if (!isInsideNamedPlace(normalizedSector, origin)) {
            throw SmartMapSectorValidationException(
                "El punto de origen esta fuera del poligono del sector $normalizedSector.",
                "ORIGIN_OUTSIDE_SECTOR",
                normalizedSector,
            )
        }

        if (!isInsideNamedPlace(normalizedSector, destination)) {
            throw SmartMapSectorValidationException(
                "El punto de destino esta fuera del poligono del sector $normalizedSector.",
                "DESTINATION_OUTSIDE_SECTOR",
                normalizedSector,
            )
        }
    }

    fun requirePointInsideSector(sector: String, point: GeoLocationDto) {
        val normalizedSector = sector.trim()
        requireValidCoordinates(point, "origen", normalizedSector)
        requireSectorWithPolygon(normalizedSector)

        if (!isInsideNamedPlace(normalizedSector, point)) {
            throw SmartMapSectorValidationException(
                "Tu ubicacion esta fuera del poligono del sector $normalizedSector.",
                "ORIGIN_OUTSIDE_SECTOR",
                normalizedSector,
            )
        }
    }

    fun sectorHasPolygon(sector: String): Boolean {
        val normalizedSector = sector.trim()
        if (normalizedSector.isEmpty()) {
            return false
        }
        val place = placeRepository.findByNormalizedName(normalizedSector).firstOrNull()
        return place?.area != null
    }

    private fun requireSectorWithPolygon(sector: String) {
        val place = placeRepository.findByNormalizedName(sector).firstOrNull()
            ?: throw SmartMapSectorValidationException(
                "Sector no encontrado: $sector.",
                "SECTOR_NOT_FOUND",
                sector,
            )

        if (place.area == null) {
            throw SmartMapSectorValidationException(
                "El sector $sector no tiene un poligono geografico definido.",
                "SECTOR_NO_POLYGON",
                sector,
            )
        }
    }

    private fun requireValidCoordinates(point: GeoLocationDto, label: String, sector: String) {
        if (point.latitude == 0.0 && point.longitude == 0.0) {
            throw SmartMapSectorValidationException(
                "Las coordenadas de $label no son validas.",
                "INVALID_COORDINATES",
                sector,
            )
        }

        if (point.latitude < PERU_MIN_LAT || point.latitude > PERU_MAX_LAT
            || point.longitude < PERU_MIN_LNG || point.longitude > PERU_MAX_LNG
        ) {
            throw SmartMapSectorValidationException(
                "Las coordenadas de $label estan fuera del rango permitido para Peru.",
                "INVALID_COORDINATES",
                sector,
            )
        }
    }

    private fun isInsideNamedPlace(sector: String, point: GeoLocationDto): Boolean {
        val place = placeRepository.findByNormalizedName(sector).firstOrNull() ?: return false
        val area = place.area ?: return false

        if (!isWithinEnvelope(area.envelopeInternal, point)) {
            return false
        }

        return placeRepository.findPlaceContainingPointInNamedPlace(
            sector,
            point.latitude,
            point.longitude,
        ) != null
    }

    private fun isWithinEnvelope(
        envelope: org.locationtech.jts.geom.Envelope,
        point: GeoLocationDto,
    ): Boolean {
        return envelope.covers(Coordinate(point.longitude, point.latitude))
    }

    companion object {
        private const val PERU_MIN_LAT = -18.5
        private const val PERU_MAX_LAT = 0.5
        private const val PERU_MIN_LNG = -81.5
        private const val PERU_MAX_LNG = -68.0
    }
}
