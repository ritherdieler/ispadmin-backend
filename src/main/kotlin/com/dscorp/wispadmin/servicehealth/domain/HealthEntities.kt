package com.dscorp.wispadmin.servicehealth.domain

import java.time.Instant
import javax.persistence.*

enum class Quality { FRESH, STALE, MISSING, UNSUPPORTED, ERROR, INVALID }
enum class Confidence { LOW, MEDIUM, HIGH }

@Entity
@Table(name = "olt_mgr_onu_optical_sample", indexes = [Index(name="idx_sh_optical_sub_time", columnList="subscription_id,observed_at"), Index(name="idx_sh_optical_onu_time", columnList="onu_id,observed_at")])
class OpticalSample(
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="id") var id: Long? = null,
    @Column(name="subscription_id") var subscriptionId: Int? = null, @Column(name="onu_id") var onuId: Long = 0, @Column(name="onu_sn") var onuSn: String = "", @Column(name="olt_id") var oltId: Long = 0,
    @Column(name="board") var board: Int = 0, @Column(name="port") var port: Int = 0, @Column(name="onu_index") var onuIndex: Int = 0,
    @Column(name="observed_at") @org.hibernate.annotations.Type(type="com.dscorp.wispadmin.servicehealth.domain.UtcInstantType") var observedAt: Instant = Instant.now(), @Column(name="collected_at") @org.hibernate.annotations.Type(type="com.dscorp.wispadmin.servicehealth.domain.UtcInstantType") var collectedAt: Instant = Instant.now(),
    @Column(name="onu_rx_dbm") var onuRxDbm: Double? = null, @Column(name="onu_tx_dbm") var onuTxDbm: Double? = null, @Column(name="olt_rx_dbm") var oltRxDbm: Double? = null,
    @Column(name="temperature_c") var temperatureC: Double? = null, @Column(name="distance_m") var distanceM: Int? = null, @Column(name="bias_ma") var biasMa: Double? = null, @Column(name="voltage_v") var voltageV: Double? = null,
    @Enumerated(EnumType.STRING) @Column(name="quality_status") var qualityStatus: Quality = Quality.FRESH,
    @Column(name="source_run_id") var sourceRunId: Long? = null, @Column(name="error_reason") var errorReason: String? = null
)

@Entity
@Table(name="service_onu_state_event", uniqueConstraints=[UniqueConstraint(name="uk_sh_state_source",columnNames=["source","source_event_id"])], indexes=[Index(name="idx_sh_state_sub_time", columnList="subscription_id,observed_at")])
class OnuStateEvent(
    @Column(name="source_event_id") var sourceEventId: Long? = null,
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="id") var id: Long? = null,
    @Column(name="subscription_id") var subscriptionId: Int? = null, @Column(name="onu_id") var onuId: Long = 0, @Column(name="onu_sn") var onuSn: String = "",
    @Column(name="olt_id") var oltId: Long = 0, @Column(name="board") var board: Int = 0, @Column(name="port") var port: Int = 0,
    @Column(name="previous_state") var previousState: String? = null, @Column(name="state") var state: String = "UNKNOWN", @Column(name="cause") var cause: String? = null,
    @Column(name="observed_at") @org.hibernate.annotations.Type(type="com.dscorp.wispadmin.servicehealth.domain.UtcInstantType") var observedAt: Instant = Instant.now(), @Column(name="collected_at") @org.hibernate.annotations.Type(type="com.dscorp.wispadmin.servicehealth.domain.UtcInstantType") var collectedAt: Instant = Instant.now(), @Column(name="source") var source: String = "OLT_INVENTORY"
)

@Entity
@Table(name="acs_wifi_count_sample", uniqueConstraints=[UniqueConstraint(name="uk_sh_wifi_reading", columnNames=["device_id","subscription_id","inform_at"])], indexes=[Index(name="idx_sh_wifi_sub_time", columnList="subscription_id,observed_at")])
class WifiCountSample(
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="id") var id: Long? = null,
    @Column(name="subscription_id") var subscriptionId: Int = 0, @Column(name="device_id", length=128) var deviceId: String = "", @Column(name="inform_at") @org.hibernate.annotations.Type(type="com.dscorp.wispadmin.servicehealth.domain.UtcInstantType") var informAt: Instant = Instant.now(),
    @Column(name="observed_at") @org.hibernate.annotations.Type(type="com.dscorp.wispadmin.servicehealth.domain.UtcInstantType") var observedAt: Instant? = null, @Column(name="collected_at") @org.hibernate.annotations.Type(type="com.dscorp.wispadmin.servicehealth.domain.UtcInstantType") var collectedAt: Instant = Instant.now(),
    @Column(name="associated_device_count") var associatedDeviceCount: Int? = null, @Column(name="associated2g") var associated2g: Int? = null, @Column(name="associated5g") var associated5g: Int? = null,
    @Column(name="lan_device_count") var lanDeviceCount: Int? = null,
    @Enumerated(EnumType.STRING) @Column(name="quality_status") var qualityStatus: Quality = Quality.MISSING,
    @Column(name="source_run_id") var sourceRunId: Long? = null, @Column(name="error_reason") var errorReason: String? = null
)

