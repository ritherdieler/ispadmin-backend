package com.dscorp.wispadmin.netdiag.service

import com.dscorp.wispadmin.netdiag.config.NetDiagProperties
import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagIncident
import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagIncidentEvent
import com.dscorp.wispadmin.netdiag.domain.repository.NetDiagIncidentEventRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.HttpMethod
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import java.time.Instant

@Service
@ConditionalOnProperty(prefix = "net.diag", name = ["enabled"], havingValue = "true")
class NetDiagLlmWebhookService(
    private val properties: NetDiagProperties,
    private val llmContextService: NetDiagLlmContextService,
    private val incidentEventRepository: NetDiagIncidentEventRepository,
    @Qualifier("netDiagRestTemplate")
    private val restTemplate: RestTemplate
) {

    private val logger = LoggerFactory.getLogger(NetDiagLlmWebhookService::class.java)

    @Async
    fun notifyIncidentOpened(incident: NetDiagIncident) {
        if (!properties.llm.webhookEnabled) {
            return
        }
        val url = properties.llm.webhookUrl.trim()
        if (url.isBlank()) {
            return
        }
        if (!incident.severity.equals("P0", ignoreCase = true)) {
            return
        }
        val incidentId = incident.id ?: return

        try {
            val payload = llmContextService.buildDiagnosticJson(incidentId)
            val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
            val entity = HttpEntity(payload, headers)
            restTemplate.exchange(url, HttpMethod.POST, entity, String::class.java)
            incidentEventRepository.save(
                NetDiagIncidentEvent(
                    incident = incident,
                    type = "LLM_WEBHOOK_SENT",
                    payload = url,
                    createdAt = Instant.now()
                )
            )
        } catch (ex: Exception) {
            logger.warn("LLM webhook failed for incident {}: {}", incidentId, ex.message)
            incidentEventRepository.save(
                NetDiagIncidentEvent(
                    incident = incident,
                    type = "LLM_WEBHOOK_FAILED",
                    payload = ex.message,
                    createdAt = Instant.now()
                )
            )
        }
    }
}
