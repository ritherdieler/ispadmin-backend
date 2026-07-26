package com.dscorp.wispadmin.netdiag.service

import com.dscorp.wispadmin.netdiag.config.NetDiagProperties
import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagIncident
import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagNotificationLog
import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagTarget
import com.dscorp.wispadmin.netdiag.domain.repository.NetDiagIncidentEventRepository
import com.dscorp.wispadmin.netdiag.domain.repository.NetDiagIncidentRepository
import com.dscorp.wispadmin.netdiag.domain.repository.NetDiagNotificationLogRepository
import com.dscorp.wispadmin.wispadmin.service.WhatsAppService
import com.dscorp.wispadmin.wispadmin.service.whatsapp.NamedTemplateParameter
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class WhatsAppOpsNotifierTest {

    private val whatsAppService = mockk<WhatsAppService>()
    private val notificationLogRepository = mockk<NetDiagNotificationLogRepository>()
    private val incidentRepository = mockk<NetDiagIncidentRepository>()
    private val incidentEventRepository = mockk<NetDiagIncidentEventRepository>(relaxed = true)
    private val properties = NetDiagProperties().apply {
        alert.cooldownMinutes = 15
        alert.minDurationSeconds = 120
        whatsapp.nocPhone = "51999888777"
        whatsapp.templateName = "noc_alert_v1"
        whatsapp.languageCode = "es"
    }
    private val notifier = WhatsAppOpsNotifier(
        whatsAppService = whatsAppService,
        notificationLogRepository = notificationLogRepository,
        incidentRepository = incidentRepository,
        incidentEventRepository = incidentEventRepository,
        properties = properties
    )

    @Test
    fun `no notifica si incidente es mas reciente que min-duration`() {
        val incident = openIncident(openedAt = Instant.now().minus(30, ChronoUnit.SECONDS))

        notifier.notifyIfNeeded(incident)

        verify(exactly = 0) { whatsAppService.sendTemplateMessage(any(), any(), any(), any()) }
        verify(exactly = 0) { notificationLogRepository.save(any()) }
    }

    @Test
    fun `respeta cooldown anti-spam`() {
        val incident = openIncident(
            openedAt = Instant.now().minus(10, ChronoUnit.MINUTES),
            lastNotifiedAt = Instant.now().minus(5, ChronoUnit.MINUTES)
        )

        notifier.notifyIfNeeded(incident)

        verify(exactly = 0) { whatsAppService.sendTemplateMessage(any(), any(), any(), any()) }
    }

    @Test
    fun `envia plantilla noc_alert_v1 y registra notification_log`() {
        val incident = openIncident(openedAt = Instant.now().minus(5, ChronoUnit.MINUTES))
        every {
            whatsAppService.sendTemplateMessage(any(), any(), any(), any())
        } returns true
        val logSlot = slot<NetDiagNotificationLog>()
        every { notificationLogRepository.save(capture(logSlot)) } answers { firstArg() }
        every { incidentRepository.save(any()) } answers { firstArg() }
        every { incidentEventRepository.save(any()) } answers { firstArg() }
        val paramsSlot = slot<List<NamedTemplateParameter>>()

        notifier.notifyIfNeeded(incident)

        verify {
            whatsAppService.sendTemplateMessage(
                phoneNumber = "51999888777",
                templateName = "noc_alert_v1",
                languageCode = "es",
                parameters = capture(paramsSlot)
            )
        }
        assertEquals("SENT", logSlot.captured.status)
        assertEquals("whatsapp", logSlot.captured.channel)
        assertEquals("51999888777", logSlot.captured.destination)
        verify { incidentRepository.save(match { it.lastNotifiedAt != null }) }
        verify { incidentEventRepository.save(match { it.type == "WHATSAPP_NOTIFIED" }) }
    }

    private fun openIncident(
        openedAt: Instant,
        lastNotifiedAt: Instant? = null
    ): NetDiagIncident {
        return NetDiagIncident(
            id = 9L,
            target = NetDiagTarget(id = 1L, name = "MK1", deviceRefId = 7L),
            dedupKey = "LINK_DOWN:1:ether1",
            status = "OPEN",
            severity = "P0",
            title = "Link down: ether1",
            reasonCode = "LINK_DOWN",
            openedAt = openedAt,
            lastNotifiedAt = lastNotifiedAt
        )
    }
}
