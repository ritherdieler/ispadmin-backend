package com.dscorp.wispadmin.wispadmin.service.whatsapp

object WhatsAppTemplateHumanFallback {

    fun render(definition: WhatsAppTemplateDefinition, parameters: List<NamedTemplateParameter>): String {
        val byName = parameters.associate { it.parameterName to it.text }
        return when (definition.code) {
            WhatsAppTemplateCode.PAYMENT_REMINDER ->
                "Estimado(a) ${value(byName, "customer_name")}, le recordamos su pago pendiente de S/ " +
                    "${value(byName, "amount")} correspondiente al periodo ${value(byName, "billing_period")}."

            WhatsAppTemplateCode.PAYMENT_VALIDATION ->
                "Estimado(a) ${value(byName, "customer_name")}, confirmamos el registro de su pago de S/ " +
                    "${value(byName, "amount")} con fecha ${value(byName, "payment_date")}."

            WhatsAppTemplateCode.SERVICE_CUT_NOTICE ->
                "Estimado(a) ${value(byName, "customer_name")}, su servicio presenta deuda de S/ " +
                    "${value(byName, "amount")} del periodo ${value(byName, "billing_period")}. " +
                    "Comuníquese con nosotros para regularizar."

            WhatsAppTemplateCode.WELCOME_CUSTOMER ->
                "Bienvenido(a) ${value(byName, "customer_name")}. Plan ${value(byName, "plan_name")} " +
                    "(${value(byName, "plan_price")}). ${value(byName, "service_title")}: " +
                    "${value(byName, "service_details")}. Día de pago: ${value(byName, "payment_day")}. " +
                    "${value(byName, "payment_info")}."
        }
    }

    fun findDefinitionByMetaName(metaName: String): WhatsAppTemplateDefinition? =
        WhatsAppTemplateCatalog.all().firstOrNull { it.metaName.equals(metaName, ignoreCase = true) }

    private fun value(map: Map<String, String>, key: String): String =
        map[key]?.trim().orEmpty().ifBlank { "—" }
}
