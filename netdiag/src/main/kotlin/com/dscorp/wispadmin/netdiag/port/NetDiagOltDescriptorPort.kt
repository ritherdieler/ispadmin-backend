package com.dscorp.wispadmin.netdiag.port

data class NetDiagOltDescriptor(
    val oltId: String,
    val host: String,
    val alarmPollEnabled: Boolean,
    val portsPerGponBoard: Int
)

interface NetDiagOltDescriptorPort {
    fun descriptor(): NetDiagOltDescriptor
}