@Entity
@Table(name="acs_wifi_station_sample", uniqueConstraints=[UniqueConstraint(name="uk_sh_station_reading",columnNames=["count_sample_id","station_key","band"])], indexes=[Index(name="idx_sh_station_sub_time",columnList="subscription_id,observed_at")])
class WifiStationSample(
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="id") var id: Long? = null,
    @Column(name="count_sample_id") var countSampleId: Long = 0, @Column(name="subscription_id") var subscriptionId: Int = 0,
    @Column(name="station_key", length=64) var stationKey: String = "", @Column(name="band", length=8) var band: String = "",
    @Column(name="observed_at") @org.hibernate.annotations.Type(type="com.dscorp.wispadmin.servicehealth.domain.UtcInstantType") var observedAt: Instant = Instant.now(), @Column(name="collected_at") @org.hibernate.annotations.Type(type="com.dscorp.wispadmin.servicehealth.domain.UtcInstantType") var collectedAt: Instant = Instant.now(),
    @Column(name="rssi") var rssi: Double? = null, @Column(name="snr") var snr: Double? = null, @Column(name="noise") var noise: Double? = null,
    @Column(name="rx_rate") var rxRate: Double? = null, @Column(name="tx_rate") var txRate: Double? = null, @Column(name="packets_tx") var packetsTx: Long? = null, @Column(name="packets_rx") var packetsRx: Long? = null,
    @Enumerated(EnumType.STRING) @Column(name="quality_status") var qualityStatus: Quality = Quality.FRESH
)

@Entity
@Table(name="acs_wifi_status_current")
class WifiCurrent(
    @Column(name="model", length=64) var model: String? = null,
    @Id @Column(name="subscription_id") var subscriptionId: Int = 0, @Column(name="device_id", length=128) var deviceId: String = "",
    @Column(name="count_sample_id") var countSampleId: Long? = null, @Column(name="inform_at") @org.hibernate.annotations.Type(type="com.dscorp.wispadmin.servicehealth.domain.UtcInstantType") var informAt: Instant? = null, @Column(name="observed_at") @org.hibernate.annotations.Type(type="com.dscorp.wispadmin.servicehealth.domain.UtcInstantType") var observedAt: Instant? = null,
    @Column(name="associated_device_count") var associatedDeviceCount: Int? = null, @Column(name="updated_at") @org.hibernate.annotations.Type(type="com.dscorp.wispadmin.servicehealth.domain.UtcInstantType") var updatedAt: Instant = Instant.now(),
    @Enumerated(EnumType.STRING) @Column(name="quality_status") var qualityStatus: Quality = Quality.MISSING
)

@Entity
@Table(name="capability_profile", uniqueConstraints=[UniqueConstraint(name="uk_sh_read_profile",columnNames=["manufacturer","model","firmware"])])
class ReadCapabilityProfile(
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="id") var id: Long? = null,
    @Column(name="manufacturer", length=64) var manufacturer: String = "", @Column(name="model", length=64) var model: String = "",
    @Column(name="firmware", length=64) var firmware: String = "*", @Column(name="wifi_count") var wifiCount: Boolean = false, @Column(name="wifi_signal") var wifiSignal: Boolean = false,
    @Column(name="station_bytes") var stationBytes: Boolean = false, @Column(name="verified_at") @org.hibernate.annotations.Type(type="com.dscorp.wispadmin.servicehealth.domain.UtcInstantType") var verifiedAt: Instant? = null, @Column(name="version") var version: String = "wifi-v1"
)

@Entity
@Table(name="telemetry_source_run", indexes=[Index(name="idx_sh_run_source_time",columnList="source,equipment_key,started_at")])
class TelemetryRun(
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="id") var id: Long? = null,
    @Column(name="source") var source: String = "", @Column(name="equipment_key") var equipmentKey: String = "", @Column(name="domain_run_id") var domainRunId: Long? = null,
    @Column(name="started_at") @org.hibernate.annotations.Type(type="com.dscorp.wispadmin.servicehealth.domain.UtcInstantType") var startedAt: Instant = Instant.now(), @Column(name="completed_at") @org.hibernate.annotations.Type(type="com.dscorp.wispadmin.servicehealth.domain.UtcInstantType") var completedAt: Instant? = null,
    @Enumerated(EnumType.STRING) @Column(name="quality_status") var qualityStatus: Quality = Quality.FRESH,
    @Column(name="read_count") var readCount: Int = 0, @Column(name="written_count") var writtenCount: Int = 0, @Column(name="missing_count") var missingCount: Int = 0, @Column(name="unmapped_count") var unmappedCount: Int = 0,
    @Column(name="unsupported_count") var unsupportedCount: Int = 0, @Column(name="error_count") var errorCount: Int = 0, @Column(name="lag_seconds") var lagSeconds: Long = 0, @Column(name="error_reason") var errorReason: String? = null
)

