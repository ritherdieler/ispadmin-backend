package com.dscorp.wispadmin.oltgateway.port

import java.math.BigDecimal
import java.time.Instant

data class OltOnuSnapshot(
    val id: Long,
    val sn: String,
    val externalId: String?,
    val oltId: Long,
    val oltName: String?,
    val board: Int,
    val port: Int,
    val onuIndex: Int,
    val onuTypeName: String? = null,
    val zoneId: Long? = null,
    val runState: String? = null,
    val lastDownCause: String? = null,
    val onuRxDbm: BigDecimal? = null,
    val oltRxDbm: BigDecimal? = null,
    val onuTxDbm: BigDecimal? = null,
    val temperatureC: Int? = null,
    val distanceM: Int? = null,
    val polledAt: Instant? = null
)

data class OltAutofindSnapshot(
    val sn: String,
    val frame: Int,
    val board: Int,
    val port: Int,
    val ponType: String,
    val vendorId: String? = null,
    val equipmentId: String? = null,
    val lastSeenAt: Instant? = null
)

interface OltInventoryPort {
    fun findBySn(sn: String): OltOnuSnapshot?
    fun findByExternalId(externalId: String): OltOnuSnapshot?
    fun findBySlot(oltId: Long, board: Int, port: Int, onuIndex: Int): OltOnuSnapshot?
    fun listConfigured(oltId: Long): List<OltOnuSnapshot>
    fun listAutofind(oltId: Long? = null): List<OltAutofindSnapshot>
    fun countConfigured(): Long
    fun findOltIdByName(name: String): Long?
}
