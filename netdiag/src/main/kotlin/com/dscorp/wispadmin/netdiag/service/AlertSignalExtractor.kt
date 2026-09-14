package com.dscorp.wispadmin.netdiag.service

import com.dscorp.wispadmin.netdiag.config.NetDiagProperties
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(prefix = "net.diag", name = ["enabled"], havingValue = "true")
class AlertSignalExtractor(
    private val properties: NetDiagProperties
) {

    fun fromSnapshot(targetId: Long, snapshot: PollSnapshot): List<AlertSignal> {
        val signals = mutableListOf<AlertSignal>()
        val critical = snapshot.criticalInterfaces.map { it.lowercase() }.toSet()

        snapshot.interfaces.forEach { iface ->
            val isGre = iface.type.contains("gre", ignoreCase = true) ||
                iface.name.contains("gre", ignoreCase = true)
            val isCritical = critical.contains(iface.name.lowercase())
            if (!iface.disabled && !iface.running) {
                when {
                    isGre -> signals += signal(
                        targetId = targetId,
                        reasonCode = "GRE_TUNNEL_DOWN",
                        severity = "P0",
                        title = "GRE tunnel down: ${iface.name}",
                        component = iface.name
                    )
                    isCritical -> signals += signal(
                        targetId = targetId,
                        reasonCode = "LINK_DOWN",
                        severity = "P0",
                        title = "Link down: ${iface.name}",
                        component = iface.name
                    )
                }
            }
        }

        snapshot.health.forEach { item ->
            val name = item.name.lowercase()
            val value = item.value.lowercase()
            when {
                name.contains("psu") && isFailValue(value) -> signals += signal(
                    targetId, "PSU_FAIL", "P0", "PSU failure: ${item.name}", item.name
                )
                name.contains("fan") && isFailValue(value) -> signals += signal(
                    targetId, "FAN_FAIL", "P0", "Fan failure: ${item.name}", item.name
                )
                name.contains("voltage") -> {
                    val voltage = item.value.toDoubleOrNull()
                    if (voltage != null && voltage < properties.alert.lowVoltage) {
                        signals += signal(
                            targetId, "LOW_VOLTAGE", "P0",
                            "Low voltage: $voltage", item.name, details = "voltage=$voltage"
                        )
                    }
                }
            }
        }

        val expected = snapshot.expectedFirmware
        val current = snapshot.routerboard?.currentFirmware
        if (!expected.isNullOrBlank() && !current.isNullOrBlank() && current != expected) {
            signals += signal(
                targetId, "FIRMWARE_DRIFT", "P1",
                "Firmware drift: current=$current expected=$expected",
                "firmware",
                details = "current=$current;expected=$expected"
            )
        }

        val cpu = snapshot.resource?.cpuLoad
        val cpuThreshold = properties.alert.cpuThreshold
        if (cpu != null && cpu >= cpuThreshold) {
            signals += signal(
                targetId, "CPU_HIGH", "P1",
                "CPU high: $cpu%",
                "cpu",
                details = "cpuLoad=$cpu;threshold=$cpuThreshold"
            )
        }

        val previous = snapshot.previousUptimeSeconds
        val currentUptime = snapshot.resource?.uptimeSeconds
        if (previous != null && currentUptime != null && currentUptime + 60 < previous) {
            signals += signal(
                targetId, "UNEXPECTED_REBOOT", "P0",
                "Unexpected reboot detected",
                "reboot",
                details = "previousUptime=$previous;currentUptime=$currentUptime"
            )
        }

        snapshot.netwatch.forEach { probe ->
            if (probe.status == "down") {
                signals += signal(
                    targetId = targetId,
                    reasonCode = "UPSTREAM_PROBE_FAIL",
                    severity = "P0",
                    title = "Upstream probe fail: ${probe.name}",
                    component = probe.name,
                    details = "host=${probe.host};type=${probe.type};comment=${probe.comment}"
                )
            }
        }

        snapshot.optical.forEach { optic ->
            if (optic.opticalDdmAvailable == false) {
                return@forEach
            }
            val hasReading = optic.rxPowerDbm != null ||
                optic.txPowerDbm != null ||
                optic.temperatureC != null
            if (!hasReading) {
                return@forEach
            }
            val rx = optic.rxPowerDbm
            if (rx != null && rx < properties.optical.rxLowDbm) {
                signals += signal(
                    targetId = targetId,
                    reasonCode = "OPTICAL_RX_LOW",
                    severity = "P0",
                    title = "Optical RX low: ${optic.interfaceName}",
                    component = optic.interfaceName,
                    details = "rxPowerDbm=$rx;threshold=${properties.optical.rxLowDbm}"
                )
            }
            val tx = optic.txPowerDbm
            val txFault = when {
                optic.sfpPresent == true && tx == null -> true
                tx != null && tx < properties.optical.txFaultDbm -> true
                else -> false
            }
            if (txFault) {
                signals += signal(
                    targetId = targetId,
                    reasonCode = "OPTICAL_TX_FAULT",
                    severity = "P0",
                    title = "Optical TX fault: ${optic.interfaceName}",
                    component = optic.interfaceName,
                    details = "txPowerDbm=$tx;threshold=${properties.optical.txFaultDbm};sfpPresent=${optic.sfpPresent}"
                )
            }
        }

        return signals
    }

    fun fromPollFailure(targetId: Long, reasonCode: String, details: String?): List<AlertSignal> {
        val severity = when (reasonCode) {
            "DEVICE_UNREACHABLE", "AUTH_FAILURE", "TIMEOUT" -> "P0"
            else -> "P1"
        }
        return listOf(
            signal(
                targetId = targetId,
                reasonCode = reasonCode,
                severity = severity,
                title = "Poll failed: $reasonCode",
                component = "poll",
                details = details
            )
        )
    }

    fun pollStale(targetId: Long, details: String): AlertSignal {
        return signal(
            targetId = targetId,
            reasonCode = "POLL_STALE",
            severity = "P1",
            title = "Poll stale for target $targetId",
            component = "poll",
            details = details
        )
    }

    fun fromIngest(
        targetId: Long?,
        reasonCode: String,
        severity: String,
        title: String,
        component: String,
        details: String?
    ): AlertSignal {
        val keyTarget = targetId?.toString() ?: "global"
        return AlertSignal(
            reasonCode = reasonCode,
            severity = severity,
            title = title,
            dedupKey = "$reasonCode:$keyTarget:$component",
            details = details
        )
    }

    private fun signal(
        targetId: Long,
        reasonCode: String,
        severity: String,
        title: String,
        component: String,
        details: String? = null
    ): AlertSignal {
        return AlertSignal(
            reasonCode = reasonCode,
            severity = severity,
            title = title,
            dedupKey = "$reasonCode:$targetId:$component",
            details = details
        )
    }

    private fun isFailValue(value: String): Boolean {
        return value.contains("fail") || value == "false" || value == "0" || value == "critical" || value == "error"
    }
}
