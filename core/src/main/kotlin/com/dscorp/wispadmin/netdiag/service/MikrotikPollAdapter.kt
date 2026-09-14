package com.dscorp.wispadmin.netdiag.service

import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagProbeRun
import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagTarget
import com.dscorp.wispadmin.netdiag.domain.repository.NetDiagProbeRunRepository
import com.dscorp.wispadmin.netdiag.port.NetDiagDeviceDirectoryPort
import com.dscorp.wispadmin.routeros.RouterOsUptimeParser
import com.dscorp.wispadmin.routeros.port.MikrotikAuthException
import com.dscorp.wispadmin.routeros.port.MikrotikClient
import com.dscorp.wispadmin.routeros.port.MikrotikCommandException
import com.dscorp.wispadmin.routeros.port.MikrotikException
import com.dscorp.wispadmin.routeros.port.MikrotikSession
import com.dscorp.wispadmin.routeros.port.MikrotikTimeoutException
import com.dscorp.wispadmin.routeros.port.MikrotikUnreachableException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.Optional

@Service
@ConditionalOnProperty(prefix = "net.diag", name = ["enabled"], havingValue = "true")
class MikrotikPollAdapter(
    @Qualifier("netDiagMikrotikClient") private val mikrotikClient: MikrotikClient,
    private val deviceDirectory: NetDiagDeviceDirectoryPort,
    private val probeRunRepository: NetDiagProbeRunRepository,
    private val objectMapper: ObjectMapper,
    private val netwatchAdapter: MikrotikNetwatchAdapter,
    private val opticalAdapter: MikrotikOpticalAdapter
) {

    fun poll(target: NetDiagTarget): PollResult {
        val startedAt = Instant.now()
        val probe = NetDiagProbeRun(
            target = target,
            status = "PENDING",
            startedAt = startedAt
        )

        val deviceRef = deviceDirectory.findMikrotikDeviceRef(target.deviceRefId)
        if (deviceRef == null) {
            return persistFailed(probe, startedAt, "DEVICE_NOT_FOUND", "Device ref not found for target ${target.id}")
        }

        return try {
            val previousUptime = previousSuccessfulUptimeSeconds(target.id!!)
            val monitorConfig = parseMonitorConfig(target.monitorConfig)
            val snapshot = mikrotikClient.withSession(deviceRef) { session ->
                collectSnapshot(session, monitorConfig, previousUptime)
            }
            val payload = objectMapper.writeValueAsString(snapshot)
            val finishedAt = Instant.now()
            probe.status = "SUCCESS"
            probe.finishedAt = finishedAt
            probe.latencyMs = finishedAt.toEpochMilli() - startedAt.toEpochMilli()
            probe.payload = payload
            val saved = probeRunRepository.save(probe)
            PollResult(
                probeRun = saved,
                status = "SUCCESS",
                payload = payload,
                snapshot = snapshot
            )
        } catch (ex: MikrotikException) {
            persistFailed(probe, startedAt, mapException(ex), ex.message ?: ex.javaClass.simpleName)
        } catch (ex: Exception) {
            persistFailed(probe, startedAt, "COMMAND_ERROR", ex.message ?: ex.javaClass.simpleName)
        }
    }

    private fun collectSnapshot(
        session: MikrotikSession,
        monitorConfig: TargetMonitorConfig,
        previousUptimeSeconds: Long?
    ): PollSnapshot {
        val interfaces = session.print("/interface").map { row ->
            InterfaceSnapshot(
                name = row["name"].orEmpty(),
                type = row["type"].orEmpty(),
                running = row["running"].equals("true", ignoreCase = true),
                disabled = row["disabled"].equals("true", ignoreCase = true),
                rxErrors = row["rx-error"]?.toLongOrNull(), txErrors = row["tx-error"]?.toLongOrNull(),
                rxDrops = row["rx-drop"]?.toLongOrNull(), txDrops = row["tx-drop"]?.toLongOrNull()
            )
        }
        val health = session.print("/system/health").map { row ->
            HealthSnapshot(
                name = row["name"].orEmpty(),
                value = row["value"] ?: row["temperature"] ?: row["voltage"] ?: ""
            )
        }
        val routerboardRow = session.print("/system/routerboard").firstOrNull()
        val routerboard = routerboardRow?.let {
            RouterboardSnapshot(
                currentFirmware = it["current-firmware"] ?: it["currentFirmware"],
                upgradeFirmware = it["upgrade-firmware"] ?: it["upgradeFirmware"]
            )
        }
        val resourceRow = session.print("/system/resource").firstOrNull()
        val uptimeRaw = resourceRow?.get("uptime")
        val resource = resourceRow?.let {
            ResourceSnapshot(
                uptimeRaw = uptimeRaw,
                uptimeSeconds = RouterOsUptimeParser.parseSeconds(uptimeRaw),
                cpuLoad = it["cpu-load"]?.toIntOrNull() ?: it["cpuLoad"]?.toIntOrNull(),
                version = it["version"],
                freeMemoryBytes = it["free-memory"]?.toLongOrNull(), totalMemoryBytes = it["total-memory"]?.toLongOrNull()
            )
        }
        val netwatch = netwatchAdapter.collect(session, monitorConfig)
        val optical = opticalAdapter.collect(session, monitorConfig)
        return PollSnapshot(
            interfaces = interfaces,
            health = health,
            routerboard = routerboard,
            resource = resource,
            criticalInterfaces = monitorConfig.criticalInterfaces,
            expectedFirmware = monitorConfig.expectedFirmware,
            previousUptimeSeconds = previousUptimeSeconds,
            netwatch = netwatch,
            optical = optical
        )
    }

    private fun previousSuccessfulUptimeSeconds(targetId: Long): Long? {
        val previous: Optional<NetDiagProbeRun> =
            probeRunRepository.findTopByTargetIdAndStatusOrderByStartedAtDesc(targetId, "SUCCESS")
        val payload = previous.map { it.payload }.orElse(null) ?: return null
        return runCatching {
            val root = objectMapper.readTree(payload)
            root.path("resource").path("uptimeSeconds").takeIf { !it.isMissingNode && !it.isNull }?.asLong()
        }.getOrNull()
    }

    private fun parseMonitorConfig(raw: String?): TargetMonitorConfig {
        if (raw.isNullOrBlank()) return TargetMonitorConfig()
        return runCatching {
            val root: JsonNode = objectMapper.readTree(raw)
            TargetMonitorConfig(
                criticalInterfaces = stringList(root, "criticalInterfaces"),
                expectedFirmware = root.path("expectedFirmware").asText(null),
                cpuThreshold = root.path("cpuThreshold").takeIf { it.isNumber }?.asInt(),
                netwatchNames = stringList(root, "netwatchNames"),
                opticalInterfaces = stringList(root, "opticalInterfaces")
            )
        }.getOrDefault(TargetMonitorConfig())
    }

    private fun stringList(root: JsonNode, field: String): List<String> {
        return root.path(field)
            .takeIf { it.isArray }
            ?.mapNotNull { it.asText(null) }
            ?.filter { it.isNotBlank() }
            ?: emptyList()
    }

    private fun persistFailed(
        probe: NetDiagProbeRun,
        startedAt: Instant,
        reasonCode: String,
        error: String
    ): PollResult {
        val finishedAt = Instant.now()
        probe.status = "FAILED"
        probe.finishedAt = finishedAt
        probe.latencyMs = finishedAt.toEpochMilli() - startedAt.toEpochMilli()
        probe.error = error
        probe.payload = """{"errorReasonCode":"$reasonCode"}"""
        val saved = probeRunRepository.save(probe)
        return PollResult(
            probeRun = saved,
            status = "FAILED",
            payload = saved.payload,
            errorReasonCode = reasonCode
        )
    }

    private fun mapException(ex: MikrotikException): String {
        return when (ex) {
            is MikrotikUnreachableException -> "DEVICE_UNREACHABLE"
            is MikrotikAuthException -> "AUTH_FAILURE"
            is MikrotikTimeoutException -> "TIMEOUT"
            is MikrotikCommandException -> "COMMAND_ERROR"
            else -> "COMMAND_ERROR"
        }
    }
}
