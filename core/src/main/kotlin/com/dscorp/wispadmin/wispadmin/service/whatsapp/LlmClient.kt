package com.dscorp.wispadmin.wispadmin.service.whatsapp

import com.dscorp.wispadmin.wispadmin.config.CrmLlmProperties
import com.dscorp.wispadmin.wispadmin.dto.CrmOpenAiTestResultDto
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate

data class LlmIntentResult(
    val intent: WhatsAppInboundIntent,
    val confidence: Double
)

@Service
class LlmClient(
    private val settingsService: CrmOpenAiSettingsService,
    private val properties: CrmLlmProperties,
    private val objectMapper: ObjectMapper,
    @Qualifier("crmLlmRestTemplate") private val restTemplate: RestTemplate
) {
    private val log = LoggerFactory.getLogger(LlmClient::class.java)

    fun classifyIntent(messageText: String): LlmIntentResult? {
        val runtime = settingsService.resolveRuntimeConfig()
        if (!runtime.enabled || runtime.apiKey.isNullOrBlank()) return null
        val system = """
            Clasifica la intencion de un mensaje de cliente de ISP (fibra).
            Responde SOLO JSON: {"intent":"<ENUM>","confidence":0.0}
            ENUM valido: ACK, DEBT_INQUIRY, PAYMENT_CLAIM, TECHNICAL_ISSUE, SUPPORT, TICKET_STATUS, INSTALLATION_REQUEST, HUMAN_ESCALATION, GREETING, UNKNOWN
            confidence entre 0 y 1. No inventes datos. Si no estas seguro usa UNKNOWN con baja confidence.
        """.trimIndent()
        val content = chatCompletion(
            apiKey = runtime.apiKey,
            model = runtime.model,
            system = system,
            user = messageText.take(1000)
        ) ?: return null
        return parseIntent(content)
    }

    fun summarizeHandoff(phone: String, reason: String, recentMessages: List<String>): String {
        val fallback = buildString {
            append("Handoff ($reason) telefono=$phone.")
            if (recentMessages.isNotEmpty()) {
                append(" Ultimos mensajes: ")
                append(recentMessages.takeLast(6).joinToString(" | ").take(800))
            }
        }
        val runtime = settingsService.resolveRuntimeConfig()
        if (!runtime.enabled || runtime.apiKey.isNullOrBlank()) return fallback
        val system = """
            Eres asistente interno de un CRM ISP. Resume para un agente humano.
            Incluye: intencion probable, datos ya dados por el cliente, acciones del bot y motivo de handoff.
            No inventes datos ni expongas secretos. Maximo 120 palabras. Español.
        """.trimIndent()
        val user = buildString {
            appendLine("Motivo: $reason")
            appendLine("Telefono: $phone")
            appendLine("Mensajes:")
            recentMessages.takeLast(12).forEach { appendLine("- $it") }
        }
        return chatCompletion(runtime.apiKey, runtime.model, system, user)?.trim()?.take(2000) ?: fallback
    }

    fun suggestReply(recentMessages: List<String>, context: String): String? {
        val runtime = settingsService.resolveRuntimeConfig()
        if (!runtime.enabled || runtime.apiKey.isNullOrBlank()) return null
        val system = """
            Sugiere UNA respuesta breve para un agente humano de atencion ISP por WhatsApp.
            No inventes saldos, tickets ni datos no presentes en el contexto.
            No digas que eres una IA. Español. Maximo 80 palabras.
            Responde solo el texto sugerido.
        """.trimIndent()
        val user = buildString {
            appendLine("Contexto: ${context.take(800)}")
            appendLine("Mensajes:")
            recentMessages.takeLast(10).forEach { appendLine("- $it") }
        }
        return chatCompletion(runtime.apiKey, runtime.model, system, user)?.trim()?.take(1500)
    }

    fun testConnection(): CrmOpenAiTestResultDto {
        val runtime = settingsService.resolveRuntimeConfig()
        if (!runtime.enabled || runtime.apiKey.isNullOrBlank()) {
            return CrmOpenAiTestResultDto(
                success = false,
                message = "LLM deshabilitado o sin API key configurada",
                model = runtime.model
            )
        }
        return try {
            val content = chatCompletion(
                apiKey = runtime.apiKey,
                model = runtime.model,
                system = "Responde exactamente: ok",
                user = "ping"
            )
            if (content.isNullOrBlank()) {
                CrmOpenAiTestResultDto(false, "Sin respuesta de OpenAI", runtime.model)
            } else {
                CrmOpenAiTestResultDto(true, "Conexion OpenAI correcta", runtime.model)
            }
        } catch (e: Exception) {
            log.warn("OpenAI test falló: {}", e.message)
            CrmOpenAiTestResultDto(false, e.message?.take(300) ?: "Error de conexion", runtime.model)
        }
    }

    private fun chatCompletion(apiKey: String, model: String, system: String, user: String): String? {
        val url = "${properties.baseUrl.trimEnd('/')}/chat/completions"
        val payload = mapOf(
            "model" to model,
            "temperature" to 0,
            "messages" to listOf(
                mapOf("role" to "system", "content" to system),
                mapOf("role" to "user", "content" to user)
            )
        )
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            setBearerAuth(apiKey)
        }
        return try {
            val response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                HttpEntity(objectMapper.writeValueAsString(payload), headers),
                String::class.java
            )
            val body = response.body ?: return null
            val root: JsonNode = objectMapper.readTree(body)
            root.path("choices").firstOrNull()
                ?.path("message")
                ?.path("content")
                ?.asText()
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            log.warn("LlmClient chatCompletion error: {}", e.message)
            null
        }
    }

    private fun parseIntent(content: String): LlmIntentResult? {
        return try {
            val jsonStart = content.indexOf('{')
            val jsonEnd = content.lastIndexOf('}')
            if (jsonStart < 0 || jsonEnd <= jsonStart) return null
            val node = objectMapper.readTree(content.substring(jsonStart, jsonEnd + 1))
            val intentName = node.path("intent").asText("").trim().uppercase()
            val intent = WhatsAppInboundIntent.values().firstOrNull { it.name == intentName }
                ?: return null
            val confidence = node.path("confidence").asDouble(0.0).coerceIn(0.0, 1.0)
            LlmIntentResult(intent = intent, confidence = confidence)
        } catch (_: Exception) {
            null
        }
    }
}
