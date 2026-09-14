package com.dscorp.wispadmin.wispadmin.service.whatsapp

/**
 * Builds human-readable AssistanceTicket descriptions from WhatsApp bot diagnostic codes.
 * Keeps technical button ids / issue enums out of technician-facing text.
 */
object WhatsAppTicketDescriptionFormatter {

    fun buildHumanReadableTicketDescription(
        issueCode: String?,
        buttonReplyId: String?,
    ): String {
        val problem = humanizeIssue(issueCode)
        val diagnostic = humanizeDiagnostic(buttonReplyId)
        return buildString {
            appendLine("Reportado desde WhatsApp:")
            appendLine("- Problema: $problem")
            appendLine("- Diagnóstico preliminar: $diagnostic")
            append("- Origen: Automatización Bot de WhatsApp")
        }
    }

    private fun humanizeIssue(issueCode: String?): String {
        return when (issueCode?.trim()?.uppercase()) {
            "NO_INTERNET" ->
                "El cliente reporta que se encuentra completamente sin servicio de internet."
            "SLOW_INTERNET" ->
                "El cliente reporta lentitud en la velocidad de navegación."
            "INTERMITTENT",
            "INTERMITTENT_INTERNET",
            "INTERNET_INTERRUPTION" ->
                "El cliente reporta intermitencia o caídas constantes en el servicio."
            "WIFI_NOT_VISIBLE" ->
                "El cliente reporta que no visualiza la red Wi-Fi de su equipo."
            "BOTH_SERVICES" ->
                "El cliente reporta fallas en internet y TV cable al mismo tiempo."
            "CABLE_INTERRUPTION",
            "TV_NO_SIGNAL" ->
                "El cliente reporta que no tiene señal de televisión."
            "TV_INTERFERENCE" ->
                "El cliente reporta imagen congelada o interferencia en TV."
            "DECODER_ERROR" ->
                "El cliente reporta un error en el decodificador de TV."
            null, "" ->
                "El cliente reporta un problema técnico con su servicio."
            else ->
                "El cliente reporta un problema técnico con su servicio."
        }
    }

    private fun humanizeDiagnostic(buttonReplyId: String?): String {
        val suffix = buttonReplyId
            ?.substringAfterLast('_')
            ?.trim()
            ?.lowercase()
            .orEmpty()

        return when (suffix) {
            "red" ->
                "Estado del equipo: Luz roja en el módem/ONT (Posible corte de fibra óptica o pérdida de señal)."
            "green" ->
                "Estado del equipo: Luces normales/verdes (Posible problema de configuración o red externa)."
            "off" ->
                "Estado del equipo: Sin luz en el indicador del módem/ONT."
            "fixed" ->
                "Estado del equipo: Luz ONLINE/INTERNET fija (posible problema más allá del módem)."
            "blink" ->
                "Estado del equipo: Luz ONLINE/INTERNET apagada o parpadeando."
            "black" ->
                "Estado del equipo: Pantalla negra en el televisor."
            "error" ->
                "Estado del equipo: Código o mensaje de error en el televisor."
            else ->
                "Diagnóstico inicial registrado por el bot."
        }
    }
}
