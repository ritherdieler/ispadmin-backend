package com.dscorp.wispadmin.oltgateway.parser

import org.springframework.stereotype.Component

@Component
class OpticalInfoParser {

    private val rowRegex = Regex(
        """(?m)^\s*(\d+)\s+(-?\d+(?:\.\d+)?|-)\s+(-?\d+(?:\.\d+)?|-)\s+(-?\d+(?:\.\d+)?|-)\s+(-?\d+(?:\.\d+)?|-)\s+(-?\d+(?:\.\d+)?|-)\s+(-?\d+(?:\.\d+)?|-)"""
    )

    fun parse(output: String, ontId: Int): ParsedOpticalInfo {
        return parseAll(output).firstOrNull { it.ontId == ontId }
            ?: ParsedOpticalInfo(ontId = ontId)
    }

    fun parseAll(output: String): List<ParsedOpticalInfo> {
        return rowRegex.findAll(output)
            .map { match ->
                val g = match.groupValues
                ParsedOpticalInfo(
                    ontId = g[1].toInt(),
                    rxPowerDbm = toDoubleOrNull(g[2]),
                    txPowerDbm = toDoubleOrNull(g[3]),
                    oltRxPowerDbm = toDoubleOrNull(g[4]),
                    temperatureC = toDoubleOrNull(g[5]),
                    voltageV = toDoubleOrNull(g[6]),
                    biasCurrentMa = toDoubleOrNull(g[7])
                )
            }
            .toList()
    }

    private fun toDoubleOrNull(value: String): Double? {
        if (value == "-") return null
        return value.toDoubleOrNull()
    }
}
