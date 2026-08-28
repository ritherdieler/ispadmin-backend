package com.dscorp.wispadmin.oltgateway.snmp

import com.dscorp.wispadmin.oltgateway.parser.ParsedAutofindOnt
import com.dscorp.wispadmin.oltgateway.parser.ParsedOnuSummary

data class SnmpOntKey(
    val ifIndex: Long,
    val ontId: Int
)

data class SnmpOntOptical(
    val key: SnmpOntKey,
    val onuRxDbm: Double?,
    val onuTxDbm: Double?,
    val oltRxDbm: Double?,
    val temperatureC: Double? = null,
    val biasCurrentMa: Double? = null,
    val distanceM: Int? = null,
    val matchState: String? = null
)

interface OltSnmpClient {
    fun probeSysObjectId(): String?

    /** Configured ONTs with SN + run state (online/offline). */
    fun listConfiguredOnus(): List<ParsedOnuSummary>

    fun listAutofind(): List<ParsedAutofindOnt>

    /** Full-table walk when [ports] is null; per-port subtree walks when set (faster with parallel ports). */
    fun listOptical(ports: Collection<GponFsp>? = null): List<SnmpOntOptical>
}
