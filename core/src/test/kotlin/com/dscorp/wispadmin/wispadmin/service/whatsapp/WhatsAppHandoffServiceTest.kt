package com.dscorp.wispadmin.wispadmin.service.whatsapp

import com.dscorp.wispadmin.wispadmin.config.WhatsAppHandoverProperties
import com.dscorp.wispadmin.wispadmin.config.WhatsAppProperties
import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppChatState
import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppChatStatus
import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppConversationStep
import com.dscorp.wispadmin.wispadmin.service.WhatsAppSendResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.ObjectProvider

class WhatsAppHandoffServiceTest {

    private val chatStateService = mockk<WhatsAppChatStateService>()
    private val metaApiService = mockk<MetaApiService>()
    private val crmConversationService = mockk<CrmConversationService>(relaxed = true)
    private val crmProvider = mockk<ObjectProvider<CrmConversationService>>()
    private val llmClient = mockk<LlmClient>(relaxed = true)

    private fun buildService(properties: WhatsAppProperties): WhatsAppHandoffService {
        every { crmProvider.ifAvailable } returns crmConversationService
        every { crmConversationService.recentMessagesForPhone(any(), any()) } returns listOf("Cliente: hola")
        every { llmClient.summarizeHandoff(any(), any(), any()) } returns "Resumen de prueba"
        return WhatsAppHandoffService(chatStateService, metaApiService, properties, crmProvider, llmClient)
    }

    @Test
    fun `pauseBotAndPassToAdvisor moves chat to waiting advisor without creating tickets`() = runBlocking {
        val phone = "51902354183"
        val reason = "support_diagnostic"
        val properties = WhatsAppProperties().apply {
            handover = WhatsAppHandoverProperties().apply {
                enabled = false
            }
        }
        coEvery { chatStateService.markWaitingForAdvisorAsync(phone, reason) } returns WhatsAppChatState(
            phone = phone,
            status = WhatsAppChatStatus.ESPERANDO_ASESOR,
            currentStep = WhatsAppConversationStep.ESPERANDO_ASESOR,
            botPaused = true,
            metadata = reason
        )

        val service = buildService(properties)
        val result = service.pauseBotAndPassToAdvisorAsync(phone, reason)

        assertTrue(result.botPaused)
        assertFalse(result.metaTransferred)
        coVerify(exactly = 1) { chatStateService.markWaitingForAdvisorAsync(phone, reason) }
        coVerify(exactly = 0) { metaApiService.passThreadControl(any(), any(), any()) }
    }

    @Test
    fun `pauseBotAndPassToAdvisor invokes Meta handover when enabled`() = runBlocking {
        val phone = "51902354183"
        val reason = "advisor_request"
        val properties = WhatsAppProperties().apply {
            handover = WhatsAppHandoverProperties().apply {
                enabled = true
                targetAppId = "123456"
            }
        }
        coEvery { chatStateService.markWaitingForAdvisorAsync(phone, reason) } returns WhatsAppChatState(
            phone = phone,
            status = WhatsAppChatStatus.ESPERANDO_ASESOR,
            currentStep = WhatsAppConversationStep.ESPERANDO_ASESOR,
            botPaused = true,
            metadata = reason
        )
        coEvery {
            metaApiService.passThreadControl(phone, "123456", "ESPERANDO_ASESOR:$reason")
        } returns WhatsAppSendResult(
            success = true,
            metaResponse = "{}",
            metaMessageId = null,
            recipient = phone,
            senderPhoneNumberId = "phone-number-id"
        )

        val service = buildService(properties)
        val result = service.pauseBotAndPassToAdvisorAsync(phone, reason)

        assertTrue(result.botPaused)
        assertTrue(result.metaTransferred)
        coVerify(exactly = 1) {
            metaApiService.passThreadControl(phone, "123456", "ESPERANDO_ASESOR:$reason")
        }
    }
}
