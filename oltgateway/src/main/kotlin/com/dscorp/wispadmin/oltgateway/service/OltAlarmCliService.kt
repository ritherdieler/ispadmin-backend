package com.dscorp.wispadmin.oltgateway.service

import com.dscorp.wispadmin.oltgateway.config.OltGatewayProperties
import com.dscorp.wispadmin.oltgateway.dto.OltAlarmPollResponseDto
import com.dscorp.wispadmin.oltgateway.dto.OltDescriptorDto
import com.dscorp.wispadmin.oltgateway.dto.OltParsedAlarmDto
import com.dscorp.wispadmin.oltgateway.parser.HuaweiOltAlarmParser
import com.dscorp.wispadmin.oltgateway.parser.ParsedOltAlarm
import com.dscorp.wispadmin.oltgateway.ssh.CliBusResult
import com.dscorp.wispadmin.oltgateway.ssh.CliJobType
import com.dscorp.wispadmin.oltgateway.ssh.OltCliBus
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty(prefix = "olt.gateway", name = ["enabled"], havingValue = "true")
class OltAlarmCliService(
    private val cliBusProvider: ObjectProvider<OltCliBus>,
    private val parser: HuaweiOltAlarmParser,
    private val properties: OltGatewayProperties
) {
    fun descriptor(): OltDescriptorDto = OltDescriptorDto(
        oltId = properties.oltId,
        host = properties.host,
        alarmPollEnabled = properties.sync.alarmEnabled,
        portsPerGponBoard = properties.inventory.defaultPortsPerGponBoard
    )

    fun pollActiveAlarms(): OltAlarmPollResponseDto {
        val bus = cliBusProvider.ifAvailable ?: return OltAlarmPollResponseDto(skippedReason = "cli_bus_unavailable")
        return when (val result = bus.execute(CliJobType.ALARM_POLL) { session ->
            session.execute("screen-length 0 temporary")
            session.execute("scroll 512")
            session.execute("display alarm active all", ALARM_COMMAND_TIMEOUT_MS)
        }) {
            is CliBusResult.Ok -> OltAlarmPollResponseDto(
                raw = result.value,
                alarms = parse(result.value)
            )
            is CliBusResult.Skipped -> OltAlarmPollResponseDto(skippedReason = result.reason)
        }
    }

    fun parse(raw: String): List<OltParsedAlarmDto> {
        return parser.parseActiveAlarms(raw).map { it.toDto() }
    }

    private fun ParsedOltAlarm.toDto(): OltParsedAlarmDto = OltParsedAlarmDto(
        alarmIdHex = alarmIdHex,
        alarmName = alarmName,
        slotId = slotId,
        portId = portId,
        ontId = ontId,
        reasonCode = reasonCode,
        severity = severity,
        component = component,
        isClear = isClear,
        rawBlock = rawBlock,
        unparsed = reasonCode == HuaweiOltAlarmParser.REASON_UNPARSED
    )

    companion object {
        const val ALARM_COMMAND_TIMEOUT_MS: Long = 300_000
    }
}
