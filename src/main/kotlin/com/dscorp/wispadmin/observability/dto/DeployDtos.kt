package com.dscorp.wispadmin.observability.dto

import com.dscorp.wispadmin.observability.entity.ObsDeployEvent
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.time.LocalDateTime

data class DeployEventDto(
    val release: String?,
    val platform: String?,
    val semver: String?,
    val gitSha: String?,
    val deployedAt: LocalDateTime?,
    val notes: String?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class CreateDeployEventRequest(
    val platform: String,
    val release: String,
    val semver: String? = null,
    val gitSha: String? = null,
    val notes: String? = null
)

fun ObsDeployEvent.toDto() = DeployEventDto(
    release = release,
    platform = platform,
    semver = semver,
    gitSha = gitSha,
    deployedAt = deployedAt,
    notes = notes
)
