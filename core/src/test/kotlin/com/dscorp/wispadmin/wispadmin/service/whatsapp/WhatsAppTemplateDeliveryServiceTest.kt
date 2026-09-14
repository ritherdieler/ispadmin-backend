package com.dscorp.wispadmin.wispadmin.service.whatsapp

import com.dscorp.wispadmin.wispadmin.data.model.EquipmentCondition
import com.dscorp.wispadmin.wispadmin.data.model.ServiceStatus
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppMarketingOptOut
import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppMessageLog
import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppSyncedTemplate
import com.dscorp.wispadmin.wispadmin.config.WhatsAppProperties
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppMarketingOptOutRepository
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppMessageLogRepository
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppSyncedTemplateRepository
import com.dscorp.wispadmin.wispadmin.service.WhatsAppSendResult
import com.dscorp.wispadmin.wispadmin.service.WhatsAppService
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WelcomeTemplateContext
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppTemplateCatalog
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppTemplateCode
import org.springframework.beans.factory.ObjectProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify

class WhatsAppTemplateDeliveryServiceTest {

    private val whatsAppService = mock(WhatsAppService::class.java)
    private val messageLogRepository = mock(WhatsAppMessageLogRepository::class.java)
    private val syncedTemplateRepository = mock(WhatsAppSyncedTemplateRepository::class.java)
    private val marketingOptOutRepository = mock(WhatsAppMarketingOptOutRepository::class.java)
    private val templateSyncService = mock(WhatsAppTemplateSyncService::class.java)
    @Suppress("UNCHECKED_CAST")
    private val crmConversationServiceProvider =
        mock(ObjectProvider::class.java) as ObjectProvider<CrmConversationService>
    private val whatsAppProperties = WhatsAppProperties().apply {
        apiVersion = "v21.0"
        accessToken = "token"
        phoneNumberId = "123"
        businessAccountId = "456"
    }
    private val templateDisplayService = WhatsAppTemplateDisplayService(
        syncedTemplateRepository,
        templateSyncService,
        whatsAppProperties
    )
    private val service = WhatsAppTemplateDeliveryService(
        whatsAppService,
        messageLogRepository,
        templateDisplayService,
        marketingOptOutRepository,
        crmConversationServiceProvider,
    )

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
            org.mockito.ArgumentMatchers.anyList(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.nullable(WhatsAppTemplateButtonParameter::class.java)
        )
        doAnswer { invocation ->
            invocation.getArgument(0)
        }.`when`(messageLogRepository).save(org.mockito.ArgumentMatchers.any(WhatsAppMessageLog::class.java))
        `when`(syncedTemplateRepository.findByName("welcome_customer_uti")).thenReturn(
            WhatsAppSyncedTemplate(
                metaTemplateId = "tpl-welcome",
                name = "welcome_customer_uti",
                bodyText = "Hola {{customer_name}}, plan {{plan_name}} por {{plan_price}}."
            )
        )

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
        assertEquals("Hola Juan Perez, plan F200 por 80.00.", saved.message)
    }

    @Test
    fun `deliverTemplate stores international phone when candidate uses nine digits`() {
        val subscription = Subscription(
            firstName = "Augusto",
            lastName = "Valverde",
            phone = "932489604",
            serviceStatus = ServiceStatus.ACTIVE,
            equipmentCondition = EquipmentCondition.LOAN
        ).apply { id = 648 }

        doAnswer {
            WhatsAppSendResult(
                success = true,
                metaResponse = """{"messages":[{"id":"wamid.reminder"}]}""",
                metaMessageId = "wamid.reminder",
                recipient = "51932489604",
                senderPhoneNumberId = "123"
            )
        }.`when`(whatsAppService).sendTemplateMessageWithMetaResponse(
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyList(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.nullable(WhatsAppTemplateButtonParameter::class.java)
        )
        doAnswer { invocation ->
            invocation.getArgument(0)
        }.`when`(messageLogRepository).save(org.mockito.ArgumentMatchers.any(WhatsAppMessageLog::class.java))
        `when`(syncedTemplateRepository.findByName("welcome_customer_uti")).thenReturn(
            WhatsAppSyncedTemplate(
                metaTemplateId = "tpl-welcome",
                name = "welcome_customer_uti",
                bodyText = "Hola {{customer_name}}."
            )
        )

        service.deliverTemplate(
            definition = definition,
            subscription = subscription,
            phone = "932489604",
            subscriptionId = 648,
            welcomeContext = WelcomeTemplateContext(
                serviceTitle = "Internet",
                serviceDetails = "200 Mbps",
                planName = "F200",
                planPrice = "70.00",
                paymentDay = "5",
                paymentInfo = "Oficinas"
            )
        )

        val captor = ArgumentCaptor.forClass(WhatsAppMessageLog::class.java)
        verify(messageLogRepository).save(captor.capture())
        assertEquals("51932489604", captor.value.phone)
    }

    @Test
    fun `deliverTemplate generates a callback token and persists it with the log`() {
        val subscription = Subscription(
            firstName = "Juan",
            lastName = "Perez",
            phone = "902354183",
            serviceStatus = ServiceStatus.ACTIVE,
            equipmentCondition = EquipmentCondition.LOAN
        ).apply { id = 1 }

        val callbackTokenCaptor = ArgumentCaptor.forClass(String::class.java)
        doAnswer {
            WhatsAppSendResult(
                success = true,
                metaResponse = """{"messages":[{"id":"wamid.cb123"}]}""",
                metaMessageId = "wamid.cb123",
                recipient = "51902354183",
                senderPhoneNumberId = "123"
            )
        }.`when`(whatsAppService).sendTemplateMessageWithMetaResponse(
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyList(),
            callbackTokenCaptor.capture(),
            org.mockito.ArgumentMatchers.nullable(WhatsAppTemplateButtonParameter::class.java)
        )
        doAnswer { invocation ->
            invocation.getArgument(0)
        }.`when`(messageLogRepository).save(org.mockito.ArgumentMatchers.any(WhatsAppMessageLog::class.java))
        `when`(syncedTemplateRepository.findByName("welcome_customer_uti")).thenReturn(
            WhatsAppSyncedTemplate(
                metaTemplateId = "tpl-welcome",
                name = "welcome_customer_uti",
                bodyText = "Hola {{customer_name}}, plan {{plan_name}} por {{plan_price}}."
            )
        )

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
            )
        )

        val captor = ArgumentCaptor.forClass(WhatsAppMessageLog::class.java)
        verify(messageLogRepository).save(captor.capture())
        val saved = captor.value
        assertNotNull(saved.callbackId)
        assertEquals(callbackTokenCaptor.value, saved.callbackId)
    }

    @Test
    fun `deliverTemplate builds button component when template definition declares a dynamic button`() {
        val subscription = Subscription(
            firstName = "Juan",
            lastName = "Perez",
            phone = "902354183",
            serviceStatus = ServiceStatus.ACTIVE,
            equipmentCondition = EquipmentCondition.LOAN
        ).apply { id = 1 }

        val paymentReminderWithButton = WhatsAppTemplateCatalog.get(WhatsAppTemplateCode.PAYMENT_REMINDER).copy(
            buttonParameter = WhatsAppTemplateButtonDef(
                index = 0,
                subType = "url",
                source = TemplateParameterSource.PAYMENT_ID
            )
        )
        val payment = com.dscorp.wispadmin.wispadmin.data.model.Payment(
            discountAmount = 0.0,
            paid = false,
            amountToPay = 79.9,
            billingDateDatetime = java.time.LocalDateTime.of(2026, 7, 1, 0, 0)
        ).apply { id = 42; this.subscription = subscription }

        val buttonParameterCaptor = ArgumentCaptor.forClass(WhatsAppTemplateButtonParameter::class.java)
        doAnswer {
            WhatsAppSendResult(
                success = true,
                metaResponse = """{"messages":[{"id":"wamid.btn1"}]}""",
                metaMessageId = "wamid.btn1",
                recipient = "51902354183",
                senderPhoneNumberId = "123"
            )
        }.`when`(whatsAppService).sendTemplateMessageWithMetaResponse(
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyList(),
            org.mockito.ArgumentMatchers.anyString(),
            buttonParameterCaptor.capture()
        )
        doAnswer { invocation ->
            invocation.getArgument(0)
        }.`when`(messageLogRepository).save(org.mockito.ArgumentMatchers.any(WhatsAppMessageLog::class.java))
        `when`(syncedTemplateRepository.findByName("payment_reminder_gigaperu")).thenReturn(null)

        service.deliverTemplate(
            definition = paymentReminderWithButton,
            subscription = subscription,
            phone = "902354183",
            payment = payment,
            paymentId = 42
        )

        val buttonParameter = buttonParameterCaptor.value
        assertNotNull(buttonParameter)
        assertEquals("url", buttonParameter.subType)
        assertEquals(0, buttonParameter.index)
        assertEquals("42", buttonParameter.parameter.text)
    }

    @Test
    fun `deliverTemplate skips marketing template and persists SKIPPED when phone is opted out`() {
        val subscription = Subscription(
            firstName = "Juan",
            lastName = "Perez",
            phone = "987654321",
            serviceStatus = ServiceStatus.ACTIVE,
            equipmentCondition = EquipmentCondition.LOAN
        ).apply { id = 1 }

        val marketingDefinition = WhatsAppTemplateCatalog.get(WhatsAppTemplateCode.PAYMENT_REMINDER).copy(
            category = WhatsAppTemplateCategory.MARKETING
        )
        val payment = com.dscorp.wispadmin.wispadmin.data.model.Payment(
            discountAmount = 0.0,
            paid = false,
            amountToPay = 79.9,
            billingDateDatetime = java.time.LocalDateTime.of(2026, 7, 1, 0, 0)
        ).apply { id = 10; this.subscription = subscription }

        `when`(marketingOptOutRepository.findByPhone("51987654321")).thenReturn(
            WhatsAppMarketingOptOut(
                id = 1,
                phone = "987654321",
                category = "marketing_messages",
                status = "OPTED_OUT"
            )
        )
        doAnswer { invocation ->
            invocation.getArgument(0)
        }.`when`(messageLogRepository).save(org.mockito.ArgumentMatchers.any(WhatsAppMessageLog::class.java))

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException::class.java) {
            service.deliverTemplate(
                definition = marketingDefinition,
                subscription = subscription,
                phone = "987654321",
                payment = payment,
                paymentId = 10
            )
        }

        val captor = ArgumentCaptor.forClass(WhatsAppMessageLog::class.java)
        verify(messageLogRepository).save(captor.capture())
        assertEquals(WhatsAppTemplateDeliveryService.STATUS_SKIPPED, captor.value.status)
        verify(whatsAppService, org.mockito.Mockito.never()).sendTemplateMessageWithMetaResponse(
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyList(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.nullable(WhatsAppTemplateButtonParameter::class.java)
        )
    }
}
