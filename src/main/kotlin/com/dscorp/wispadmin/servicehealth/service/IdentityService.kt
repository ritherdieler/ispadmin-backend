package com.dscorp.wispadmin.servicehealth.service

import com.dscorp.wispadmin.servicehealth.domain.*
import com.dscorp.wispadmin.servicehealth.repository.*
import com.dscorp.wispadmin.servicehealth.port.AcsSubscriptionPort
import com.dscorp.wispadmin.servicehealth.port.HealthOnuPort
import com.dscorp.wispadmin.servicehealth.port.HealthTrafficPort
import com.dscorp.wispadmin.servicehealth.port.SubscriptionDirectoryPort
import com.dscorp.wispadmin.servicehealth.port.SubscriptionHealthRef
import com.dscorp.wispadmin.wispadmin.service.genieacs.Tr069SerialMatcher
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class IdentityService(
    private val subscriptions: SubscriptionDirectoryPort, private val acs: AcsSubscriptionPort,
    private val onuPort: ObjectProvider<HealthOnuPort>, private val links: IdentityLinkRepository,
    private val conflicts: IdentityConflictRepository, private val trafficPort: ObjectProvider<HealthTrafficPort>,
    private val json: ObjectMapper
) {
    /** subscription_acs is the canonical ACS registry: a contradictory device id resolves to nothing. */
    fun resolveAcs(deviceId: String): Int? {
        val ids = acs.findSubscriptionIdsByDeviceId(deviceId).distinct()
        if (ids.size != 1) return null
        val id = ids.single()
        val canonical = acs.findDeviceId(id)
        if (!canonical.isNullOrBlank() && canonical != deviceId) return null
        return id
    }

    @Transactional
    fun resolveAcsForCollection(deviceId: String): Int? {
        val resolved = resolveAcs(deviceId)
        if (resolved != null) return resolved
        val ids = acs.findSubscriptionIdsByDeviceId(deviceId).distinct()
        if (ids.isNotEmpty()) conflict("ACS", deviceId, ids)
        return null
    }

    fun resolveOnu(sn: String): Int? {
        val exact = subscriptions.findIdsByOnuSerial(sn)
        if (exact.size == 1) return exact.single()
        if (exact.size > 1) return null
        val suffix = Tr069SerialMatcher.normalizeSuffix(sn) ?: return null
        return subscriptions.findIdsByOnuSerialOrSuffix(sn, suffix).singleOrNull()
    }

    @Transactional
    fun resolveOnuForCollection(sn: String): Int? {
        val exact = subscriptions.findIdsByOnuSerial(sn)
        if (exact.size > 1) {
            conflict("ONU", sn, exact)
            return null
        }
        if (exact.size == 1) return exact.single()
        val suffix = Tr069SerialMatcher.normalizeSuffix(sn) ?: return null
        val candidates = subscriptions.findIdsByOnuSerialOrSuffix(sn, suffix)
        if (candidates.size > 1) conflict("ONU", sn, candidates)
        return candidates.singleOrNull()
    }

    @Transactional
    fun conflict(kind: String, value: String, ids: List<Int>) {
        val key = "$kind:$value"
        val existing = conflicts.findByConflictKey(key)
        val row = existing ?: IdentityConflict(conflictKey=key, kind=kind, identityValue=value)
        row.subscriptionIdsJson = json.writeValueAsString(ids.sorted())
        row.status = "OPEN"
        row.resolvedAt = null
        conflicts.save(row)
    }

    @Transactional
    fun reconcile(subscriptionId: Int, now: Instant = Instant.now()): Map<String, String> {
        val id = subscriptionId
        val verified=subscriptions.lockIdentityOwner(id) ?: return emptyMap()
        val snapshot = snapshot(verified)
        val current = links.findBySubscriptionIdAndValidToIsNull(id)
        for (link in current) {
            if (snapshot[link.kind] != link.identityValue) link.validTo = now
            else link.verifiedAt = now
        }
        links.saveAll(current)
        for ((kind,value) in snapshot) {
            if (current.none { it.kind == kind && it.identityValue == value && it.validTo == null }) {
                links.save(IdentityLink(subscriptionId=id, kind=kind, identityValue=value, validFrom=now, verifiedAt=now))
            }
        }
        return snapshot
    }

    fun currentLinks(id: Int): List<IdentityLink> = links.findBySubscriptionIdAndValidToIsNull(id)

    fun snapshot(s: SubscriptionHealthRef): Map<String,String> {
        val id = s.id
        val sn = s.onuSn?.uppercase()
        val onu = sn?.let { onuPort.ifAvailable?.findBySn(it) }
        val canonical = acs.findDeviceId(id)
        return buildMap {
            sn?.let { if (resolveOnu(it) == id) put("ONU", it) }
            canonical?.let { if (resolveAcs(it) == id) put("ACS", it) }
            s.ip?.let { put("IP", it) }
            s.hostDeviceId?.let { put("ROUTER", it.toString()) }
            s.planId?.let { put("PLAN", it.toString()) }
            s.napBoxId?.let { put("NAP", it.toString()) }
            if (onu != null && get("ONU") != null) {
                onu.oltId?.let { put("OLT", it.toString()) }
                put("PON", "${onu.oltId}:${onu.board}:${onu.port}")
                put("ONU_ID", onu.id.toString())
                onu.externalId?.let { put("ONU_EXTERNAL_ID", it) }
                onu.zoneId?.let { put("ZONE", it.toString()) }
            }
            trafficPort.ifAvailable?.latestSample(id)?.queueId?.let { put("QUEUE", it) }
        }
    }

    @Transactional
    fun resolveConflict(conflictId: Long, selectedId: Int, actorId: Int, reason: String): IdentityConflict {
        require(reason.isNotBlank() && reason.length <= 500) { "Se requiere motivo de resolución (máximo 500 caracteres)" }
        val conflict = conflicts.findById(conflictId).orElseThrow { NoSuchElementException("Conflicto inexistente") }
        require(conflict.status == "OPEN") { "El conflicto ya fue resuelto" }
        val candidates = json.readTree(conflict.subscriptionIdsJson).map { it.asInt() }
        require(selectedId in candidates) { "La suscripción debe pertenecer al conflicto" }
        // The provisioning domain remains the authority; do not repair it through a diagnostic override.
        val resolved = when(conflict.kind) { "ACS" -> resolveAcs(conflict.identityValue); "ONU" -> resolveOnu(conflict.identityValue); else -> null }
        require(resolved == selectedId) { "Corrija primero el vínculo en provisión/inventario; aún existe ambigüedad" }
        conflict.status = "RESOLVED"; conflict.resolvedAt = Instant.now(); conflict.resolvedBy = actorId; conflict.resolution = reason
        return conflicts.save(conflict)
    }
}
