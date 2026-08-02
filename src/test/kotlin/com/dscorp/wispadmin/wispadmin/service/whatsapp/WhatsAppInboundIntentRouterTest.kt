package com.dscorp.wispadmin.wispadmin.service.whatsapp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WhatsAppInboundIntentRouterTest {

    private val router = WhatsAppInboundIntentRouter()

    @Test
    fun `routes ok and gracias as ACK`() {
        assertEquals(WhatsAppInboundIntent.ACK, router.route("ok"))
        assertEquals(WhatsAppInboundIntent.ACK, router.route("OK"))
        assertEquals(WhatsAppInboundIntent.ACK, router.route("gracias"))
        assertEquals(WhatsAppInboundIntent.ACK, router.route("listo"))
    }

    @Test
    fun `routes debt inquiry phrases`() {
        assertEquals(WhatsAppInboundIntent.DEBT_INQUIRY, router.route("cuanto debo"))
        assertEquals(WhatsAppInboundIntent.DEBT_INQUIRY, router.route("¿Cuánto debo?"))
        assertEquals(WhatsAppInboundIntent.DEBT_INQUIRY, router.route("ver deuda"))
        assertEquals(WhatsAppInboundIntent.DEBT_INQUIRY, router.route("mi factura pendiente"))
    }

    @Test
    fun `routes payment claim phrases`() {
        assertEquals(WhatsAppInboundIntent.PAYMENT_CLAIM, router.route("ya pague"))
        assertEquals(WhatsAppInboundIntent.PAYMENT_CLAIM, router.route("ya pagué"))
        assertEquals(WhatsAppInboundIntent.PAYMENT_CLAIM, router.route("pago hecho"))
    }

    @Test
    fun `routes technical issue before generic support`() {
        assertEquals(WhatsAppInboundIntent.TECHNICAL_ISSUE, router.route("tengo el internet lento"))
        assertEquals(WhatsAppInboundIntent.TECHNICAL_ISSUE, router.route("sin internet"))
        assertEquals(WhatsAppInboundIntent.TECHNICAL_ISSUE, router.route("averia"))
        assertEquals(WhatsAppInboundIntent.TECHNICAL_ISSUE, router.route("pantalla negra sin señal"))
        assertEquals(WhatsAppInboundIntent.SUPPORT, router.route("soporte"))
        assertEquals(WhatsAppInboundIntent.SUPPORT, router.route("necesito ayuda"))
    }

    @Test
    fun `routes frustration to human escalation`() {
        assertEquals(WhatsAppInboundIntent.HUMAN_ESCALATION, router.route("pesimo servicio"))
        assertEquals(WhatsAppInboundIntent.HUMAN_ESCALATION, router.route("quiero atencion humana"))
        assertEquals(WhatsAppInboundIntent.HUMAN_ESCALATION, router.route("esto no sirve"))
        assertEquals(WhatsAppInboundIntent.HUMAN_ESCALATION, router.route("quiero hablar con una persona"))
        assertEquals(WhatsAppInboundIntent.HUMAN_ESCALATION, router.route("pasame con un agente"))
        assertEquals(WhatsAppInboundIntent.HUMAN_ESCALATION, router.route("estoy molesto"))
    }

    @Test
    fun `routes greeting and unknown`() {
        assertEquals(WhatsAppInboundIntent.GREETING, router.route("hola"))
        assertEquals(WhatsAppInboundIntent.GREETING, router.route("buenos dias"))
        assertEquals(WhatsAppInboundIntent.UNKNOWN, router.route("asdfqwerty"))
        assertEquals(WhatsAppInboundIntent.UNKNOWN, router.route(null))
        assertEquals(WhatsAppInboundIntent.UNKNOWN, router.route("   "))
    }

    @Test
    fun `payment claim wins over ack when combined`() {
        assertEquals(WhatsAppInboundIntent.PAYMENT_CLAIM, router.route("ya pague gracias"))
    }

    @Test
    fun `routes ticket status inquiry phrases`() {
        assertEquals(WhatsAppInboundIntent.TICKET_STATUS, router.route("estado de mi ticket"))
        assertEquals(WhatsAppInboundIntent.TICKET_STATUS, router.route("como va mi ticket"))
        assertEquals(WhatsAppInboundIntent.TICKET_STATUS, router.route("numero de ticket"))
        assertEquals(WhatsAppInboundIntent.TICKET_STATUS, router.route("consultar ticket"))
    }
}
