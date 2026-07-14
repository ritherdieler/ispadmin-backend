package com.dscorp.wispadmin.observability.repository

import com.dscorp.wispadmin.observability.entity.ObsDeployEvent
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDateTime

interface ObsDeployEventRepository : JpaRepository<ObsDeployEvent, Long> {

    fun findByPlatformAndDeployedAtBetweenOrderByDeployedAtDesc(
        platform: String,
        from: LocalDateTime,
        to: LocalDateTime
    ): List<ObsDeployEvent>

    fun findByDeployedAtBetweenOrderByDeployedAtDesc(
        from: LocalDateTime,
        to: LocalDateTime
    ): List<ObsDeployEvent>

    fun findTop50ByOrderByDeployedAtDesc(): List<ObsDeployEvent>

    fun findTop50ByPlatformOrderByDeployedAtDesc(platform: String): List<ObsDeployEvent>

    fun findFirstByReleaseOrderByDeployedAtDesc(release: String): ObsDeployEvent?

    fun findFirstByPlatformAndDeployedAtLessThanOrderByDeployedAtDesc(
        platform: String,
        deployedAt: LocalDateTime
    ): ObsDeployEvent?

    fun findFirstByPlatformAndDeployedAtGreaterThanOrderByDeployedAtAsc(
        platform: String,
        deployedAt: LocalDateTime
    ): ObsDeployEvent?

    fun findFirstByDeployedAtLessThanOrderByDeployedAtDesc(deployedAt: LocalDateTime): ObsDeployEvent?

    fun findFirstByDeployedAtGreaterThanOrderByDeployedAtAsc(deployedAt: LocalDateTime): ObsDeployEvent?
}
