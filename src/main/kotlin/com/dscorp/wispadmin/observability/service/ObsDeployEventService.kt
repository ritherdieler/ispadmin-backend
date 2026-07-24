package com.dscorp.wispadmin.observability.service

import com.dscorp.wispadmin.observability.dto.CreateDeployEventRequest
import com.dscorp.wispadmin.observability.dto.DeployEventDto
import com.dscorp.wispadmin.observability.dto.toDto
import com.dscorp.wispadmin.observability.entity.ObsDeployEvent
import com.dscorp.wispadmin.observability.repository.ObsDeployEventRepository
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class ObsDeployEventService(
    private val deployEventRepository: ObsDeployEventRepository
) {

    fun list(platform: String?, from: LocalDateTime?, to: LocalDateTime?): List<DeployEventDto> {
        val normalizedPlatform = platform?.takeIf { it.isNotBlank() }
        val events = if (from != null && to != null) {
            if (normalizedPlatform != null) {
                deployEventRepository.findByPlatformAndDeployedAtBetweenOrderByDeployedAtDesc(normalizedPlatform, from, to)
            } else {
                deployEventRepository.findByDeployedAtBetweenOrderByDeployedAtDesc(from, to)
            }
        } else {
            if (normalizedPlatform != null) {
                deployEventRepository.findTop50ByPlatformOrderByDeployedAtDesc(normalizedPlatform)
            } else {
                deployEventRepository.findTop50ByOrderByDeployedAtDesc()
            }
        }
        return events.map { it.toDto() }
    }

    fun create(request: CreateDeployEventRequest): DeployEventDto {
        val entity = ObsDeployEvent(
            platform = request.platform,
            release = request.release,
            semver = request.semver,
            gitSha = request.gitSha,
            notes = request.notes,
            deployedAt = LocalDateTime.now()
        )
        return deployEventRepository.save(entity).toDto()
    }
}
