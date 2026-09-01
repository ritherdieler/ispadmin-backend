package com.dscorp.wispadmin.netdiag.port

data class NetDiagOltAlarm(
    val alarmIdHex: String?,
    val alarmName: String,
    val slotId: Int?,
    val portId: Int?,
    val ontId: Int?,
    val reasonCode: String,
    val severity: String,
    val component: String,
    val isClear: Boolean,
    val rawBlock: String,
    val unparsed: Boolean
)

interface NetDiagOltAlarmParserPort {
    fun parseActiveAlarms(raw: String): List<NetDiagOltAlarm>
}
