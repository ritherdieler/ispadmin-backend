package com.dscorp.wispadmin.oltgateway.service

import com.dscorp.wispadmin.oltgateway.config.OltGatewayProperties
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

data class ResolvedAuthorizeProfiles(
    val lineProfileId: Int,
    val serviceProfileId: Int,
    val description: String,
)

class SmartOltAuthorizeProfileResolver(
    private val properties: OltGatewayProperties,
    private val clock: Clock = Clock.systemUTC(),
) {

    fun resolve(
        customProfile: String,
        vlan: Int,
        name: String,
        zone: String,
        sn: String,
        at: Instant = clock.instant(),
    ): ResolvedAuthorizeProfiles {
        val binding = bindings()["${customProfile.trim()}:$vlan"]
        val line = binding?.first ?: properties.writes.defaultLineProfileId
        val srv = binding?.second ?: properties.writes.defaultServiceProfileId
        val baseName = name.trim().ifBlank { sn.trim() }.ifBlank { "onu" }
        val zonePart = zone.trim().ifBlank { "Zone" }
        val day = DateTimeFormatter.ofPattern("yyyyMMdd")
            .withZone(ZoneOffset.UTC)
            .format(at)
        return ResolvedAuthorizeProfiles(
            lineProfileId = line,
            serviceProfileId = srv,
            description = "${baseName}_zone_${zonePart}_authd_$day",
        )
    }

    private fun bindings(): Map<String, Pair<Int, Int>> {
        val raw = properties.writes.customProfileBindings
        if (raw.isBlank()) return emptyMap()
        return raw.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { token ->
                val parts = token.split('=')
                if (parts.size != 2) return@mapNotNull null
                val key = parts[0].trim()
                val ids = parts[1].split(':')
                if (ids.size != 2) return@mapNotNull null
                val line = ids[0].trim().toIntOrNull() ?: return@mapNotNull null
                val srv = ids[1].trim().toIntOrNull() ?: return@mapNotNull null
                key to (line to srv)
            }
            .toMap()
    }
}
