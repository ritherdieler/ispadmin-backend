package com.dscorp.wispadmin.oltgateway.parser

import org.springframework.stereotype.Component

@Component
class OpticalInfoParser {

    private val rowRegex = Regex(
        """(?m)^\s*(\d+)\s+(-?\d+(?:\.\d+)?|-)\s+(-?\d+(?:\.\d+)?|-)\s+(-?\d+(?:\.\d+)?|-)\s+(-?\d+(?:\.\d+)?|-)\s+(-?\d+(?:\.\d+)?|-)\s+(-?\d+(?:\.\d+)?|-)"""
    )

    fun parse(output: String, ontId: Int): ParsedOpticalInfo {
        return parseAll(output).firstOrNull { it.ontId == ontId }
            ?: parseDetail(output, ontId)
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

    private fun parseDetail(output: String, ontId: Int): ParsedOpticalInfo {
        return ParsedOpticalInfo(
            ontId = ontId,
            rxPowerDbm = detailValue(output, "Rx optical power(dBm)"),
            txPowerDbm = detailValue(output, "Tx optical power(dBm)"),
            oltRxPowerDbm = detailValue(output, "OLT Rx ONT optical power(dBm)"),
            temperatureC = detailValue(output, "Temperature(C)"),
            voltageV = detailValue(output, "Voltage(V)"),
            biasCurrentMa = detailValue(output, "Laser bias current(mA)")
        )
    }

    private fun detailValue(output: String, label: String): Double? {
        val regex = Regex("""(?m)^\s*${Regex.escape(label)}\s*:\s*(\S+)""")
        val raw = regex.find(output)?.groupValues?.get(1) ?: return null
        return toDoubleOrNull(raw)
    }

    private fun toDoubleOrNull(value: String): Double? {
        if (value == "-") return null
        return value.toDoubleOrNull()
    }
}
