package com.dscorp.wispadmin.wispadmin.service.whatsapp

import com.dscorp.wispadmin.wispadmin.data.model.Payment
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.data.model.EquipmentCondition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class WhatsAppTemplateCatalogTest {

    @Test
    fun `all templates are registered with expected meta names`() {
        val templates = WhatsAppTemplateCatalog.all()

        assertEquals(4, templates.size)
        assertEquals("payment_reminder_gigaperu", WhatsAppTemplateCatalog.get(WhatsAppTemplateCode.PAYMENT_REMINDER).metaName)
        assertEquals("payment_validation_gigaperu", WhatsAppTemplateCatalog.get(WhatsAppTemplateCode.PAYMENT_VALIDATION).metaName)
        assertEquals("service_cut_notice_gigaperu", WhatsAppTemplateCatalog.get(WhatsAppTemplateCode.SERVICE_CUT_NOTICE).metaName)
        assertEquals("welcome_customer_gigaperu", WhatsAppTemplateCatalog.get(WhatsAppTemplateCode.WELCOME_CUSTOMER).metaName)
    }

    @Test
    fun `payment reminder parameters follow Meta order`() {
        val definition = WhatsAppTemplateCatalog.get(WhatsAppTemplateCode.PAYMENT_REMINDER)

        assertEquals(
            listOf("customer_name", "amount", "billing_period"),
            definition.parameters.map { it.metaParameterName }
        )
    }

    @Test
    fun `payment validation parameters follow Meta order`() {
        val definition = WhatsAppTemplateCatalog.get(WhatsAppTemplateCode.PAYMENT_VALIDATION)

        assertEquals(
            listOf("customer_name", "amount", "payment_date"),
            definition.parameters.map { it.metaParameterName }
        )
    }

    @Test
    fun `welcome template parameters follow Meta order`() {
        val definition = WhatsAppTemplateCatalog.get(WhatsAppTemplateCode.WELCOME_CUSTOMER)

        assertEquals(
            listOf(
                "customer_name",
                "service_title",
                "service_details",
                "plan_name",
                "plan_price",
                "payment_day",
                "payment_info"
            ),
            definition.parameters.map { it.metaParameterName }
        )
    }

    @Test
    fun `invalid template code throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            WhatsAppTemplateCatalog.getByCodeString("UNKNOWN_TEMPLATE")
        }
    }
}

class TemplateParameterResolverTest {

    private val subscription = Subscription(
        firstName = "Juan",
        lastName = "Perez",
        phone = "987654321",
        equipmentCondition = EquipmentCondition.LOAN
    ).apply { id = 10 }

    @Test
    fun `resolves payment reminder parameters`() {
        val payment = Payment(
            discountAmount = 0.0,
            paid = false,
            amountToPay = 79.9,
            billingDateDatetime = LocalDateTime.of(2026, 7, 1, 0, 0)
        ).apply { id = 5; this.subscription = subscription }

        val parameters = TemplateParameterResolver.resolve(
            definition = WhatsAppTemplateCatalog.get(WhatsAppTemplateCode.PAYMENT_REMINDER),
            subscription = subscription,
            payment = payment
        )

        assertEquals("Juan Perez", parameters[0].text)
        assertEquals("79.9", parameters[1].text)
        assertEquals("01/07/2026", parameters[2].text)
    }

    @Test
    fun `resolves payment validation parameters`() {
        val payment = Payment(
            discountAmount = 0.0,
            paid = true,
            amountToPay = 79.9,
            amountPaid = 79.9,
            billingDateDatetime = LocalDateTime.of(2026, 7, 1, 0, 0),
            paymentDateDatetime = LocalDateTime.of(2026, 7, 15, 10, 30)
        ).apply { id = 6; this.subscription = subscription }

        val parameters = TemplateParameterResolver.resolve(
            definition = WhatsAppTemplateCatalog.get(WhatsAppTemplateCode.PAYMENT_VALIDATION),
            subscription = subscription,
            payment = payment
        )

        assertEquals("Juan Perez", parameters[0].text)
        assertEquals("79.9", parameters[1].text)
        assertEquals("15/07/2026", parameters[2].text)
    }

    @Test
    fun `resolves service cut notice from oldest unpaid payment`() {
        val oldestUnpaid = Payment(
            discountAmount = 0.0,
            paid = false,
            amountToPay = 55.0,
            billingDateDatetime = LocalDateTime.of(2026, 5, 1, 0, 0)
        )

        val parameters = TemplateParameterResolver.resolve(
            definition = WhatsAppTemplateCatalog.get(WhatsAppTemplateCode.SERVICE_CUT_NOTICE),
            subscription = subscription,
            oldestUnpaidPayment = oldestUnpaid
        )

        assertEquals("Juan Perez", parameters[0].text)
        assertEquals("55.0", parameters[1].text)
        assertEquals("01/05/2026", parameters[2].text)
    }

    @Test
    fun `welcome template resolves all variables with welcome context`() {
        val welcomeContext = WelcomeTemplateContext(
            serviceTitle = "TV Cable",
            serviceDetails = "Full HD + SD y mas de 90 canales",
            planName = "TV Full HD",
            planPrice = "30.00",
            paymentDay = "15",
            paymentInfo = WelcomeServiceCopy.PAYMENT_INFO
        )

        val parameters = TemplateParameterResolver.resolve(
            definition = WhatsAppTemplateCatalog.get(WhatsAppTemplateCode.WELCOME_CUSTOMER),
            subscription = subscription,
            welcomeContext = welcomeContext
        )

        assertEquals(7, parameters.size)
        assertEquals("Juan Perez", parameters[0].text)
        assertEquals("TV Cable", parameters[1].text)
        assertEquals("Full HD + SD y mas de 90 canales", parameters[2].text)
        assertEquals("TV Full HD", parameters[3].text)
        assertEquals("30.00", parameters[4].text)
        assertEquals("15", parameters[5].text)
        assertEquals(WelcomeServiceCopy.PAYMENT_INFO, parameters[6].text)
    }

    @Test
    fun `welcome template requires welcome context`() {
        assertThrows(IllegalArgumentException::class.java) {
            TemplateParameterResolver.resolve(
                definition = WhatsAppTemplateCatalog.get(WhatsAppTemplateCode.WELCOME_CUSTOMER),
                subscription = subscription
            )
        }
    }

    @Test
    fun `payment date is required for validation template`() {
        val payment = Payment(
            discountAmount = 0.0,
            paid = true,
            amountToPay = 79.9,
            billingDateDatetime = LocalDateTime.of(2026, 7, 1, 0, 0),
            paymentDateDatetime = null
        )

        assertThrows(IllegalArgumentException::class.java) {
            TemplateParameterResolver.resolve(
                definition = WhatsAppTemplateCatalog.get(WhatsAppTemplateCode.PAYMENT_VALIDATION),
                subscription = subscription,
                payment = payment
            )
        }
    }
}

class PeruvianPhoneValidatorTest {

    @Test
    fun `accepts valid peruvian mobile formats`() {
        assertEquals(true, PeruvianPhoneValidator.isValid("987654321"))
        assertEquals(true, PeruvianPhoneValidator.isValid("51987654321"))
    }

    @Test
    fun `rejects invalid numbers`() {
        assertEquals(false, PeruvianPhoneValidator.isValid("123456789"))
        assertEquals(false, PeruvianPhoneValidator.isValid(""))
    }
}
