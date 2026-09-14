package com.dscorp.wispadmin.wispadmin.util

import com.dscorp.wispadmin.wispadmin.dto.GeoLocationDto
import org.locationtech.jts.geom.LineString
import org.locationtech.jts.geom.Polygon
import kotlin.math.abs

object PlaceGeometryUtils {

    fun polygonToGeoJson(polygon: Polygon?): String? {
        if (polygon == null || polygon.isEmpty) {
            return null
        }

        val rings = mutableListOf<String>()
        rings.add(lineStringToGeoJsonCoordinates(polygon.exteriorRing))

        for (index in 0 until polygon.numInteriorRing) {
            rings.add(lineStringToGeoJsonCoordinates(polygon.getInteriorRingN(index)))
        }

        return """{"type":"Polygon","coordinates":[${rings.joinToString(",")}]}"""
    }

    fun polygonCentroid(polygon: Polygon?): GeoLocationDto? {
        if (polygon == null || polygon.isEmpty) {
            return null
        }

        val positions = mutableListOf<Pair<Double, Double>>()
        collectNormalizedPositions(polygon.exteriorRing, positions)
        if (positions.isEmpty()) {
            return null
        }

        val latitude = positions.map { it.second }.average()
        val longitude = positions.map { it.first }.average()
        return GeoLocationDto(latitude = latitude, longitude = longitude)
    }

    fun normalizeLonLat(x: Double, y: Double): Pair<Double, Double> {
        // En algunos registros el poligono viene guardado como (lat, lon) en (x, y).
        if (abs(x) <= 25.0 && abs(y) >= 55.0) {
            return y to x
        }
        return x to y
    }

    private fun collectNormalizedPositions(
        lineString: LineString,
        positions: MutableList<Pair<Double, Double>>,
    ) {
        for (index in 0 until lineString.numPoints) {
            val coordinate = lineString.getCoordinateN(index)
            val (longitude, latitude) = normalizeLonLat(coordinate.x, coordinate.y)
            positions.add(longitude to latitude)
        }
    }

    private fun lineStringToGeoJsonCoordinates(lineString: LineString): String {
        val coordinates = (0 until lineString.numPoints)
            .map { index ->
                val coordinate = lineString.getCoordinateN(index)
                val (longitude, latitude) = normalizeLonLat(coordinate.x, coordinate.y)
                "[$longitude,$latitude]"
            }
            .joinToString(",")

        return "[$coordinates]"
    }
}
