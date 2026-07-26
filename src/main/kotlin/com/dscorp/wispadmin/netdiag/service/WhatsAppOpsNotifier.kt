package com.dscorp.wispadmin.netdiag.service

import com.dscorp.wispadmin.netdiag.config.NetDiagProperties
import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagIncident
import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagIncidentEvent
import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagNotificationLog
import com.dscorp.wispadmin.netdiag.domain.repository.NetDiagIncidentEventRepository
import com.dscorp.wispadmin.netdiag.domain.repository.NetDiagIncidentRepository
import com.dscorp.wispadmin.netdiag.domain.repository.NetDiagNotificationLogRepository
import com.dscorp.wispadmin.wispadmin.service.WhatsAppService
import com.dscorp.wispadmin.wispadmin.service.whatsapp.NamedTemplateParameter
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant

@Service
@ConditionalOnProperty(prefix = "net.diag", name = ["enabled"], havingValue = "true")
class WhatsAppOpsNotifier(
    private val whatsAppService: WhatsAppService,
    private val notificationLogRepository: NetDiagNotificationLogRepository,
    private val incidentRepository: NetDiagIncidentRepository,
    private val incidentEventRepository: NetDiagIncidentEventRepository,
    private val properties: NetDiagProperties
) {

    private val logger = LoggerFactory.getLogger(WhatsAppOpsNotifier::class.java)

    fun notifyIfNeeded(incident: NetDiagIncident) {
        val phone = properties.whatsapp.nocPhone
        if (phone.isBlank()) {
            return
        }
        if (incident.status != "OPEN") {
            return
        }

        val now = Instant.now()
        val minDuration = Duration.ofSeconds(properties.alert.minDurationSeconds.toLong())
        if (Duration.between(incident.openedAt, now) < minDuration) {
            return
        }

        val cooldown = Duration.ofMinutes(properties.alert.cooldownMinutes.toLong())
        val lastNotified = incident.lastNotifiedAt
        if (lastNotified != null && Duration.between(lastNotified, now) < cooldown) {
            return
        }

        val parameters = listOf(
            NamedTemplateParameter("severity", incident.severity),
            NamedTemplateParameter("title", incident.title.take(60)),
            NamedTemplateParameter("reason_code", incident.reasonCode ?: "UNKNOWN"),
            NamedTemplateParameter("target", incident.target?.name ?: "n/a")
        )

        val log = NetDiagNotificationLog(
            incident = incident,
            channel = "whatsapp",
            status = "PENDING",
            destination = phone,
            payload = parameters.joinToString(",") { "${it.parameterName}=${it.text}" },
            createdAt = now
        )

        try {
            val sent = whatsAppService.sendTemplateMessage(
                phoneNumber = phone,
                templateName = properties.whatsapp.templateName,
                languageCode = properties.whatsapp.languageCode,
                parameters = parameters
            )
            log.status = if (sent) "SENT" else "FAILED"
            if (!sent) {
                log.error = "sendTemplateMessage returned false"
            }
            notificationLogRepository.save(log)
            if (sent) {
                incident.lastNotifiedAt = now
                incidentRepository.save(incident)
                incidentEventRepository.save(
                    NetDiagIncidentEvent(
                        incident = incident,
                        type = "WHATSAPP_NOTIFIED",
                        payload = log.payload,
                        createdAt = now
                    )
                )
            }
        } catch (ex: Exception) {
            logger.warn("WhatsApp NOC notify failed for incident {}: {}", incident.id, ex.message)
            log.status = "FAILED"
            log.error = ex.message
            notificationLogRepository.save(log)
        }
    }
}
