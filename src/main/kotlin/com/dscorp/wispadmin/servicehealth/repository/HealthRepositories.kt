package com.dscorp.wispadmin.servicehealth.repository

import com.dscorp.wispadmin.servicehealth.domain.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import java.time.Instant
import javax.persistence.LockModeType

interface OpticalSampleRepository : JpaRepository<OpticalSample, Long> {
    fun findTopBySubscriptionIdOrderByObservedAtDesc(id: Int): OpticalSample?

    @Query(
        value = """
        select * from olt_mgr_onu_optical_sample
        where subscription_id = :id
          and observed_at >= :fromText
          and observed_at <= :toText
        order by observed_at asc
        """,
        nativeQuery = true
    )
    fun findOpticalBySubscriptionUtcRange(
        @Param("id") id: Int,
        @Param("fromText") fromText: String,
        @Param("toText") toText: String
    ): List<OpticalSample>

    @Query(
        value = """
        select * from olt_mgr_onu_optical_sample
        where onu_id = :id
          and observed_at >= :fromText
          and observed_at <= :toText
        order by observed_at asc
        """,
        nativeQuery = true
    )
    fun findOpticalByOnuUtcRange(
        @Param("id") id: Long,
        @Param("fromText") fromText: String,
        @Param("toText") toText: String
    ): List<OpticalSample>


}

interface OnuStateEventRepository : JpaRepository<OnuStateEvent, Long> {
    fun existsBySourceAndSourceEventId(source: String, id: Long): Boolean
    fun findTopByOnuIdOrderByObservedAtDesc(id: Long): OnuStateEvent?

    @Query(
        value = """
        select * from service_onu_state_event
        where subscription_id = :id
          and observed_at >= :fromText
          and observed_at <= :toText
        order by observed_at asc
        """,
        nativeQuery = true
    )
    fun findStateBySubscriptionUtcRange(
        @Param("id") id: Int,
        @Param("fromText") fromText: String,
        @Param("toText") toText: String
    ): List<OnuStateEvent>

}

