package com.dscorp.wispadmin.observability.service

import com.dscorp.wispadmin.observability.dto.AlertChannelDto
import com.dscorp.wispadmin.observability.dto.AlertChannelUpsertRequest
import com.dscorp.wispadmin.observability.dto.AlertEventDto
import com.dscorp.wispadmin.observability.dto.AlertRuleDto
import com.dscorp.wispadmin.observability.dto.AlertRuleUpsertRequest
import com.dscorp.wispadmin.observability.dto.PagedResponse
import com.dscorp.wispadmin.observability.dto.TestChannelResult
import com.dscorp.wispadmin.observability.dto.toDto
import com.dscorp.wispadmin.observability.entity.ObsAlertChannel
import com.dscorp.wispadmin.observability.entity.ObsAlertRule
import com.dscorp.wispadmin.observability.port.AlertChannelRegistry
import com.dscorp.wispadmin.observability.port.AlertNotification
import com.dscorp.wispadmin.observability.repository.ObsAlertChannelRepository
import com.dscorp.wispadmin.observability.repository.ObsAlertEventRepository
import com.dscorp.wispadmin.observability.repository.ObsAlertRuleRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class ObsAlertService(
    private val ruleRepository: ObsAlertRuleRepository,
    private val channelRepository: ObsAlertChannelRepository,
    private val alertEventRepository: ObsAlertEventRepository,
    private val channelRegistry: AlertChannelRegistry
) {

    @Transactional(readOnly = true)
    fun listRules(): List<AlertRuleDto> = ruleRepository.findAllByOrderByCreatedAtDesc().map { it.toDto() }

    @Transactional
    fun createRule(request: AlertRuleUpsertRequest): AlertRuleDto {
        val name = request.name?.trim().takeUnless { it.isNullOrBlank() }
            ?: throw IllegalArgumentException("El nombre de la regla es obligatorio")
        val type = request.type ?: throw IllegalArgumentException("El tipo de alerta es obligatorio")
        val now = LocalDateTime.now()
        val rule = ObsAlertRule(
            name = name,
            type = type,
            createdAt = now,
            updatedAt = now
        )
        applyRule(rule, request)
        return ruleRepository.save(rule).toDto()
    }

    @Transactional
    fun updateRule(id: Long, request: AlertRuleUpsertRequest): AlertRuleDto? {
        val rule = ruleRepository.findById(id).orElse(null) ?: return null
        request.name?.trim()?.takeIf { it.isNotBlank() }?.let { rule.name = it }
        request.type?.let { rule.type = it }
        applyRule(rule, request)
        rule.updatedAt = LocalDateTime.now()
        return ruleRepository.save(rule).toDto()
    }

    @Transactional
    fun deleteRule(id: Long): Boolean {
        if (!ruleRepository.existsById(id)) return false
        ruleRepository.deleteById(id)
        return true
    }

    @Transactional(readOnly = true)
    fun listChannels(): List<AlertChannelDto> =
        channelRepository.findAllByOrderByCreatedAtDesc().map { it.toDto() }

    @Transactional
    fun createChannel(request: AlertChannelUpsertRequest): AlertChannelDto {
        val name = request.name?.trim().takeUnless { it.isNullOrBlank() }
            ?: throw IllegalArgumentException("El nombre del canal es obligatorio")
        val type = request.type ?: throw IllegalArgumentException("El tipo de canal es obligatorio")
        val now = LocalDateTime.now()
        val channel = ObsAlertChannel(
            name = name,
            type = type,
            enabled = request.enabled ?: true,
            target = request.target?.trim().orEmpty(),
            configJson = request.configJson,
            createdAt = now,
            updatedAt = now
        )
        return channelRepository.save(channel).toDto()
    }

    @Transactional
    fun updateChannel(id: Long, request: AlertChannelUpsertRequest): AlertChannelDto? {
        val channel = channelRepository.findById(id).orElse(null) ?: return null
        request.name?.trim()?.takeIf { it.isNotBlank() }?.let { channel.name = it }
        request.type?.let { channel.type = it }
        request.enabled?.let { channel.enabled = it }
        request.target?.let { channel.target = it.trim() }
        if (request.configJson != null) {
            channel.configJson = request.configJson.ifBlank { null }
        }
        channel.updatedAt = LocalDateTime.now()
        return channelRepository.save(channel).toDto()
    }

    @Transactional
    fun deleteChannel(id: Long): Boolean {
        if (!channelRepository.existsById(id)) return false
        channelRepository.deleteById(id)
        return true
    }

    @Transactional(readOnly = true)
    fun testChannel(id: Long): TestChannelResult? {
        val channel = channelRepository.findById(id).orElse(null) ?: return null
        val adapter = channelRegistry.forType(channel.type)
            ?: return TestChannelResult(false, "Tipo de canal no soportado")
        val result = adapter.send(
            channel,
            AlertNotification(
                title = "Prueba de canal de alertas",
                message = "Este es un mensaje de prueba desde el hub de observabilidad.",
                severity = "info",
                type = "TEST",
                issueUrl = null
            )
        )
        return TestChannelResult(result.ok, result.detail)
    }

    @Transactional(readOnly = true)
    fun listEvents(page: Int, size: Int): PagedResponse<AlertEventDto> {
        val pageable = PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, 200))
        val result = alertEventRepository.findAllByOrderByCreatedAtDesc(pageable)
        return PagedResponse(
            content = result.content.map { it.toDto() },
            page = result.number,
            size = result.size,
            totalElements = result.totalElements,
            totalPages = result.totalPages
        )
    }

    private fun applyRule(rule: ObsAlertRule, request: AlertRuleUpsertRequest) {
        request.enabled?.let { rule.enabled = it }
        rule.platform = request.platform?.trim()?.ifBlank { null } ?: rule.platform
        rule.severity = request.severity?.trim()?.ifBlank { null } ?: rule.severity
        rule.environment = request.environment?.trim()?.ifBlank { null } ?: rule.environment
        rule.routePattern = request.routePattern?.trim()?.ifBlank { null } ?: rule.routePattern
        request.comparator?.let { rule.comparator = it }
        request.threshold?.let { rule.threshold = it }
        request.windowMinutes?.let { rule.windowMinutes = it.coerceAtLeast(1) }
        request.baselineMultiplier?.let { rule.baselineMultiplier = it }
        request.minSample?.let { rule.minSample = it.coerceAtLeast(0) }
        request.channelIds?.let { ids ->
            rule.channelIds = ids.joinToString(",")
        }
    }
}
