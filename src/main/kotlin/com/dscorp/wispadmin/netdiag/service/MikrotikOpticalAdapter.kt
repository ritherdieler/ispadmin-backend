package com.dscorp.wispadmin.netdiag.service

import com.dscorp.wispadmin.routeros.port.MikrotikSession
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(prefix = "net.diag", name = ["enabled"], havingValue = "true")
class MikrotikOpticalAdapter {

    fun collect(session: MikrotikSession, monitorConfig: TargetMonitorConfig): List<OpticalSnapshot> {
        if (monitorConfig.opticalInterfaces.isEmpty()) {
            return emptyList()
        }
        return monitorConfig.opticalInterfaces.mapNotNull { iface ->
            runCatching {
                val rows = session.call(
                    "/interface/ethernet/monitor",
                    mapOf("numbers" to iface, "once" to "")
                )
                val row = rows.firstOrNull() ?: return@runCatching null
                OpticalSnapshot(
                    interfaceName = row["name"].orEmpty().ifBlank { iface },
                    rxPowerDbm = parsePower(row["sfp-rx-power"] ?: row["rx-power"]),
                    txPowerDbm = parsePower(row["sfp-tx-power"] ?: row["tx-power"]),
                    temperatureC = parsePower(row["sfp-temperature"] ?: row["temperature"]),
                    sfpPresent = parseBool(row["sfp-module-present"] ?: row["sfp-present"])
                )
            }.getOrNull()
        }
    }

    private fun parsePower(raw: String?): Double? {
        if (raw.isNullOrBlank()) return null
        val cleaned = raw.trim().removeSuffix("dBm").removeSuffix("C").trim()
        return cleaned.toDoubleOrNull()
    }

    private fun parseBool(raw: String?): Boolean? {
        if (raw.isNullOrBlank()) return null
        return when (raw.lowercase()) {
            "true", "yes", "1" -> true
            "false", "no", "0" -> false
            else -> null
        }
    }
}
