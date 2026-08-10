package com.dscorp.wispadmin.wispadmin.service.whatsapp

object WhatsAppQuickReplyCatalog {
    data class Seed(val title: String, val shortcut: String, val content: String)

    val defaults: List<Seed> = listOf(
        Seed(
            title = "Solución de avería",
            shortcut = "/averia",
            content = """
                ¡Hola! 👋 Soy Lisbeth, asesora de GigaFiber Perú.
                Te confirmo que tu inconveniente ya fue solucionado. ✅
                Por favor, verifica tu servicio y confírmame si ya está funcionando con normalidad. 😊
            """.trimIndent()
        ),
        Seed(
            title = "Validación de comprobante",
            shortcut = "/comprobante",
            content = """
                ¡Hola! 😊 Hemos revisado el comprobante de pago y figura a nombre de una persona distinta.

                Por favor, verifique que el pago haya sido realizado a uno de nuestros medios de pago autorizados:

                🏦 Cuenta BCP: 335-98410-54-0-22
                📲 Yape / Plin: 958 073 976
                👤 Titular: GIGAFIBERPERU

                Si realizó el pago a uno de estos medios, por favor reenvíenos el comprobante para poder validarlo y registrarlo correctamente. ✅
            """.trimIndent()
        ),
        Seed(
            title = "Saludo atención al cliente",
            shortcut = "/saludo",
            content = """
                ¡Hola! 👋 Soy Lisbeth, asesora de GigaFiber Perú.
                Será un gusto atenderte el día de hoy. 😊
                ¿En qué te puedo ayudar?
            """.trimIndent()
        ),
        Seed(
            title = "Confirmación de pago",
            shortcut = "/pago-confirmado",
            content = """
                ¡Listo! 😊 Su pago correspondiente a la factura del mes de julio ya fue registrado correctamente en nuestro sistema. ✅
                Muchas gracias por su pago y por mantenerse al día con GigaFiber Perú.
            """.trimIndent()
        ),
        Seed(
            title = "Compromiso de pago registrado",
            shortcut = "/compromiso-pago",
            content = """
                Se ha registrado correctamente su compromiso de pago. ✅
                Agradecemos su confirmación. Estaremos atentos a la fecha acordada para la regularización de su deuda.
                Gracias por comunicarse con GigaFiber Perú. 😊
            """.trimIndent()
        ),
        Seed(
            title = "Agendamiento de visita técnica",
            shortcut = "/agendar-visita",
            content = """
                Entendido. Para solucionar el problema de manera presencial, coordinaremos la visita de un técnico a tu domicilio. 🛠️

                Por favor, confírmanos:
                📍 Dirección exacta / Referencia:
                📅 Turno preferido: (Mañana: 9am - 1pm / Tarde: 2pm - 6pm)
                📞 Teléfono de contacto en casa:
            """.trimIndent()
        ),
        Seed(
            title = "Descarte básico de router (Reboot)",
            shortcut = "/descarte-basico",
            content = """
                Para ayudarte a restablecer la señal rápidamente, por favor realiza este descarte básico:

                1. Desconecta el cable de energía (fuente) del router principal por 30 segundos.
                2. Vuelve a conectarlo y espera unos 3 a 5 minutos a que las luces se estabilicen (especialmente la luz de PON / Internet).

                Por favor, avísame si la señal vuelve a la normalidad tras este proceso. 📶
            """.trimIndent()
        ),
        Seed(
            title = "Consulta de cobertura y planes",
            shortcut = "/planes-cobertura",
            content = """
                ¡Excelente decisión querer sumarte a la ultra velocidad de GigaFiber Perú! 🚀

                Para verificar la disponibilidad de Fibra Óptica en tu zona y brindarte nuestras mejores promociones, por favor envíanos:
                📍 Tu ubicación actual en tiempo real por WhatsApp o tu dirección completa con referencia. 😊
            """.trimIndent()
        ),
        Seed(
            title = "Solicitud de cambio de clave Wi-Fi",
            shortcut = "/cambio-clave",
            content = """
                Con gusto te ayudamos a actualizar la clave de tu Wi-Fi. 🔐

                Por favor, indícanos:
                1. Nombre deseado para la red Wi-Fi:
                2. Nueva contraseña: (mínimo 8 caracteres, entre letras y números)

                Una vez nos envíes estos datos, realizaremos la configuración remota de inmediato.
            """.trimIndent()
        ),
        Seed(
            title = "Aviso preventivo de corte",
            shortcut = "/aviso-corte",
            content = """
                Estimado(a) cliente, le recordamos que su recibo del servicio de internet se encuentra pendiente de pago. ⚠️

                Para evitar la suspensión temporal del servicio y el cobro de reconexión, le invitamos a regularizar su pago a la brevedad y enviarnos el comprobante por este medio.

                ¡Agradecemos su atención y preferencia! 🙌
            """.trimIndent()
        ),
        Seed(
            title = "Confirmación de reconexión",
            shortcut = "/reconexion",
            content = """
                ¡Gracias por enviar tu comprobante de pago! ✅

                Hemos activado la reconexión de tu servicio de internet. En un lapso de 5 a 15 minutos tu router volverá a contar con navegación normal.

                Por favor, reinicia tu equipo si no se restablece automáticamente. ¡Que tengas un excelente día! 😊
            """.trimIndent()
        ),
        Seed(
            title = "Cierre de atención por inactividad",
            shortcut = "/cierre-chat",
            content = """
                Dado que no hemos recibido respuesta en los últimos minutos, daremos por finalizada esta atención por el momento. ⏳

                Si aún necesitas ayuda, solo escríbenos nuevamente por este medio y con gusto te asistiremos. ¡Gracias por comunicarte con GigaFiber Perú! 👋
            """.trimIndent()
        )
    )
}