interface WifiCountSampleRepository : JpaRepository<WifiCountSample, Long> {
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        INSERT INTO acs_wifi_count_sample
          (device_id, subscription_id, inform_at, observed_at, collected_at,
           associated_device_count, associated2g, associated5g, lan_device_count,
           quality_status, source_run_id, error_reason)
        VALUES (:deviceId, :subscriptionId, :informAt, :observedAt, :collectedAt,
                :associatedDeviceCount, :associated2g, :associated5g, :lanDeviceCount,
                :qualityStatus, :sourceRunId, :errorReason)
        ON DUPLICATE KEY UPDATE
          inform_at = VALUES(inform_at),
          collected_at = VALUES(collected_at),
          associated_device_count = VALUES(associated_device_count),
          associated2g = VALUES(associated2g),
          associated5g = VALUES(associated5g),
          lan_device_count = VALUES(lan_device_count),
          quality_status = VALUES(quality_status),
          source_run_id = VALUES(source_run_id),
          error_reason = VALUES(error_reason)
        """, nativeQuery = true)
    fun upsertAtomic(
        @Param("deviceId") deviceId: String,
        @Param("subscriptionId") subscriptionId: Int,
        @Param("informAt") informAt: String,
        @Param("observedAt") observedAt: String?,
        @Param("collectedAt") collectedAt: String,
        @Param("associatedDeviceCount") associatedDeviceCount: Int?,
        @Param("associated2g") associated2g: Int?,
        @Param("associated5g") associated5g: Int?,
        @Param("lanDeviceCount") lanDeviceCount: Int?,
        @Param("qualityStatus") qualityStatus: String,
        @Param("sourceRunId") sourceRunId: Long?,
        @Param("errorReason") errorReason: String?
    ): Int

    @Query(value = "select id from acs_wifi_count_sample where device_id = :deviceId and subscription_id = :subscriptionId and observed_at = :observedAt limit 1", nativeQuery = true)
    fun findIdByDeviceIdAndSubscriptionIdAndObservedAtSql(@Param("deviceId") deviceId: String, @Param("subscriptionId") subscriptionId: Int, @Param("observedAt") observedAt: String): Long?

    fun findByDeviceIdAndSubscriptionIdAndInformAt(deviceId: String, subscriptionId: Int, informAt: Instant): WifiCountSample?
    fun findByDeviceIdAndSubscriptionIdAndObservedAt(deviceId: String, subscriptionId: Int, observedAt: Instant): WifiCountSample?
    fun findTopByDeviceIdAndSubscriptionIdOrderByInformAtDesc(deviceId: String, subscriptionId: Int): WifiCountSample?
    @Query(value = "select id from acs_wifi_count_sample where device_id = :deviceId and subscription_id = :subscriptionId and date_format(inform_at, '%Y-%m-%d %H:%i:%s') = :informText limit 1", nativeQuery = true)
    fun findIdByDeviceIdAndSubscriptionIdAndInformText(@Param("deviceId") deviceId: String, @Param("subscriptionId") subscriptionId: Int, @Param("informText") informText: String): Long?

    @Query(
        value = """
        select * from acs_wifi_count_sample
        where subscription_id = :id
          and observed_at >= :fromText
          and observed_at <= :toText
        order by observed_at asc
        """,
        nativeQuery = true
    )
    fun findWifiCountBySubscriptionUtcRange(
        @Param("id") id: Int,
        @Param("fromText") fromText: String,
        @Param("toText") toText: String
    ): List<WifiCountSample>

}

interface WifiStationSampleRepository : JpaRepository<WifiStationSample, Long> {
    fun findByCountSampleId(id: Long): List<WifiStationSample>

    @Query(
        value = """
        select * from acs_wifi_station_sample
        where subscription_id = :id
          and observed_at >= :fromText
          and observed_at <= :toText
        order by observed_at asc
        """,
        nativeQuery = true
    )
    fun findWifiStationBySubscriptionUtcRange(
        @Param("id") id: Int,
        @Param("fromText") fromText: String,
        @Param("toText") toText: String
    ): List<WifiStationSample>

    fun findTopByOrderByObservedAtAsc(): WifiStationSample?

    @Query("select s from WifiStationSample s where s.observedAt >= :from and s.observedAt < :to")
    fun findByObservedAtRange(@Param("from") from: Instant, @Param("to") to: Instant): List<WifiStationSample>
}

interface WifiStationHourlyRepository : JpaRepository<WifiStationHourly, Long> {
    @Modifying(clearAutomatically = true)
    @Query("delete from WifiStationHourly h where h.bucketStart >= :from and h.bucketStart < :to")
    fun deleteByBucketStartRange(@Param("from") from: Instant, @Param("to") to: Instant): Int

    @Query(
        value = """
        select * from acs_wifi_station_hourly
        where subscription_id = :id
          and bucket_start >= :fromText
          and bucket_start <= :toText
        order by bucket_start asc
        """,
        nativeQuery = true
    )
    fun findHourlyBySubscriptionUtcRange(
        @Param("id") id: Int,
        @Param("fromText") fromText: String,
        @Param("toText") toText: String
    ): List<WifiStationHourly>
}

interface WifiAggregationWatermarkRepository : JpaRepository<WifiAggregationWatermark, String>

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

    @Query(
        value = """
        select * from service_health_event
        where subscription_id = :id
          and observed_at >= :fromText
          and observed_at <= :toText
        order by observed_at desc
        """,
        countQuery = """
        select count(*) from service_health_event
        where subscription_id = :id
          and observed_at >= :fromText
          and observed_at <= :toText
        """,
        nativeQuery = true
    )
    fun findHealthEventBySubscriptionUtcRange(
        @Param("id") id: Int,
        @Param("fromText") fromText: String,
        @Param("toText") toText: String,
        page: Pageable
    ): Page<HealthEvent>

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

    @Query(
        value = """
        select * from service_remote_action
        where subscription_id = :id
          and created_at >= :fromText
          and created_at <= :toText
        order by created_at desc
        """,
        nativeQuery = true
    )
    fun findRemoteActionBySubscriptionUtcRange(
        @Param("id") id: Int,
        @Param("fromText") fromText: String,
        @Param("toText") toText: String
    ): List<RemoteAction>

}
