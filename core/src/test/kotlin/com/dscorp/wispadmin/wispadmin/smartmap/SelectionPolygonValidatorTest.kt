package com.dscorp.wispadmin.wispadmin.smartmap

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SelectionPolygonValidatorTest {

    private val validPolygon = """
        {
          "type": "Polygon",
          "coordinates": [[
            [-77.40, -11.23],
            [-77.36, -11.23],
            [-77.36, -11.26],
            [-77.40, -11.26],
            [-77.40, -11.23]
          ]]
        }
    """.trimIndent()

    @Test
    fun `parseAndValidate returns normalized geo json for valid polygon`() {
        val result = SelectionPolygonValidator.parseAndValidate(validPolygon)

        assertTrue(result.contains("\"type\":\"Polygon\""))
        assertTrue(result.contains("coordinates"))
    }

    @Test
    fun `parseAndValidate closes open ring`() {
        val openRing = """
            {
              "type": "Polygon",
              "coordinates": [[
                [-77.40, -11.23],
                [-77.36, -11.23],
                [-77.36, -11.26],
                [-77.40, -11.26]
              ]]
            }
        """.trimIndent()

        val result = SelectionPolygonValidator.parseAndValidate(openRing)
        assertTrue(result.contains("-77.4") && result.contains("-11.23"))
    }

    @Test
    fun `parseAndValidate rejects fewer than three vertices`() {
        val tooSmall = """
            {
              "type": "Polygon",
              "coordinates": [[
                [-77.40, -11.23],
                [-77.36, -11.23],
                [-77.40, -11.23]
              ]]
            }
        """.trimIndent()

        assertThrows(IllegalArgumentException::class.java) {
            SelectionPolygonValidator.parseAndValidate(tooSmall)
        }
    }

    @Test
    fun `parseAndValidate rejects polygon outside Peru bounds`() {
        val outsidePeru = """
            {
              "type": "Polygon",
              "coordinates": [[
                [0.0, 0.0],
                [1.0, 0.0],
                [1.0, 1.0],
                [0.0, 1.0],
                [0.0, 0.0]
              ]]
            }
        """.trimIndent()

        val exception = assertThrows(IllegalArgumentException::class.java) {
            SelectionPolygonValidator.parseAndValidate(outsidePeru)
        }
        assertEquals("El polígono debe estar dentro de Perú", exception.message)
    }

    @Test
    fun `parseAndValidate rejects area below minimum`() {
        val tiny = """
            {
              "type": "Polygon",
              "coordinates": [[
                [-77.376278, -11.233708],
                [-77.376270, -11.233708],
                [-77.376270, -11.233700],
                [-77.376278, -11.233700],
                [-77.376278, -11.233708]
              ]]
            }
        """.trimIndent()

        val exception = assertThrows(IllegalArgumentException::class.java) {
            SelectionPolygonValidator.parseAndValidate(tiny)
        }
        assertEquals("El polígono es demasiado pequeño", exception.message)
    }

    @Test
    fun `parseAndValidate rejects non polygon geometry`() {
        val point = """{"type":"Point","coordinates":[-77.38,-11.24]}"""

        assertThrows(IllegalArgumentException::class.java) {
            SelectionPolygonValidator.parseAndValidate(point)
        }
    }

    @Test
    fun `requireExclusiveSelection rejects place and polygon together`() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            SelectionPolygonValidator.requireExclusiveSelection("la villa", validPolygon)
        }
        assertEquals("No se puede enviar place y selectionPolygonGeoJson juntos", exception.message)
    }

    @Test
    fun `toWkt converts normalized polygon to mysql wkt`() {
        val normalized = SelectionPolygonValidator.parseAndValidate(validPolygon)

        val wkt = SelectionPolygonValidator.toWkt(normalized)

        assertTrue(wkt.startsWith("POLYGON(("))
        assertTrue(wkt.contains("-77.4"))
        assertTrue(wkt.contains("-11.23"))
    }
}
