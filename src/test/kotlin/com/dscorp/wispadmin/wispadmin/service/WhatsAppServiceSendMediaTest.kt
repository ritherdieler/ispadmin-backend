package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.observability.tracing.TracingClientHttpRequestInterceptor
import com.dscorp.wispadmin.wispadmin.config.WhatsAppProperties
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppOutboundMediaKind
import io.mockk.mockk
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestTemplate

class WhatsAppServiceSendMediaTest {

    private lateinit var properties: WhatsAppProperties
    private lateinit var restTemplate: RestTemplate
    private lateinit var server: MockRestServiceServer
    private lateinit var service: WhatsAppService

    @BeforeEach
    fun setUp() {
        properties = WhatsAppProperties().apply {
            apiVersion = "v21.0"
            phoneNumberId = "phone-1"
            businessAccountId = "biz-1"
            accessToken = "token-1"
        }
        restTemplate = RestTemplate()
        server = MockRestServiceServer.createServer(restTemplate)
        service = WhatsAppService(
            whatsAppProperties = properties,
            tracingInterceptor = mockk(relaxed = true),
        )
        service.useRestTemplate(restTemplate)
    }

    @Test
    fun `sendMediaMessage audio returns HTTP 200 and posts payload without caption`() {
        server.expect(requestTo("https://graph.facebook.com/v21.0/phone-1/messages"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().string(containsString("\"type\":\"audio\"")))
            .andExpect(content().string(containsString("\"id\":\"meta-audio-9\"")))
            .andExpect(content().string(not(containsString("caption"))))
            .andRespond(
                withSuccess(
                    """{"messaging_product":"whatsapp","messages":[{"id":"wamid.audio.ok"}]}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        val result = service.sendMediaMessage(
            phoneNumber = "51987654321",
            kind = WhatsAppOutboundMediaKind.AUDIO,
            mediaId = "meta-audio-9",
            caption = "no debe enviarse",
            filename = "voice-note.ogg",
        )

        server.verify()
        assertTrue(result.success)
        assertEquals("wamid.audio.ok", result.metaMessageId)
    }
}
