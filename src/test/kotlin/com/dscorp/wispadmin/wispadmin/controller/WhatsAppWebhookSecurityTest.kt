package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.config.WhatsAppProperties
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppMessageLogRepository
import com.dscorp.wispadmin.wispadmin.repository.ErrorLogRepository
import com.dscorp.wispadmin.wispadmin.logging.LoggingService
import com.dscorp.wispadmin.wispadmin.security.SecurityConfig
import com.dscorp.wispadmin.wispadmin.service.WhatsAppService
import com.dscorp.wispadmin.wispadmin.service.WhatsAppWebhookSignatureValidator
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(WhatsAppController::class)
@Import(SecurityConfig::class)
class WhatsAppWebhookSecurityTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var whatsAppService: WhatsAppService

    @MockBean
    private lateinit var whatsAppMessageLogRepository: WhatsAppMessageLogRepository

    @MockBean
    private lateinit var whatsAppProperties: WhatsAppProperties

    @MockBean
    private lateinit var webhookSignatureValidator: WhatsAppWebhookSignatureValidator

    @MockBean
    private lateinit var loggingService: LoggingService

    @MockBean
    private lateinit var errorLogRepository: ErrorLogRepository

    @Test
    fun `POST webhook without CSRF token is allowed and returns 200`() {
        `when`(webhookSignatureValidator.isValid(anyString(), any())).thenReturn(true)

        mockMvc.perform(
            post("/whatsapp/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .header("User-Agent", "facebookexternalua")
                .content("""{"object":"whatsapp_business_account","entry":[]}""")
        )
            .andExpect(status().isOk)
            .andExpect(content().string("EVENT_RECEIVED"))
    }

    @Test
    fun `GET webhook verification returns challenge when token matches`() {
        `when`(whatsAppProperties.webhookVerifyToken).thenReturn("test_verify_token")

        mockMvc.perform(
            get("/whatsapp/webhook")
                .param("hub.mode", "subscribe")
                .param("hub.verify_token", "test_verify_token")
                .param("hub.challenge", "12345")
        )
            .andExpect(status().isOk)
            .andExpect(content().string("12345"))
    }
}
