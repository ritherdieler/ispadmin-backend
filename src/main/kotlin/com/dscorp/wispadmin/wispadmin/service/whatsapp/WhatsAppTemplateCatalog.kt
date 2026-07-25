package com.dscorp.wispadmin.wispadmin.service.whatsapp

enum class WhatsAppTemplateCode {
    PAYMENT_REMINDER,
    PAYMENT_VALIDATION,
    SERVICE_CUT_NOTICE,
    WELCOME_CUSTOMER
}

enum class WhatsAppTargetType {
    PAYMENT,
    SUBSCRIPTION
}

enum class TemplateParameterSource {
    CLIENT_NAME,
    AMOUNT_TO_PAY,
    AMOUNT_PAID,
    BILLING_PERIOD,
    PAYMENT_DATE,
    OLDEST_UNPAID_AMOUNT,
    OLDEST_UNPAID_BILLING_PERIOD,
    SERVICE_TITLE,
    SERVICE_DETAILS,
    PLAN_NAME,
    PLAN_PRICE,
    PAYMENT_DAY,
    PAYMENT_INFO
}

data class WhatsAppTemplateParameterDef(
    val metaParameterName: String,
    val source: TemplateParameterSource
)

data class WhatsAppTemplateDefinition(
    val code: WhatsAppTemplateCode,
    val metaName: String,
    val language: String = "es_PE",
    val label: String,
    val description: String,
    val targetType: WhatsAppTargetType,
    val parameters: List<WhatsAppTemplateParameterDef>
) {
    val messageType: String get() = code.name
}

object WhatsAppTemplateCatalog {

    private val templates = listOf(
        WhatsAppTemplateDefinition(
            code = WhatsAppTemplateCode.PAYMENT_REMINDER,
            metaName = "payment_reminder_gigaperu",
            label = "Recordatorio de pago",
            description = "Facturas impagas con telefono valido",
            targetType = WhatsAppTargetType.PAYMENT,
            parameters = listOf(
                WhatsAppTemplateParameterDef("customer_name", TemplateParameterSource.CLIENT_NAME),
                WhatsAppTemplateParameterDef("amount", TemplateParameterSource.AMOUNT_TO_PAY),
                WhatsAppTemplateParameterDef("billing_period", TemplateParameterSource.BILLING_PERIOD)
            )
        ),
        WhatsAppTemplateDefinition(
            code = WhatsAppTemplateCode.PAYMENT_VALIDATION,
            metaName = "payment_validation_gigaperu",
            label = "Validacion de pago",
            description = "Pagos registrados recientemente",
            targetType = WhatsAppTargetType.PAYMENT,
            parameters = listOf(
                WhatsAppTemplateParameterDef("customer_name", TemplateParameterSource.CLIENT_NAME),
                WhatsAppTemplateParameterDef("amount", TemplateParameterSource.AMOUNT_PAID),
                WhatsAppTemplateParameterDef("payment_date", TemplateParameterSource.PAYMENT_DATE)
            )
        ),
        WhatsAppTemplateDefinition(
            code = WhatsAppTemplateCode.SERVICE_CUT_NOTICE,
            metaName = "service_cut_notice_gigaperu",
            label = "Aviso de corte",
            description = "Clientes con servicio cortado",
            targetType = WhatsAppTargetType.SUBSCRIPTION,
            parameters = listOf(
                WhatsAppTemplateParameterDef("customer_name", TemplateParameterSource.CLIENT_NAME),
                WhatsAppTemplateParameterDef("amount", TemplateParameterSource.OLDEST_UNPAID_AMOUNT),
                WhatsAppTemplateParameterDef("billing_period", TemplateParameterSource.OLDEST_UNPAID_BILLING_PERIOD)
            )
        ),
        WhatsAppTemplateDefinition(
            code = WhatsAppTemplateCode.WELCOME_CUSTOMER,
            metaName = "welcome_customer_gigaperu",
            label = "Bienvenida",
            description = "Clientes activos con instalacion reciente",
            targetType = WhatsAppTargetType.SUBSCRIPTION,
            parameters = listOf(
                WhatsAppTemplateParameterDef("customer_name", TemplateParameterSource.CLIENT_NAME),
                WhatsAppTemplateParameterDef("service_title", TemplateParameterSource.SERVICE_TITLE),
                WhatsAppTemplateParameterDef("service_details", TemplateParameterSource.SERVICE_DETAILS),
                WhatsAppTemplateParameterDef("plan_name", TemplateParameterSource.PLAN_NAME),
                WhatsAppTemplateParameterDef("plan_price", TemplateParameterSource.PLAN_PRICE),
                WhatsAppTemplateParameterDef("payment_day", TemplateParameterSource.PAYMENT_DAY),
                WhatsAppTemplateParameterDef("payment_info", TemplateParameterSource.PAYMENT_INFO)
            )
        )
    )

    private val byCode = templates.associateBy { it.code }

    fun all(): List<WhatsAppTemplateDefinition> = templates

    fun get(code: WhatsAppTemplateCode): WhatsAppTemplateDefinition =
        byCode[code] ?: throw IllegalArgumentException("Plantilla WhatsApp no registrada: $code")

    fun getByCodeString(code: String): WhatsAppTemplateDefinition {
        val parsed = runCatching { WhatsAppTemplateCode.valueOf(code.trim()) }
            .getOrElse { throw IllegalArgumentException("Codigo de plantilla invalido: $code") }
        return get(parsed)
    }
}
