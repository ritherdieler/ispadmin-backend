package com.dscorp.wispadmin.wispadmin.data.model.util

import com.dscorp.wispadmin.wispadmin.data.model.GeoLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class GeoLocationConverterTest {

    private val converter = GeoLocationConverter()

    @Test
    fun convertToEntityAttribute_returnsNullWhenDbDataIsNull() {
        assertNull(converter.convertToEntityAttribute(null))
    }

    @Test
    fun convertToEntityAttribute_roundTripsJson() {
        val json = """{"latitude":-11.23,"longitude":-77.37}"""
        val geo = converter.convertToEntityAttribute(json)
        assertEquals(-11.23, geo!!.latitude, 0.0001)
        assertEquals(-77.37, geo.longitude, 0.0001)
        assertEquals(json, converter.convertToDatabaseColumn(GeoLocation(-11.23, -77.37)))
    }
}
