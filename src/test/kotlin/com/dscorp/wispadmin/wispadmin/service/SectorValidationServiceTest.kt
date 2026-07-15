package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.data.model.Place
import com.dscorp.wispadmin.wispadmin.dto.GeoLocationDto
import com.dscorp.wispadmin.wispadmin.exception.SmartMapSectorValidationException
import com.dscorp.wispadmin.wispadmin.repository.PlaceRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.Polygon
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class SectorValidationServiceTest {

    private val placeRepository = mock(PlaceRepository::class.java)
    private val service = SectorValidationService(placeRepository)
    private val geometryFactory = GeometryFactory()

    @Test
    fun `requirePointInsideSector throws when point is outside polygon`() {
        val polygon = geometryFactory.createPolygon(
            arrayOf(
                Coordinate(-77.40, -11.23),
                Coordinate(-77.36, -11.23),
                Coordinate(-77.36, -11.26),
                Coordinate(-77.40, -11.26),
                Coordinate(-77.40, -11.23),
            ),
        )
        val place = Place(id = 1, name = "la villa", area = polygon)
        `when`(placeRepository.findByNormalizedName("la villa")).thenReturn(listOf(place))
        `when`(
            placeRepository.findPlaceContainingPointInNamedPlace("la villa", -11.50, -77.50),
        ).thenReturn(null)

        val exception = assertThrows(SmartMapSectorValidationException::class.java) {
            service.requirePointInsideSector(
                "la villa",
                GeoLocationDto(-11.50, -77.50),
            )
        }

        assertEquals("ORIGIN_OUTSIDE_SECTOR", exception.code)
    }

    @Test
    fun `requirePointsInsideSector throws when sector has no polygon`() {
        val place = Place(id = 2, name = "sector x", area = null)
        `when`(placeRepository.findByNormalizedName("sector x")).thenReturn(listOf(place))

        val exception = assertThrows(SmartMapSectorValidationException::class.java) {
            service.requirePointsInsideSector(
                "sector x",
                GeoLocationDto(-11.24, -77.38),
                GeoLocationDto(-11.25, -77.39),
            )
        }

        assertEquals("SECTOR_NO_POLYGON", exception.code)
    }
}
