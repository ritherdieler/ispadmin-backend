package com.dscorp.wispadmin.servicehealth.repository

import com.dscorp.wispadmin.servicehealth.domain.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import java.time.Instant
import javax.persistence.LockModeType

interface OpticalSampleRepository : JpaRepository<OpticalSample, Long> {
    fun findTopBySubscriptionIdOrderByObservedAtDesc(id: Int): OpticalSample?
    fun findBySubscriptionIdAndObservedAtBetweenOrderByObservedAtAsc(id: Int, from: Instant, to: Instant): List<OpticalSample>
    fun findByOnuIdAndObservedAtBetweenOrderByObservedAtAsc(id: Long, from: Instant, to: Instant): List<OpticalSample>
}
interface OnuStateEventRepository : JpaRepository<OnuStateEvent, Long> {
    fun existsBySourceAndSourceEventId(source: String, id: Long): Boolean
    fun findTopByOnuIdOrderByObservedAtDesc(id: Long): OnuStateEvent?
    fun findBySubscriptionIdAndObservedAtBetweenOrderByObservedAtAsc(id: Int, from: Instant, to: Instant): List<OnuStateEvent>
}
interface WifiCountSampleRepository : JpaRepository<WifiCountSample, Long> {
    fun findByDeviceIdAndSubscriptionIdAndInformAt(deviceId: String, subscriptionId: Int, informAt: Instant): WifiCountSample?
    fun findBySubscriptionIdAndObservedAtBetweenOrderByObservedAtAsc(id: Int, from: Instant, to: Instant): List<WifiCountSample>
}
interface WifiStationSampleRepository : JpaRepository<WifiStationSample, Long> {
    fun findByCountSampleId(id: Long): List<WifiStationSample>
    fun findBySubscriptionIdAndObservedAtBetweenOrderByObservedAtAsc(id: Int, from: Instant, to: Instant): List<WifiStationSample>
}
interface WifiCurrentRepository : JpaRepository<WifiCurrent, Int>
interface ReadCapabilityProfileRepository : JpaRepository<ReadCapabilityProfile, Long> {
    fun findByManufacturerAndModelAndFirmware(manufacturer: String, model: String, firmware: String): ReadCapabilityProfile?
}
interface TelemetryRunRepository : JpaRepository<TelemetryRun, Long> {
    fun findTopBySourceAndEquipmentKeyOrderByStartedAtDesc(source: String, key: String): TelemetryRun?
}
interface IdentityLinkRepository : JpaRepository<IdentityLink, Long> {
    fun findBySubscriptionIdAndValidToIsNull(id: Int): List<IdentityLink>
    fun findByKindAndIdentityValueAndValidToIsNull(kind: String, value: String): List<IdentityLink>
}
interface IdentityConflictRepository : JpaRepository<IdentityConflict, Long> {
    fun findByConflictKey(key: String): IdentityConflict?
    fun findByStatusOrderByCreatedAtDesc(status: String, page: Pageable): Page<IdentityConflict>
}
interface HealthEventRepository : JpaRepository<HealthEvent, Long> {
    fun findBySubscriptionIdAndEventStatus(id: Int, status: String): List<HealthEvent>
    fun findByEventStatus(status: String): List<HealthEvent>
    fun findBySubscriptionIdAndObservedAtBetweenOrderByObservedAtDesc(id: Int, from: Instant, to: Instant, page: Pageable): Page<HealthEvent>
}
interface HealthCurrentRepository : JpaRepository<HealthCurrent, Int>
interface EvidenceLinkRepository : JpaRepository<EvidenceLink, Long> {
    fun findByHealthEventIdAndSourceAndReferenceId(eventId: Long, source: String, ref: String): EvidenceLink?
}
interface HealthCursorRepository : JpaRepository<HealthCursor, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from HealthCursor c where c.cursorKey = :key")
    fun lock(@Param("key") key: String): HealthCursor?
}
interface TrafficEvidenceRepository : JpaRepository<TrafficEvidence, Long> {
    fun findBySubscriptionIdAndEventStatus(id: Int, status: String): List<TrafficEvidence>
}
interface IncidentSubscriptionRepository : JpaRepository<IncidentSubscription, Long> {
    fun findByIncidentIdAndSubscriptionId(incidentId: Long, subscriptionId: Int): IncidentSubscription?
    fun findByIncidentId(incidentId: Long, page: Pageable): Page<IncidentSubscription>
    fun findByState(state: String): List<IncidentSubscription>
    fun findBySubscriptionIdAndState(id: Int, state: String): List<IncidentSubscription>
}
interface RemoteActionRepository : JpaRepository<RemoteAction, Long> {
    fun findByActorIdAndRequestKey(actorId: Int, key: String): RemoteAction?
    fun findTopByDeviceKeyAndActionInOrderByCreatedAtDesc(key: String, actions: Collection<String>): RemoteAction?
    fun countByActionAndStatus(action: String, status: String): Long
    fun findByStatus(status: String): List<RemoteAction>
    fun findBySubscriptionIdAndCreatedAtBetweenOrderByCreatedAtDesc(id: Int, from: Instant, to: Instant): List<RemoteAction>
}
