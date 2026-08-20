package com.dscorp.wispadmin.wispadmin.service.subscription

/**
 * Single source of truth for FIBER VLAN: value sent from the mobile app
 * (`subscription.vlan`). Used by OLT authorize and GenieACS TR-069 alike.
 */
object SubscriptionVlanRules {
    val ALLOWED: Set<String> = setOf("1", "100")

    fun requireAppVlan(raw: String?): String {
        val vlan = raw?.trim().orEmpty()
        require(vlan.isNotEmpty()) {
            "vlan es obligatoria (enviada desde la app)"
        }
        require(vlan in ALLOWED) {
            "vlan inválida '$vlan'; valores permitidos: ${ALLOWED.joinToString(", ")}"
        }
        return vlan
    }

    fun assertPoolAligned(vlan: String, ipSegment: String?) {
        if (ipSegment.isNullOrBlank()) return
        val segment = ipSegment.trim()
        when (vlan) {
            "100" -> require(segment.startsWith("192.168.30.")) {
                "VLAN 100 requiere pool 192.168.30.0/24 (recibido: $segment)"
            }
            "1" -> require(!segment.startsWith("192.168.30.")) {
                "VLAN 1 no debe usar pool 192.168.30.0/24 (recibido: $segment)"
            }
        }
    }
}