@Entity
@Table(name="identity_link",indexes=[Index(name="idx_sh_identity_sub",columnList="subscription_id,valid_to"),Index(name="idx_sh_identity_value",columnList="kind,identity_value,valid_to")])
class IdentityLink(
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="id") var id: Long? = null,
    @Column(name="subscription_id") var subscriptionId: Int = 0, @Column(name="kind", length=32) var kind: String = "",
    @Column(name="identity_value", length=160) var identityValue: String = "",
    @Column(name="valid_from") @org.hibernate.annotations.Type(type="com.dscorp.wispadmin.servicehealth.domain.UtcInstantType") var validFrom: Instant = Instant.now(), @Column(name="valid_to") @org.hibernate.annotations.Type(type="com.dscorp.wispadmin.servicehealth.domain.UtcInstantType") var validTo: Instant? = null,
    @Column(name="source") var source: String = "DOMAIN", @Column(name="confidence") var confidence: Double = 1.0, @Column(name="verified_at") @org.hibernate.annotations.Type(type="com.dscorp.wispadmin.servicehealth.domain.UtcInstantType") var verifiedAt: Instant = Instant.now()
)

@Entity
@Table(name="service_identity_conflict", indexes=[Index(name="idx_sh_conflict_status",columnList="status,created_at")])
class IdentityConflict(
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="id") var id: Long? = null,
    @Column(name="conflict_key", unique=true,length=200) var conflictKey: String = "", @Column(name="kind") var kind: String = "", @Column(name="identity_value") var identityValue: String = "",
    @Column(name="subscription_ids_json", columnDefinition="TEXT") var subscriptionIdsJson: String = "[]",
    @Column(name="status") var status: String = "OPEN", @Column(name="reason") var reason: String = "AMBIGUOUS", @Column(name="created_at") @org.hibernate.annotations.Type(type="com.dscorp.wispadmin.servicehealth.domain.UtcInstantType") var createdAt: Instant = Instant.now(),
    @Column(name="resolved_at") @org.hibernate.annotations.Type(type="com.dscorp.wispadmin.servicehealth.domain.UtcInstantType") var resolvedAt: Instant? = null, @Column(name="resolved_by") var resolvedBy: Int? = null, @Column(name="resolution", length=500) var resolution: String? = null
)

@Entity
@Table(name="service_health_event", indexes=[Index(name="idx_sh_event_sub_time",columnList="subscription_id,observed_at")])
class HealthEvent(
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="id") var id: Long? = null,
    @Column(name="subscription_id") var subscriptionId: Int = 0, @Column(name="diagnosis_code") var diagnosisCode: String = "", @Column(name="event_status") var eventStatus: String = "OPEN",
    @Enumerated(EnumType.STRING) @Column(name="confidence") var confidence: Confidence = Confidence.LOW,
    @Column(name="observed_at") @org.hibernate.annotations.Type(type="com.dscorp.wispadmin.servicehealth.domain.UtcInstantType") var observedAt: Instant = Instant.now(), @Column(name="evaluated_at") @org.hibernate.annotations.Type(type="com.dscorp.wispadmin.servicehealth.domain.UtcInstantType") var evaluatedAt: Instant = Instant.now(), @Column(name="ended_at") @org.hibernate.annotations.Type(type="com.dscorp.wispadmin.servicehealth.domain.UtcInstantType") var endedAt: Instant? = null,
    @Column(name="rule_version") var ruleVersion: String = "service-health-v1", @Column(name="diagnosis_json", columnDefinition="TEXT") var diagnosisJson: String = "{}",
    @Column(name="identity_snapshot_json", columnDefinition="TEXT") var identitySnapshotJson: String = "{}", @Column(name="suppressing_incident_id") var suppressingIncidentId: Long? = null
)

@Entity
@Table(name="service_health_current")
class HealthCurrent(
    @Id @Column(name="subscription_id") var subscriptionId: Int = 0, @Column(name="evaluated_at") @org.hibernate.annotations.Type(type="com.dscorp.wispadmin.servicehealth.domain.UtcInstantType") var evaluatedAt: Instant = Instant.now(),
    @Column(name="summary_json", columnDefinition="TEXT") var summaryJson: String = "{}"
)

