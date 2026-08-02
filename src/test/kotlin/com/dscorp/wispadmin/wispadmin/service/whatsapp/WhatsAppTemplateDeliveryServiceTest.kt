package com.dscorp.wispadmin.wispadmin.service.whatsapp

import com.dscorp.wispadmin.wispadmin.data.model.EquipmentCondition
import com.dscorp.wispadmin.wispadmin.data.model.ServiceStatus
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppMessageLog
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppMessageLogRepository
import com.dscorp.wispadmin.wispadmin.service.WhatsAppSendResult
import com.dscorp.wispadmin.wispadmin.service.WhatsAppService
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WelcomeTemplateContext
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppTemplateCatalog
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppTemplateCode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

class WhatsAppTemplateDeliveryServiceTest {

    private val whatsAppService = mock(WhatsAppService::class.java)
    private val messageLogRepository = mock(WhatsAppMessageLogRepository::class.java)
    private val service = WhatsAppTemplateDeliveryService(whatsAppService, messageLogRepository)

    private val definition = WhatsAppTemplateCatalog.get(WhatsAppTemplateCode.WELCOME_CUSTOMER)

    @Test
    fun `deliverTemplate persists metaMessageId from Meta response`() {
        val subscription = Subscription(
            firstName = "Juan",
            lastName = "Perez",
            phone = "902354183",
            serviceStatus = ServiceStatus.ACTIVE,
            equipmentCondition = EquipmentCondition.LOAN
        ).apply { id = 1 }

        doAnswer {
            WhatsAppSendResult(
                success = true,
                metaResponse = """{"messages":[{"id":"wamid.test123"}]}""",
                metaMessageId = "wamid.test123",
                recipient = "51902354183",
                senderPhoneNumberId = "123"
            )
        }.`when`(whatsAppService).sendTemplateMessageWithMetaResponse(
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyList()
        )
        doAnswer { invocation ->
            invocation.getArgument(0)
        }.`when`(messageLogRepository).save(org.mockito.ArgumentMatchers.any(WhatsAppMessageLog::class.java))

        service.deliverTemplate(
            definition = definition,
            subscription = subscription,
            phone = "902354183",
            subscriptionId = 1,
            welcomeContext = WelcomeTemplateContext(
                serviceTitle = "Internet 100% Fibra Optica",
                serviceDetails = "200 Mbps",
                planName = "F200",
                planPrice = "80.00",
                paymentDay = "5",
                paymentInfo = "Paga en nuestras oficinas"
            ),
            campaignId = "camp-1",
            operatorUsername = "admin"
        )

        val captor = ArgumentCaptor.forClass(WhatsAppMessageLog::class.java)
        verify(messageLogRepository).save(captor.capture())
        val saved = captor.value
        assertEquals("wamid.test123", saved.metaMessageId)
        assertEquals("camp-1", saved.campaignId)
        assertEquals("admin", saved.operatorUsername)
        assertNotNull(saved.sentAt)
    }
}
