package com.dscorp.wispadmin.wispadmin.smartmap

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import kotlin.math.abs
import kotlin.math.cos

object SelectionPolygonValidator {

    private const val PERU_MIN_LAT = -18.5
    private const val PERU_MAX_LAT = 0.0
    private const val PERU_MIN_LNG = -82.0
    private const val PERU_MAX_LNG = -68.0
    private const val MIN_AREA_SQUARE_METERS = 500.0

    private val objectMapper = ObjectMapper()
    private val geometryFactory = GeometryFactory()

    fun requireExclusiveSelection(place: String?, selectionPolygonGeoJson: String?) {
        val normalizedPlace = place?.trim()?.takeIf { it.isNotEmpty() }
        val normalizedPolygon = selectionPolygonGeoJson?.trim()?.takeIf { it.isNotEmpty() }
        if (normalizedPlace != null && normalizedPolygon != null) {
            throw IllegalArgumentException("No se puede enviar place y selectionPolygonGeoJson juntos")
        }
    }

    fun parseAndValidate(rawGeoJson: String): String {
        val trimmed = rawGeoJson.trim()
        if (trimmed.isEmpty()) {
            throw IllegalArgumentException("selectionPolygonGeoJson es requerido")
        }

        val root = try {
            objectMapper.readTree(trimmed)
        } catch (_: Exception) {
            throw IllegalArgumentException("selectionPolygonGeoJson no es JSON válido")
        }

        if (root.path("type").asText() != "Polygon") {
            throw IllegalArgumentException("selectionPolygonGeoJson debe ser un Polygon")
        }

        val ringNode = root.path("coordinates").path(0)
        if (!ringNode.isArray || ringNode.size() < 3) {
            throw IllegalArgumentException("El polígono debe tener al menos 3 vértices")
        }

        val coordinates = mutableListOf<Pair<Double, Double>>()
        for (index in 0 until ringNode.size()) {
            val node = ringNode.get(index)
            if (!node.isArray || node.size() < 2) {
                throw IllegalArgumentException("Coordenadas de polígono inválidas")
            }
            val lng = node.get(0).asDouble()
            val lat = node.get(1).asDouble()
            if (!isWithinPeruBounds(lat, lng)) {
                throw IllegalArgumentException("El polígono debe estar dentro de Perú")
            }
            coordinates.add(lng to lat)
        }

        val closedCoordinates = closeRing(coordinates)
        if (distinctVertexCount(closedCoordinates) < 3) {
            throw IllegalArgumentException("El polígono debe tener al menos 3 vértices")
        }

        val jtsCoordinates = closedCoordinates.map { (lng, lat) -> Coordinate(lng, lat) }.toTypedArray()
        val polygon = geometryFactory.createPolygon(jtsCoordinates)
        if (!polygon.isValid) {
            throw IllegalArgumentException("El polígono no es válido")
        }

        val areaSquareMeters = approximateAreaSquareMeters(closedCoordinates)
        if (areaSquareMeters < MIN_AREA_SQUARE_METERS) {
            throw IllegalArgumentException("El polígono es demasiado pequeño")
        }

        val normalizedRing = closedCoordinates.joinToString(",") { (lng, lat) -> "[$lng,$lat]" }
        return """{"type":"Polygon","coordinates":[[$normalizedRing]]}"""
    }

    fun toWkt(normalizedGeoJson: String): String {
        val root = try {
            objectMapper.readTree(normalizedGeoJson)
        } catch (_: Exception) {
            throw IllegalArgumentException("selectionPolygonGeoJson no es JSON válido")
        }

        if (root.path("type").asText() != "Polygon") {
            throw IllegalArgumentException("selectionPolygonGeoJson debe ser un Polygon")
        }

        val ringNode = root.path("coordinates").path(0)
        if (!ringNode.isArray || ringNode.size() < 3) {
            throw IllegalArgumentException("El polígono debe tener al menos 3 vértices")
        }

        val points = buildList {
            for (index in 0 until ringNode.size()) {
                val node = ringNode.get(index)
                if (!node.isArray || node.size() < 2) {
                    throw IllegalArgumentException("Coordenadas de polígono inválidas")
                }
                add("${node.get(0).asDouble()} ${node.get(1).asDouble()}")
            }
        }

        return "POLYGON((${points.joinToString(", ")}))"
    }

    private fun closeRing(coordinates: List<Pair<Double, Double>>): List<Pair<Double, Double>> {
        if (coordinates.isEmpty()) {
            return coordinates
        }
        val first = coordinates.first()
        val last = coordinates.last()
        return if (first.first == last.first && first.second == last.second) {
            coordinates
        } else {
            coordinates + first
        }
    }

    private fun distinctVertexCount(coordinates: List<Pair<Double, Double>>): Int {
        val openVertices = if (
            coordinates.size > 1 &&
            coordinates.first().first == coordinates.last().first &&
            coordinates.first().second == coordinates.last().second
        ) {
            coordinates.dropLast(1)
        } else {
            coordinates
        }
        return openVertices.distinctBy { (lng, lat) ->
            "${"%.6f".format(lng)}:${"%.6f".format(lat)}"
        }.size
    }

    private fun approximateAreaSquareMeters(coordinates: List<Pair<Double, Double>>): Double {
        val openVertices = coordinates.dropLast(1)
        if (openVertices.size < 3) {
            return 0.0
        }

        val refLat = openVertices.map { it.second }.average()
        val refLng = openVertices.map { it.first }.average()
        val latScale = 111_320.0
        val lngScale = 111_320.0 * cos(Math.toRadians(refLat))

        val projected = openVertices.map { (lng, lat) ->
            (lng - refLng) * lngScale to (lat - refLat) * latScale
        }

        var area = 0.0
        for (index in projected.indices) {
            val current = projected[index]
            val next = projected[(index + 1) % projected.size]
            area += current.first * next.second - next.first * current.second
        }
        return abs(area) / 2.0
    }

    private fun isWithinPeruBounds(latitude: Double, longitude: Double): Boolean {
        return latitude in PERU_MIN_LAT..PERU_MAX_LAT &&
            longitude in PERU_MIN_LNG..PERU_MAX_LNG
    }
}
