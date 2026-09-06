package com.dscorp.wispadmin.wispadmin.service.subscription

/**
 * Single source of truth for FIBER VLAN: value sent from the mobile app
 * (`subscription.vlan`). Used by OLT authorize and GenieACS TR-069 alike.
 */
object SubscriptionVlanRules {
    val ALLOWED: Set<String> = setOf("1", "100")
    const val PROD_VLAN100_POOL_PREFIX = "192.168.30."
    const val PROD_VLAN100_POOL_PREFIX_31 = "192.168.31."
    val PROD_VLAN100_POOL_PREFIXES: Set<String> = setOf(
        PROD_VLAN100_POOL_PREFIX,
        PROD_VLAN100_POOL_PREFIX_31,
    )
    const val STAGING_VLAN100_POOL_PREFIX = "192.168.250."

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

    fun resolveMigrationVlan(rawFromApp: String?): String {
        val trimmed = rawFromApp?.trim().orEmpty()
        return requireAppVlan(if (trimmed.isEmpty()) "100" else trimmed)
    }

    fun assertPoolAligned(vlan: String, ipSegment: String?, environmentTag: String? = null) {
        if (ipSegment.isNullOrBlank()) return
        val segment = ipSegment.trim()
        val staging = environmentTag?.trim()?.lowercase() == "stg"
        when (vlan) {
            "100" -> {
                val prodPool = PROD_VLAN100_POOL_PREFIXES.any { segment.startsWith(it) }
                val stagingPool = staging && segment.startsWith(STAGING_VLAN100_POOL_PREFIX)
                require(prodPool || stagingPool) {
                    if (staging) {
                        "VLAN 100 en staging requiere pool ${PROD_VLAN100_POOL_PREFIXES.joinToString(" o ") { "${it}x" }} o " +
                            "${STAGING_VLAN100_POOL_PREFIX}x (recibido: $segment)"
                    } else {
                        "VLAN 100 requiere pool 192.168.30.0/24 o 192.168.31.0/24 (recibido: $segment)"
                    }
                }
            }
            "1" -> require(PROD_VLAN100_POOL_PREFIXES.none { segment.startsWith(it) }) {
                "VLAN 1 no debe usar pools VLAN100 prod (recibido: $segment)"
            }
        }
    }
}
