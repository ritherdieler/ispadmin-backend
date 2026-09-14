package com.dscorp.wispadmin.wispadmin.service.whatsapp

import com.dscorp.wispadmin.wispadmin.data.model.Payment
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import java.time.format.DateTimeFormatter

data class NamedTemplateParameter(
    val parameterName: String,
    val text: String
)

data class WhatsAppTemplateButtonParameter(
    val subType: String = "url",
    val index: Int = 0,
    val parameter: NamedTemplateParameter
)

object TemplateParameterResolver {

    private val DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    fun resolve(
        definition: WhatsAppTemplateDefinition,
        subscription: Subscription,
        payment: Payment? = null,
        oldestUnpaidPayment: Payment? = null,
        welcomeContext: WelcomeTemplateContext? = null,
        unpaidAggregate: UnpaidInvoiceAggregate? = null
    ): List<NamedTemplateParameter> {
        return definition.parameters.map { param ->
            NamedTemplateParameter(
                parameterName = param.metaParameterName,
                text = resolveValue(
                    param.source,
                    subscription,
                    payment,
                    oldestUnpaidPayment,
                    welcomeContext,
                    unpaidAggregate
                )
            )
        }
    }

    private fun resolveValue(
        source: TemplateParameterSource,
        subscription: Subscription,
        payment: Payment?,
        oldestUnpaidPayment: Payment?,
        welcomeContext: WelcomeTemplateContext?,
        unpaidAggregate: UnpaidInvoiceAggregate?
    ): String {
        return when (source) {
            TemplateParameterSource.CLIENT_NAME -> subscription.getFullName()
            TemplateParameterSource.AMOUNT_TO_PAY ->
                unpaidAggregate?.totalAmount?.toString()
                    ?: requirePayment(payment).amountToPay.toString()
            TemplateParameterSource.AMOUNT_PAID -> {
                val p = requirePayment(payment)
                (p.amountPaid ?: p.amountToPay).toString()
            }
            TemplateParameterSource.BILLING_PERIOD ->
                unpaidAggregate?.periodSummary()
                    ?: requirePayment(payment).billingDateDatetime.format(DATE_FORMAT)
            TemplateParameterSource.PAYMENT_DATE -> {
                val date = requirePayment(payment).paymentDateDatetime
                    ?: throw IllegalArgumentException("La factura no tiene fecha de pago registrada.")
                date.format(DATE_FORMAT)
            }
            TemplateParameterSource.OLDEST_UNPAID_AMOUNT ->
                requirePayment(oldestUnpaidPayment).amountToPay.toString()
            TemplateParameterSource.OLDEST_UNPAID_BILLING_PERIOD ->
                requirePayment(oldestUnpaidPayment).billingDateDatetime.format(DATE_FORMAT)
            TemplateParameterSource.SERVICE_TITLE ->
                requireWelcomeContext(welcomeContext).serviceTitle
            TemplateParameterSource.SERVICE_DETAILS ->
                requireWelcomeContext(welcomeContext).serviceDetails
            TemplateParameterSource.PLAN_NAME ->
                requireWelcomeContext(welcomeContext).planName
            TemplateParameterSource.PLAN_PRICE ->
                requireWelcomeContext(welcomeContext).planPrice
            TemplateParameterSource.PAYMENT_DAY ->
                requireWelcomeContext(welcomeContext).paymentDay
            TemplateParameterSource.PAYMENT_INFO ->
                requireWelcomeContext(welcomeContext).paymentInfo
            TemplateParameterSource.PAYMENT_ID ->
                requirePayment(payment).id?.toString()
                    ?: throw IllegalArgumentException("La factura no tiene id valido para el boton de plantilla.")
        }
    }

    fun resolveButtonParameter(
        definition: WhatsAppTemplateDefinition,
        subscription: Subscription,
        payment: Payment? = null,
        oldestUnpaidPayment: Payment? = null,
        welcomeContext: WelcomeTemplateContext? = null,
        unpaidAggregate: UnpaidInvoiceAggregate? = null
    ): NamedTemplateParameter? {
        val buttonDef = definition.buttonParameter ?: return null
        return NamedTemplateParameter(
            parameterName = "button_${buttonDef.index}",
            text = resolveValue(
                buttonDef.source,
                subscription,
                payment,
                oldestUnpaidPayment,
                welcomeContext,
                unpaidAggregate
            )
        )
    }

    private fun requireWelcomeContext(welcomeContext: WelcomeTemplateContext?): WelcomeTemplateContext {
        return welcomeContext
            ?: throw IllegalArgumentException("No se encontro el contexto de bienvenida requerido para la plantilla.")
    }

    private fun requirePayment(payment: Payment?): Payment {
        return payment ?: throw IllegalArgumentException("No se encontro la factura requerida para la plantilla.")
    }
}
