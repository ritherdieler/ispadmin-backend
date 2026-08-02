package com.dscorp.wispadmin.wispadmin.service.whatsapp

import com.dscorp.wispadmin.wispadmin.config.WhatsAppProperties
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

data class WhatsAppHandoffResult(
    val botPaused: Boolean,
    val metaTransferred: Boolean,
    val warning: String? = null
)

@Service
class WhatsAppHandoffService(
    private val chatStateService: WhatsAppChatStateService,
    private val metaApiService: MetaApiService,
    private val whatsAppProperties: WhatsAppProperties
) {

    private val log = LoggerFactory.getLogger(WhatsAppHandoffService::class.java)

    fun pauseBotAndPassToAdvisor(phone: String, reason: String): WhatsAppHandoffResult = runBlocking {
        pauseBotAndPassToAdvisorAsync(phone, reason)
    }

    suspend fun pauseBotAndPassToAdvisorAsync(phone: String, reason: String): WhatsAppHandoffResult {
        chatStateService.markWaitingForAdvisorAsync(phone, reason)

        if (!whatsAppProperties.handover.enabled) {
            return WhatsAppHandoffResult(
                botPaused = true,
                metaTransferred = false,
                warning = "Handover Meta desactivado por configuracion."
            )
        }

        val targetAppId = whatsAppProperties.handover.targetAppId.trim()
        if (targetAppId.isBlank()) {
            return WhatsAppHandoffResult(
                botPaused = true,
                metaTransferred = false,
                warning = "Handover Meta sin targetAppId configurado."
            )
        }

        return try {
            val metadata = "ESPERANDO_ASESOR:$reason"
            val result = metaApiService.passThreadControl(phone, targetAppId, metadata)
            WhatsAppHandoffResult(botPaused = true, metaTransferred = result.success)
        } catch (e: Exception) {
            log.warn("WhatsApp handoff: no se pudo transferir hilo Meta para {}: {}", phone, e.message)
            WhatsAppHandoffResult(
                botPaused = true,
                metaTransferred = false,
                warning = e.message?.take(500)
            )
        }
    }

    fun resumeBotAndTakeControl(phone: String, reason: String): WhatsAppHandoffResult = runBlocking {
        resumeBotAndTakeControlAsync(phone, reason)
    }

    suspend fun resumeBotAndTakeControlAsync(phone: String, reason: String): WhatsAppHandoffResult {
        chatStateService.resumeBotAsync(phone, reason)

        if (!whatsAppProperties.handover.enabled) {
            return WhatsAppHandoffResult(
                botPaused = false,
                metaTransferred = false,
                warning = "Handover Meta desactivado por configuracion."
            )
        }

        return try {
            val result = metaApiService.takeThreadControl(phone, "BOT_ACTIVE:$reason")
            WhatsAppHandoffResult(botPaused = false, metaTransferred = result.success)
        } catch (e: Exception) {
            log.warn("WhatsApp handoff: no se pudo recuperar hilo Meta para {}: {}", phone, e.message)
            WhatsAppHandoffResult(
                botPaused = false,
                metaTransferred = false,
                warning = e.message?.take(500)
            )
        }
    }
}
