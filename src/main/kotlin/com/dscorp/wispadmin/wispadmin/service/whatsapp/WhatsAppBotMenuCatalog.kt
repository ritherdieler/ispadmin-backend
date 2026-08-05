package com.dscorp.wispadmin.wispadmin.service.whatsapp

object WhatsAppBotMenuCatalog {
    const val REPORT_FAULT = "reportar_averia"
    const val DEBT = "ver_deuda"
    const val PAYMENT_PROOF = "enviar_comprobante"
    const val PAID = "ya_pague"
    const val ADVISOR = "hablar_asesor"
    const val INSTALLATION_LEGACY = "solicitud_instalacion"
    const val SUPPORT_LEGACY = "soporte"
    const val BACK = "nav_back"
    const val HOME = "nav_home"
    const val SUPPORT_ISSUE_PREFIX = "support_issue_"
    const val SUPPORT_DIAG_PREFIX = "support_diag_"
    const val GLOBAL_COMMANDS_FOOTER = "Escribe MENÚ o ASESOR en cualquier momento"
    const val PAYMENT_PROOF_FOOTER = "Puedes enviar tu comprobante como imagen o PDF"

    val mainMenu = listOf(
        WhatsAppInteractiveOption(REPORT_FAULT, "Reportar avería"),
        WhatsAppInteractiveOption(DEBT, "Consultar deuda"),
        WhatsAppInteractiveOption(PAYMENT_PROOF, "Registrar pago"),
        WhatsAppInteractiveOption(ADVISOR, "Hablar con asesor")
    )

    val mainMenuDescriptions = mapOf(
        REPORT_FAULT to "Internet o TV con problemas",
        DEBT to "Saldo pendiente y formas de pago",
        PAYMENT_PROOF to "Adjunta tu voucher (foto o PDF)",
        ADVISOR to "Te atiende una persona del equipo"
    )

    val debtMenu = listOf(
        WhatsAppInteractiveOption(PAID, "Ya pagué"),
        WhatsAppInteractiveOption(HOME, "Menú principal"),
        WhatsAppInteractiveOption(ADVISOR, "Hablar con asesor")
    )

    val paymentProofMenu = listOf(
        WhatsAppInteractiveOption(HOME, "Menú principal"),
        WhatsAppInteractiveOption(ADVISOR, "Hablar con asesor")
    )

    fun isSupportIssue(buttonId: String?): Boolean =
        buttonId.orEmpty().startsWith(SUPPORT_ISSUE_PREFIX)

    fun isSupportDiagnostic(buttonId: String?): Boolean =
        buttonId.orEmpty().startsWith(SUPPORT_DIAG_PREFIX)
}
