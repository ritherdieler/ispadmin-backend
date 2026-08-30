package com.dscorp.wispadmin.servicehealth.dto

import com.dscorp.wispadmin.servicehealth.domain.*
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import java.time.Instant

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class Evidence(val source: String, val metric: String, val observedAt: Instant?, val value: Any?, val qualityStatus: Quality,
                    val referenceId: String? = null, val link: String? = null)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class MissingEvidence(val source: String, val metric: String, val qualityStatus: Quality, val reason: String)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class Diagnosis(val diagnosisCode: String, val probableCause: String, val confidence: Confidence,
                     val evidence: List<Evidence>, val missingEvidence: List<MissingEvidence>,
                     val confidenceBreakdown: List<String>, val recommendedNextCheck: String,
                     val affectedScope: Map<String, Any?>, val suppressingIncidentId: Long? = null)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class HealthSummary(val subscriptionId: Int, val evaluatedAt: Instant, val states: Map<String,String>,
                         val sources: List<Evidence>, val diagnoses: List<Diagnosis>, val missingEvidence: List<MissingEvidence>,
                         val identity: Map<String,Any?>, val pilotEnabled: Boolean, val actionsEnabled: Boolean,
                         val ruleVersion: String = "service-health-v1")

data class CpeGponStatus(val state: String, val rxDbm: String?, val txDbm: String?, val qualityStatus: Quality)
data class CpeAcsStatus(val state: String, val lastInform: Instant?, val reachable: Boolean, val qualityStatus: Quality)
data class CpeCapabilities(val canWriteWanViaTr069: Boolean = false, val canWriteWanViaOmci: Boolean = false,
                           val canWriteWifiViaTr069: Boolean = false, val wanManagedBy: String = "TR069",
                           val vendor: String? = null, val model: String? = null)
data class CpeStatus(val online: Boolean, val rxDbm: String?, val lastInform: Instant?, val sn: String?,
                     val gponStatus: CpeGponStatus, val acsStatus: CpeAcsStatus, val capabilities: CpeCapabilities,
                     val actionsEnabled: Boolean = false)

fun qualityAt(observed: Instant?, now: Instant, freshSeconds: Long): Quality = when {
    observed == null -> Quality.MISSING
    observed.isAfter(now.plusSeconds(60)) -> Quality.INVALID
    observed.isBefore(now.minusSeconds(freshSeconds)) -> Quality.STALE
    else -> Quality.FRESH
}
