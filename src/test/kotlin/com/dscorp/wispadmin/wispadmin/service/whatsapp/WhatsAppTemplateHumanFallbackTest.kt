package com.dscorp.wispadmin.wispadmin.service.whatsapp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WhatsAppTemplateHumanFallbackTest {

    @Test
    fun `payment reminder fallback uses parameter values`() {
        val definition = WhatsAppTemplateCatalog.get(WhatsAppTemplateCode.PAYMENT_REMINDER)
        val parameters = listOf(
            NamedTemplateParameter("customer_name", "Ana Lopez"),
            NamedTemplateParameter("amount", "50.0"),
            NamedTemplateParameter("billing_period", "01/08/2026")
        )

        val text = WhatsAppTemplateHumanFallback.render(definition, parameters)

        assertEquals(
            "Estimado(a) Ana Lopez, le recordamos su pago pendiente de S/ 50.0 correspondiente al periodo 01/08/2026.",
            text
        )
    }

    @Test
    fun `payment validation fallback uses parameter values`() {
        val definition = WhatsAppTemplateCatalog.get(WhatsAppTemplateCode.PAYMENT_VALIDATION)
        val parameters = listOf(
            NamedTemplateParameter("customer_name", "Ana"),
            NamedTemplateParameter("amount", "50.0"),
            NamedTemplateParameter("payment_date", "03/08/2026")
        )

        val text = WhatsAppTemplateHumanFallback.render(definition, parameters)

        assertEquals(
            "Estimado(a) Ana, confirmamos el registro de su pago de S/ 50.0 con fecha 03/08/2026.",
            text
        )
    }
}
