package com.dscorp.wispadmin.oltgateway.snmp

import org.snmp4j.PDU
import org.snmp4j.mp.SnmpConstants
import org.snmp4j.smi.OID
import org.snmp4j.smi.OctetString
import org.snmp4j.smi.VariableBinding
import java.time.Instant

data class OltSnmpTrapVarbind(
    val oid: String,
    val value: String
)

data class OltSnmpTrapEvent(
    val receivedAt: Instant,
    val sourceHost: String?,
    val community: String?,
    val trapOid: String?,
    val trapLabel: String?,
    val varbinds: List<OltSnmpTrapVarbind>
)

/**
 * Decodes SNMPv2c notification/trap PDUs into a stable DTO for logging and later NetDiag mapping.
 * Huawei private trap OIDs are kept raw until we capture live samples.
 */
object OltSnmpTrapDecoder {

    private val knownLabels = mapOf(
        "1.3.6.1.6.3.1.1.5.1" to "coldStart",
        "1.3.6.1.6.3.1.1.5.2" to "warmStart",
        "1.3.6.1.6.3.1.1.5.3" to "linkDown",
        "1.3.6.1.6.3.1.1.5.4" to "linkUp",
        "1.3.6.1.6.3.1.1.5.5" to "authenticationFailure"
    )

    fun decode(pdu: PDU?, sourceHost: String?, community: String?): OltSnmpTrapEvent {
        val bindings = pdu?.variableBindings.orEmpty()
        val trapOid = bindings.firstOrNull { it.oid == SnmpConstants.snmpTrapOID }
            ?.variable
            ?.toString()
        val varbinds = bindings.map { vb ->
            OltSnmpTrapVarbind(
                oid = vb.oid?.toString() ?: "",
                value = formatValue(vb)
            )
        }
        return OltSnmpTrapEvent(
            receivedAt = Instant.now(),
            sourceHost = sourceHost,
            community = community?.ifBlank { null },
            trapOid = trapOid,
            trapLabel = trapOid?.let { knownLabels[it] },
            varbinds = varbinds
        )
    }

    private fun formatValue(vb: VariableBinding): String {
        val variable = vb.variable ?: return ""
        return when (variable) {
            is OctetString -> {
                val ascii = variable.toString()
                if (ascii.all { it.code in 32..126 }) ascii else variable.toHexString()
            }
            is OID -> variable.toString()
            else -> variable.toString()
        }
    }
}
