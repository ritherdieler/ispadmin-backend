package com.dscorp.wispadmin.wispadmin.service.whatsapp

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class WhatsAppInteractiveMessageValidatorTest {

    @Test
    fun `reply buttons accepts three unique user friendly options`() {
        assertDoesNotThrow {
            WhatsAppInteractiveMessageValidator.validateReplyButtons(
                bodyText = "¿En qué te podemos ayudar hoy?",
                footerText = "Escribe MENÚ o ASESOR en cualquier momento",
                buttons = listOf(
                    WhatsAppInteractiveOption("reportar_averia", "Reportar avería"),
                    WhatsAppInteractiveOption("ver_deuda", "Consultar deuda"),
                    WhatsAppInteractiveOption("hablar_asesor", "Hablar con asesor")
                )
            )
        }
    }

    @Test
    fun `reply buttons rejects more than three options instead of truncating`() {
        assertThrows(IllegalArgumentException::class.java) {
            WhatsAppInteractiveMessageValidator.validateReplyButtons(
                bodyText = "Selecciona una opción",
                footerText = null,
                buttons = listOf(
                    WhatsAppInteractiveOption("one", "Uno"),
                    WhatsAppInteractiveOption("two", "Dos"),
                    WhatsAppInteractiveOption("three", "Tres"),
                    WhatsAppInteractiveOption("four", "Cuatro")
                )
            )
        }
    }

    @Test
    fun `reply buttons rejects duplicate ids and labels`() {
        assertThrows(IllegalArgumentException::class.java) {
            WhatsAppInteractiveMessageValidator.validateReplyButtons(
                bodyText = "Selecciona una opción",
                footerText = null,
                buttons = listOf(
                    WhatsAppInteractiveOption("same", "Opción"),
                    WhatsAppInteractiveOption("same", "Opción")
                )
            )
        }
    }

    @Test
    fun `list validates meta row limits without silently truncating`() {
        assertThrows(IllegalArgumentException::class.java) {
            WhatsAppInteractiveMessageValidator.validateList(
                bodyText = "Selecciona una opción",
                buttonText = "Ver opciones",
                footerText = null,
                sections = listOf(
                    WhatsAppInteractiveSection(
                        title = "Opciones",
                        rows = (1..11).map {
                            WhatsAppInteractiveListOption("option_$it", "Opción $it")
                        }
                    )
                )
            )
        }
    }
}
