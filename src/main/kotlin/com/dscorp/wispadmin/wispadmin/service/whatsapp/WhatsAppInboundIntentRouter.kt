package com.dscorp.wispadmin.wispadmin.service.whatsapp

import org.springframework.stereotype.Component
import java.text.Normalizer

enum class WhatsAppInboundIntent {
    ACK,
    DEBT_INQUIRY,
    PAYMENT_CLAIM,
    TECHNICAL_ISSUE,
    SUPPORT,
    TICKET_STATUS,
    INSTALLATION_REQUEST,
    HUMAN_ESCALATION,
    GREETING,
    UNKNOWN
}

/**
 * Deterministic keyword router for inbound WhatsApp text.
 * Priority: payment claim > technical issue > debt > support > ack > greeting > unknown.
 */
@Component
class WhatsAppInboundIntentRouter {

    fun route(messageText: String?): WhatsAppInboundIntent {
        val normalized = normalize(messageText) ?: return WhatsAppInboundIntent.UNKNOWN

        return when {
            matchesAny(normalized, HUMAN_ESCALATION_PATTERNS) -> WhatsAppInboundIntent.HUMAN_ESCALATION
            matchesAny(normalized, TICKET_STATUS_PATTERNS) -> WhatsAppInboundIntent.TICKET_STATUS
            matchesAny(normalized, PAYMENT_CLAIM_PATTERNS) -> WhatsAppInboundIntent.PAYMENT_CLAIM
            matchesAny(normalized, TECHNICAL_ISSUE_PATTERNS) -> WhatsAppInboundIntent.TECHNICAL_ISSUE
            matchesAny(normalized, DEBT_PATTERNS) -> WhatsAppInboundIntent.DEBT_INQUIRY
            matchesAny(normalized, INSTALLATION_PATTERNS) -> WhatsAppInboundIntent.INSTALLATION_REQUEST
            matchesAny(normalized, SUPPORT_PATTERNS) -> WhatsAppInboundIntent.SUPPORT
            matchesAny(normalized, ACK_PATTERNS) -> WhatsAppInboundIntent.ACK
            matchesAny(normalized, GREETING_PATTERNS) -> WhatsAppInboundIntent.GREETING
            else -> WhatsAppInboundIntent.UNKNOWN
        }
    }

    fun isTechnicalCategory(messageText: String?): Boolean {
        val normalized = normalize(messageText) ?: return false
        return matchesAny(normalized, TECHNICAL_ISSUE_PATTERNS)
    }

    companion object {
        private val ACK_PATTERNS = listOf(
            Regex("""^ok$"""),
            Regex("""^okay$"""),
            Regex("""^vale$"""),
            Regex("""\bgracias\b"""),
            Regex("""\blisto\b"""),
            Regex("""\bperfecto\b"""),
            Regex("""\bentendido\b""")
        )

        private val DEBT_PATTERNS = listOf(
            Regex("""\bdeuda\b"""),
            Regex("""\bcuanto\s+debo\b"""),
            Regex("""\bfactura\b"""),
            Regex("""\bpendiente\b"""),
            Regex("""\bmonto\b"""),
            Regex("""\bver\s+deuda\b""")
        )

        private val PAYMENT_CLAIM_PATTERNS = listOf(
            Regex("""\bya\s+pague\b"""),
            Regex("""\bpago\s+hecho\b"""),
            Regex("""\bya\s+pague\b"""),
            Regex("""\brealiz(?:e|o)\s+el\s+pago\b"""),
            Regex("""\bpague\b""")
        )

        private val TECHNICAL_ISSUE_PATTERNS = listOf(
            Regex("""\binternet\s+lento\b"""),
            Regex("""\blento\b"""),
            Regex("""\bsin\s+internet\b"""),
            Regex("""\bsin\s+senal\b"""),
            Regex("""\bpantalla\s+negra\b"""),
            Regex("""\bcanales?\s+congelad[oa]s?\b"""),
            Regex("""\binterferencia\b"""),
            Regex("""\bdecodificador\b"""),
            Regex("""\bwifi\b"""),
            Regex("""\bwi\s*fi\b"""),
            Regex("""\bred\s+wifi\b"""),
            Regex("""\bsin\s+conexion\b"""),
            Regex("""\baveria\b"""),
            Regex("""\bcortad[oa]\b"""),
            Regex("""\bno\s+funciona\b"""),
            Regex("""\bno\s+tengo\s+internet\b""")
        )

        private val SUPPORT_PATTERNS = listOf(
            Regex("""\bsoporte\b"""),
            Regex("""\bayuda\b"""),
            Regex("""\basesor\b""")
        )

        private val TICKET_STATUS_PATTERNS = listOf(
            Regex("""\bestado\s+de\s+mi\s+ticket\b"""),
            Regex("""\bcomo\s+va\s+mi\s+ticket\b"""),
            Regex("""\bnumero\s+de\s+ticket\b"""),
            Regex("""\bconsultar\s+ticket\b"""),
            Regex("""\bmi\s+ticket\b"""),
            Regex("""\bticket\s+#?\d+\b""")
        )

        private val INSTALLATION_PATTERNS = listOf(
            Regex("""\binstalacion\b"""),
            Regex("""\binstalar\b"""),
            Regex("""\bnueva\s+instalacion\b"""),
            Regex("""\btraslado\b"""),
            Regex("""\bcambio\s+de\s+domicilio\b""")
        )

        private val HUMAN_ESCALATION_PATTERNS = listOf(
            Regex("""\bno\s+sirve\b"""),
            Regex("""\bpesim[oa]\b"""),
            Regex("""\bmal\s+servicio\b"""),
            Regex("""\bmala\s+atencion\b"""),
            Regex("""\batencion\b"""),
            Regex("""\basesor(?:a|es)?\b"""),
            Regex("""\boperador\b"""),
            Regex("""\bhumano\b"""),
            Regex("""\bpersona\b"""),
            Regex("""\bagente\b"""),
            Regex("""\bmolest[oa]\b"""),
            Regex("""\breclamo\b""")
        )

        private val GREETING_PATTERNS = listOf(
            Regex("""^hola\b"""),
            Regex("""\bbuenos\s+dias\b"""),
            Regex("""\bbuenas\s+tardes\b"""),
            Regex("""\bbuenas\s+noches\b"""),
            Regex("""^buenas\b""")
        )

        @Suppress("UNREACHABLE_CODE")
        fun normalize(messageText: String?): String? {
            if (messageText.isNullOrBlank()) return null
            val withoutAccents = Normalizer.normalize(messageText.lowercase(), Normalizer.Form.NFD)
                .replace(Regex("\\p{M}+"), "")
            return withoutAccents
                .replace(Regex("""[Â¿?Â¡!.,;:]"""), "")
                .replace(Regex("""\s+"""), " ")
                .trim()
                .ifBlank { null }
            return messageText
                .lowercase()
                .replace('á', 'a')
                .replace('é', 'e')
                .replace('í', 'i')
                .replace('ó', 'o')
                .replace('ú', 'u')
                .replace('ü', 'u')
                .replace('ñ', 'n')
                .replace(Regex("""[¿?¡!.,;:]"""), "")
                .replace(Regex("""\s+"""), " ")
                .trim()
                .ifBlank { null }
        }

        private fun matchesAny(text: String, patterns: List<Regex>): Boolean {
            return patterns.any { it.containsMatchIn(text) }
        }
    }
}
