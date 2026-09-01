package com.dscorp.wispadmin.oltgateway.adapter

import com.dscorp.wispadmin.netdiag.port.NetDiagOltAlarm
import com.dscorp.wispadmin.netdiag.port.NetDiagOltAlarmParserPort
import com.dscorp.wispadmin.oltgateway.parser.HuaweiOltAlarmParser
import com.dscorp.wispadmin.oltgateway.parser.ParsedOltAlarm
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(prefix = "olt.gateway", name = ["enabled"], havingValue = "true")
class NetDiagOltAlarmParserAdapter(
    private val parser: HuaweiOltAlarmParser
) : NetDiagOltAlarmParserPort {

    override fun parseActiveAlarms(raw: String): List<NetDiagOltAlarm> {
        return parser.parseActiveAlarms(raw).map { it.toNetDiag() }
    }

    private fun ParsedOltAlarm.toNetDiag(): NetDiagOltAlarm = NetDiagOltAlarm(
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
}