@Entity
@Table(name="evidence_link", uniqueConstraints=[UniqueConstraint(name="uk_sh_evidence",columnNames=["health_event_id","source","reference_id"])])
class EvidenceLink(
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="id") var id: Long? = null,
    @Column(name="health_event_id") var healthEventId: Long = 0, @Column(name="source") var source: String = "", @Column(name="reference_id") var referenceId: String = "",
    @Column(name="observed_at") @org.hibernate.annotations.Type(type="com.dscorp.wispadmin.servicehealth.domain.UtcInstantType") var observedAt: Instant? = null, @Column(name="summary_json", columnDefinition="TEXT") var summaryJson: String = "{}"
)

@Entity
@Table(name="service_health_cursor")
class HealthCursor(
    @Id @Column(name="cursor_key", length=160) var cursorKey: String = "", @Column(name="observed_at") @org.hibernate.annotations.Type(type="com.dscorp.wispadmin.servicehealth.domain.UtcInstantType") var observedAt: Instant? = null,
    @Column(name="reference_id") var referenceId: Long = 0, @Column(name="updated_at") @org.hibernate.annotations.Type(type="com.dscorp.wispadmin.servicehealth.domain.UtcInstantType") var updatedAt: Instant = Instant.now(), @Version @Column(name="version") var version: Long? = null
)

@Entity
@Table(name="service_traffic_evidence")
class TrafficEvidence(
    @Id @Column(name="event_id") var eventId: Long = 0, @Column(name="subscription_id") var subscriptionId: Int? = null, @Column(name="router_id") var routerId: Int? = null,
    @Column(name="event_status") var eventStatus: String = "", @Column(name="anomaly_type") var anomalyType: String = "", @Column(name="observed_at") @org.hibernate.annotations.Type(type="com.dscorp.wispadmin.servicehealth.domain.UtcInstantType") var observedAt: Instant = Instant.now(),
    @Column(name="coverage_pct") var coveragePct: Double = 0.0, @Column(name="confidence") var confidence: Double = 0.0,
    @Column(name="evidence_json", columnDefinition="TEXT") var evidenceJson: String? = null
)

@Entity
@Table(name="service_incident_subscription",uniqueConstraints=[UniqueConstraint(name="uk_sh_affected",columnNames=["incident_id","subscription_id"])])
class IncidentSubscription(
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="id") var id: Long? = null,
    @Column(name="incident_id") var incidentId: Long = 0, @Column(name="subscription_id") var subscriptionId: Int = 0, @Column(name="health_event_id") var healthEventId: Long? = null,
    @Column(name="state") var state: String = "AFFECTED", @Column(name="first_seen_at") @org.hibernate.annotations.Type(type="com.dscorp.wispadmin.servicehealth.domain.UtcInstantType") var firstSeenAt: Instant = Instant.now(), @Column(name="last_seen_at") @org.hibernate.annotations.Type(type="com.dscorp.wispadmin.servicehealth.domain.UtcInstantType") var lastSeenAt: Instant = Instant.now(),
    @Column(name="recovered_at") @org.hibernate.annotations.Type(type="com.dscorp.wispadmin.servicehealth.domain.UtcInstantType") var recoveredAt: Instant? = null, @Column(name="scope_json", columnDefinition="TEXT") var scopeJson: String = "{}"
)

@Entity
@Table(name="service_remote_action",uniqueConstraints=[UniqueConstraint(name="uk_sh_action_request",columnNames=["actor_id","request_key"])],indexes=[Index(name="idx_sh_action_device_time",columnList="device_key,created_at")])
class RemoteAction(
    @Column(name="acs_device_id", length=128) var acsDeviceId: String? = null,
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="id") var id: Long? = null,
    @Column(name="subscription_id") var subscriptionId: Int = 0, @Column(name="actor_id") var actorId: Int = 0, @Column(name="request_key", length=128) var requestKey: String = "",
    @Column(name="device_key", length=160) var deviceKey: String = "", @Column(name="action") var action: String = "", @Column(name="status") var status: String = "PENDING",
    @Column(name="created_at") @org.hibernate.annotations.Type(type="com.dscorp.wispadmin.servicehealth.domain.UtcInstantType") var createdAt: Instant = Instant.now(), @Column(name="completed_at") @org.hibernate.annotations.Type(type="com.dscorp.wispadmin.servicehealth.domain.UtcInstantType") var completedAt: Instant? = null,
    @Column(name="network_status") var networkStatus: String? = null, @Column(name="wifi_status") var wifiStatus: String? = null, @Column(name="network_channel") var networkChannel: String? = null,
    @Column(name="task_id") var taskId: String? = null, @Column(name="error_reason", length=200) var errorReason: String? = null,
    // Store only HMAC of requested values, never credentials or payloads.
    @Column(name="request_digest", length=64) var requestDigest: String = "",
    @Column(name="confirmation_json", columnDefinition="TEXT") var confirmationJson: String? = null
)
