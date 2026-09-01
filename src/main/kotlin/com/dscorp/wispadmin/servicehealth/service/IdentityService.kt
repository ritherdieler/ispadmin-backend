package com.dscorp.wispadmin.servicehealth.service

import com.dscorp.wispadmin.servicehealth.domain.*
import com.dscorp.wispadmin.servicehealth.repository.*
import com.dscorp.wispadmin.wispadmin.repository.*
import com.dscorp.wispadmin.servicehealth.port.HealthOnuPort
import com.dscorp.wispadmin.servicehealth.port.HealthTrafficPort
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.service.genieacs.Tr069SerialMatcher
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class IdentityService(
    private val subscriptions: SubscriptionRepository, private val acs: SubscriptionAcsRepository,
    private val onuPort: ObjectProvider<HealthOnuPort>, private val links: IdentityLinkRepository,
    private val conflicts: IdentityConflictRepository, private val trafficPort: ObjectProvider<HealthTrafficPort>,
    private val json: ObjectMapper
) {
    /** Read-only resolver: no fallback is permitted when either source is contradictory. */
    fun resolveAcs(deviceId: String): Int? {
        val canonical = acs.findByGenieacsDeviceId(deviceId)
        val operational = subscriptions.findByTr069DeviceId(deviceId)
        val ids = (canonical.map { it.subscriptionId } + operational.mapNotNull { it.id }).distinct()
        if (ids.size != 1) return null
        val id = ids.single()
        val otherCanonical = acs.findById(id).orElse(null)?.genieacsDeviceId
        val otherOperational = subscriptions.findById(id).orElse(null)?.tr069DeviceId
        if (!otherCanonical.isNullOrBlank() && otherCanonical != deviceId) return null
        if (!otherOperational.isNullOrBlank() && otherOperational != deviceId) return null
        return id
    }

    @Transactional
    fun resolveAcsForCollection(deviceId: String): Int? {
        val resolved = resolveAcs(deviceId)
        if (resolved != null) return resolved
        val ids = (acs.findByGenieacsDeviceId(deviceId).map { it.subscriptionId } +
            subscriptions.findByTr069DeviceId(deviceId).mapNotNull { it.id }).distinct()
        if (ids.isNotEmpty()) conflict("ACS", deviceId, ids)
        return null
    }

    fun resolveOnu(sn: String): Int? {
        val exact = subscriptions.findByExactOnuSerial(sn)
        if (exact.size == 1) return exact.single().id
        if (exact.size > 1) return null
        val suffix = Tr069SerialMatcher.normalizeSuffix(sn) ?: return null
        return subscriptions.findByOnuSerialOrSuffix(sn, suffix).singleOrNull()?.id
    }

    @Transactional
    fun resolveOnuForCollection(sn: String): Int? {
        val exact = subscriptions.findByExactOnuSerial(sn)
        if (exact.size > 1) {
            conflict("ONU", sn, exact.mapNotNull { it.id })
            return null
        }
        if (exact.size == 1) return exact.single().id
        val suffix = Tr069SerialMatcher.normalizeSuffix(sn) ?: return null
        val candidates = subscriptions.findByOnuSerialOrSuffix(sn, suffix)
        if (candidates.size > 1) conflict("ONU", sn, candidates.mapNotNull { it.id })
        return candidates.singleOrNull()?.id
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
    fun reconcile(subscription: Subscription, now: Instant = Instant.now()): Map<String, String> {
        val id = subscription.id ?: return emptyMap()
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

    fun snapshot(s: Subscription): Map<String,String> {
        val id = s.id ?: return emptyMap()
        val sn = s.fiberOnu?.sn?.uppercase()
        val onu = sn?.let { onuPort.ifAvailable?.findBySn(it) }
        val canonical = acs.findById(id).orElse(null)?.genieacsDeviceId ?: s.tr069DeviceId
        return buildMap {
            sn?.let { if (resolveOnu(it) == id) put("ONU", it) }
            canonical?.let { if (resolveAcs(it) == id) put("ACS", it) }
            s.ip?.let { put("IP", it) }
            s.hostDevice?.id?.let { put("ROUTER", it.toString()) }
            s.plan?.id?.let { put("PLAN", it.toString()) }
            s.napBox?.id?.let { put("NAP", it.toString()) }
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
