package com.dscorp.wispadmin.oltgateway.service

import com.dscorp.wispadmin.events.EventBusPort
import com.dscorp.wispadmin.events.NoOpEventBus
import com.dscorp.wispadmin.events.PlatformEvent
import com.dscorp.wispadmin.events.PlatformEventTypes
import com.dscorp.wispadmin.oltgateway.dto.LabOpticalRefreshResponseDto
import com.dscorp.wispadmin.oltgateway.parser.OpticalInfoParser
import com.dscorp.wispadmin.oltgateway.parser.ParsedOpticalInfo
import com.dscorp.wispadmin.oltgateway.ssh.CliBusResult
import com.dscorp.wispadmin.oltgateway.ssh.CliJobType
import com.dscorp.wispadmin.oltgateway.ssh.OltCliBus
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.time.Instant

data class LabOpticalPollResult(
    val collected: Int = 0,
    val unmapped: Int = 0,
    val error: String? = null,
)

@Service
@ConditionalOnProperty(prefix = "olt.gateway", name = ["enabled"], havingValue = "true")
class LabOpticalSshPollService(
    private val onuQuery: OltHealthOnuQueryService,
    private val cliBusProvider: ObjectProvider<OltCliBus>,
    private val parser: OpticalInfoParser,
    private val publisher: ApplicationEventPublisher,
    private val eventBus: EventBusPort = NoOpEventBus(),
) {
    private val logger = LoggerFactory.getLogger(LabOpticalSshPollService::class.java)

    fun pollAllLab(): LabOpticalPollResult {
        return LabOpticalPollResult(error = "no_directory")
    }

    fun refreshBySn(sn: String): LabOpticalRefreshResponseDto {
        if (sn.isBlank()) return LabOpticalRefreshResponseDto(false, error = "missing_sn")
        val onu = onuQuery.findBySn(sn) ?: return LabOpticalRefreshResponseDto(false, unmapped = true)
        val ref = LabOnuRef(
            id = onu.id,
            sn = onu.sn,
            oltId = onu.oltId,
            board = onu.board,
            port = onu.port,
            onuIndex = onu.onuIndex,
        )
        val bus = cliBusProvider.ifAvailable ?: return LabOpticalRefreshResponseDto(false, error = "cli_bus_unavailable")
        val parsed = readOptical(bus, ref) ?: return LabOpticalRefreshResponseDto(false, error = "ssh_failed")
        val oltId = ref.oltId ?: return LabOpticalRefreshResponseDto(false, unmapped = true)
        val rows = listOf(OltSignalPollService.OpticalRow(ref.board, ref.port, parsed))
        publisher.publishEvent(OltOpticalObservation(oltId, Instant.now(), rows))
        eventBus.publish(
            PlatformEvent(
                type = PlatformEventTypes.ONU_OPTICAL,
                sn = onu.sn,
                occurredAt = Instant.now(),
                payloadJson = """{"rxPowerDbm":${parsed.rxPowerDbm},"runState":"online"}""",
            )
        )
        return LabOpticalRefreshResponseDto(collected = true)
    }

    private fun readOptical(cliBus: OltCliBus, onu: LabOnuRef): ParsedOpticalInfo? {
        return try {
            when (val result = cliBus.execute(CliJobType.ADHOC) { session ->
                session.execute("interface gpon 0/${onu.board}")
                val output = session.execute("display ont optical-info ${onu.port} ${onu.onuIndex}")
                session.execute("quit")
                parser.parse(output, onu.onuIndex)
            }) {
                is CliBusResult.Ok -> result.value
                is CliBusResult.Skipped -> {
                    logger.info("Lab optical SSH skipped onu={} reason={}", onu.sn, result.reason)
                    null
                }
            }
        } catch (ex: Exception) {
            logger.warn("Lab optical SSH failed onu={}: {}", onu.sn, ex.message)
            null
        }
    }
}
