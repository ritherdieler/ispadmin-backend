package com.dscorp.wispadmin.wispadmin.service.whatsapp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class WhatsAppTemplateBodyRendererTest {

    private val renderer = WhatsAppTemplateBodyRenderer()

    @Test
    fun `render replaces named placeholders`() {
        val body = "Hola {{customer_name}}, tu monto es {{amount}}."
        val parameters = listOf(
            NamedTemplateParameter("customer_name", "Olivia Matias Paulino"),
            NamedTemplateParameter("amount", "50.0")
        )

        assertEquals(
            "Hola Olivia Matias Paulino, tu monto es 50.0.",
            renderer.render(body, parameters)
        )
    }

    @Test
    fun `parseLegacyPreview extracts meta name and parameters`() {
        val stored =
            "payment_reminder_gigaperu [customer_name=Olivia Matias Paulino, amount=50.0, billing_period=31/07/2026]"

        val parsed = renderer.parseLegacyPreview(stored)

        assertEquals("payment_reminder_gigaperu", parsed?.first)
        assertEquals("Olivia Matias Paulino", parsed?.second?.get("customer_name"))
        assertEquals("50.0", parsed?.second?.get("amount"))
        assertEquals("31/07/2026", parsed?.second?.get("billing_period"))
    }

    @Test
    fun `parseLegacyPreview returns null for non legacy text`() {
        assertNull(renderer.parseLegacyPreview("Hola, este es un mensaje normal."))
    }

    @Test
    fun `buildDisplayMessage renders legacy stored log using body template`() {
        val stored =
            "payment_reminder_gigaperu [customer_name=Olivia Matias Paulino, amount=50.0, billing_period=31/07/2026]"
        val bodyTemplate = "Estimado(a) {{customer_name}}, recuerde pagar {{amount}} antes del {{billing_period}}."

        val display = renderer.buildDisplayMessage(
            storedMessage = stored,
            messageType = "PAYMENT_REMINDER",
            bodyTextForMetaName = { metaName ->
                if (metaName == "payment_reminder_gigaperu") bodyTemplate else null
            }
        )

        assertEquals(
            "Estimado(a) Olivia Matias Paulino, recuerde pagar 50.0 antes del 31/07/2026.",
            display
        )
    }

    @Test
    fun `buildDisplayMessage uses human fallback when body sync missing`() {
        val stored =
            "payment_reminder_gigaperu [customer_name=Ana, amount=50.0, billing_period=01/08/2026]"

        val display = renderer.buildDisplayMessage(
            storedMessage = stored,
            messageType = "PAYMENT_REMINDER",
            bodyTextForMetaName = { null }
        )

        assertEquals(
            "Estimado(a) Ana, le recordamos su pago pendiente de S/ 50.0 correspondiente al periodo 01/08/2026.",
            display
        )
    }

    @Test
    fun `buildDisplayMessage returns stored message when not legacy`() {
        val stored = "Mensaje del operador"

        assertEquals(
            stored,
            renderer.buildDisplayMessage(
                storedMessage = stored,
                messageType = "OPERATOR_REPLY",
                bodyTextForMetaName = { null }
            )
        )
    }

    @Test
    fun `buildPreviewFromDefinition uses meta body template`() {
        val definition = WhatsAppTemplateCatalog.get(WhatsAppTemplateCode.PAYMENT_REMINDER)
        val parameters = listOf(
            NamedTemplateParameter("customer_name", "Ana"),
            NamedTemplateParameter("amount", "80"),
            NamedTemplateParameter("billing_period", "01/08/2026")
        )

        val preview = renderer.buildPreviewFromDefinition(
            definition = definition,
            parameters = parameters,
            bodyText = "Hola {{customer_name}}, pague {{amount}} ({{billing_period}})."
        )

        assertEquals("Hola Ana, pague 80 (01/08/2026).", preview)
    }
}
