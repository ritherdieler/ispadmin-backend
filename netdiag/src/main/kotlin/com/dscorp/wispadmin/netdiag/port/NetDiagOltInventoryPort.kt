package com.dscorp.wispadmin.netdiag.port

data class NetDiagPonOnu(
    val onuIndex: Int,
    val sn: String,
    val runState: String?,
    val lastDownCause: String?,
    val onuRxDbm: Double?
)

interface NetDiagOltInventoryPort {
    fun findOltId(name: String): Long?
    fun listOnusOnPon(oltName: String, board: Int, port: Int): List<NetDiagPonOnu>
    fun findOnu(oltName: String, board: Int, port: Int, onuIndex: Int): NetDiagPonOnu?
}
