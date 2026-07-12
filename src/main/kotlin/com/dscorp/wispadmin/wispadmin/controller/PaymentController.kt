package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.data.model.util.BaseResponse
import com.dscorp.wispadmin.wispadmin.dto.PaymentDto
import com.dscorp.wispadmin.wispadmin.requestbody.PaymentRequest
import com.dscorp.wispadmin.wispadmin.requestbody.PaymentUpdateRequest
import com.dscorp.wispadmin.wispadmin.requestbody.PaymentCreateRequest
import com.dscorp.wispadmin.wispadmin.requestbody.PaymentInvoiceCreateRequest
import com.dscorp.wispadmin.wispadmin.requestbody.PaymentValidationResponse
import com.dscorp.wispadmin.wispadmin.repository.PaymentRepository
import com.dscorp.wispadmin.wispadmin.service.MikrotikService
import com.dscorp.wispadmin.wispadmin.service.PaymentInvoiceService
import org.springframework.http.ResponseEntity
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import com.dscorp.wispadmin.wispadmin.util.toLocalDateTimeOrNull
import com.dscorp.wispadmin.wispadmin.service.PaymentWhatsAppNotificationService

@CrossOrigin(origins = ["*"])
@RestController
@RequestMapping("/payment")
class PaymentController(
    private val repository: PaymentRepository,
    private val mikrotikService: MikrotikService,
    private val paymentInvoiceService: PaymentInvoiceService,
    private val paymentWhatsAppNotificationService: PaymentWhatsAppNotificationService
) {

    @GetMapping("/getElectronicPayers")
    fun getElectronicPayers(@RequestParam subscriptionId: Int): BaseResponse {
        val electronicPayers = repository.getElectronicPayers(subscriptionId)
        return BaseResponse(
            status = 200,
            message = "ok",
            data = electronicPayers ?: emptyList<String>()
        )
    }

    @GetMapping("/{id}")
    fun getPaymentById(@PathVariable id: String): ResponseEntity<PaymentDto> {
        val paymentId = id.toIntOrNull()
            ?: return ResponseEntity.status(400).body(null)

        val payment = repository.findById(paymentId)
        return if (payment.isPresent) {
            ResponseEntity.ok(payment.get().toDto())
        } else {
            ResponseEntity.notFound().build()
        }
    }

    @PutMapping
    fun registerPayment(@RequestBody newPayment: PaymentRequest): ResponseEntity<PaymentDto> =
        ResponseEntity.ok(mikrotikService.savePayment(newPayment).toDto())

    @GetMapping("/filtered")
    fun getPayments(
        @RequestParam(value = "subscriptionId", required = false) subscriptionCode: Int?,
        @RequestParam(value = "startDate", required = false) startDate: Long?,
        @RequestParam(value = "endDate", required = false) endDate: Long?,
    ): ResponseEntity<List<PaymentDto>> {
        if (subscriptionCode == null) {
            return ResponseEntity.badRequest().body(emptyList())
        }

        val initDate = (startDate ?: 0L).toLocalDateTimeOrNull()
            ?: java.time.LocalDateTime.of(1970, 1, 1, 0, 0)

        val finalDate = (endDate ?: System.currentTimeMillis()).toLocalDateTimeOrNull()
            ?: java.time.LocalDateTime.now()

        val payments = repository.findBySubscriptionFiltered(subscriptionCode, initDate, finalDate)
        return ResponseEntity.ok(payments.map { it.toDto() })
    }

    @GetMapping
    @Transactional
    fun findTop10BySubscriptionIdOrderByPaymentDateDesc(
        @RequestParam(value = "subscriptionId", required = false) subscriptionId: Int?,
        @RequestParam(value = "limit", required = false) limit: Int?
    ): ResponseEntity<List<PaymentDto>> {
        if (subscriptionId == null) {
            return ResponseEntity.badRequest().body(emptyList())
        }

        val payments = repository.findBySubscriptionIdOrderByBillingDateDatetimeDesc(subscriptionId)
        val limitedPayments = if (limit != null && limit > 0) {
            payments.take(limit)
        } else {
            payments
        }

        return ResponseEntity.ok(limitedPayments.map { it.toDto() })
    }

    @PutMapping("/{id}")
    fun updatePayment(
        @PathVariable id: Int,
        @RequestBody updateRequest: PaymentUpdateRequest
    ): ResponseEntity<PaymentDto> {
        val existingPayment = repository.findById(id)
        if (!existingPayment.isPresent) {
            return ResponseEntity.notFound().build()
        }

        val payment = existingPayment.get()

        updateRequest.amountToPay?.let { payment.amountToPay = it }
        updateRequest.amountPaid?.let { payment.amountPaid = it }
        updateRequest.discountAmount?.let { payment.discountAmount = it }
        updateRequest.discountReason?.let { payment.discountReason = it }
        updateRequest.method?.let { payment.method = it }
        updateRequest.electronicPayerName?.let { payment.electronicPayerName = it }
        updateRequest.paymentDate?.let {
            payment.paymentDateDatetime = it.toLocalDateTimeOrNull()
        }
        updateRequest.billingDate?.let {
            payment.billingDateDatetime = it.toLocalDateTimeOrNull() ?: payment.billingDateDatetime
        }

        return ResponseEntity.ok(repository.save(payment).toDto())
    }

    @DeleteMapping("/{id}")
    fun deletePayment(@PathVariable id: Int): ResponseEntity<BaseResponse> {
        val existingPayment = repository.findById(id)
        if (!existingPayment.isPresent) {
            return ResponseEntity.status(404).body(
                BaseResponse(
                    status = 404,
                    message = "Pago no encontrado",
                    data = null
                )
            )
        }

        repository.deleteById(id)
        return ResponseEntity.ok(
            BaseResponse(
                status = 200,
                message = "Pago eliminado exitosamente",
                data = null
            )
        )
    }

    @PostMapping
    fun createPayment(@RequestBody createRequest: PaymentCreateRequest): ResponseEntity<PaymentDto> {
        val paymentRequest = PaymentRequest(
            id = null,
            amountPaid = createRequest.amountPaid,
            discountAmount = createRequest.discountAmount ?: 0.0,
            discountReason = createRequest.discountReason,
            method = createRequest.method,
            paid = true,
            subscriptionId = createRequest.subscriptionId,
            responsibleId = createRequest.responsibleId,
            electronicPayerName = createRequest.electronicPayerName,
            billingDate = createRequest.billingDate ?: System.currentTimeMillis()
        )

        val payment = mikrotikService.savePayment(paymentRequest)
        return ResponseEntity.status(201).body(payment.toDto())
    }

    @PostMapping("/invoice")
    fun createInvoice(@RequestBody request: PaymentInvoiceCreateRequest): ResponseEntity<PaymentDto> {
        return try {
            val payment = paymentInvoiceService.createInvoice(request)
            ResponseEntity.status(201).body(payment.toDto())
        } catch (e: IllegalArgumentException) {
            ResponseEntity.status(400).body(null)
        }
    }

    @PostMapping("/validate")
    fun validatePayment(@RequestBody paymentData: PaymentRequest): ResponseEntity<PaymentValidationResponse> {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        if (paymentData.amountPaid <= 0) {
            errors.add("El monto pagado debe ser mayor a 0")
        }

        if (paymentData.discountAmount != null && paymentData.discountAmount < 0) {
            errors.add("El descuento no puede ser negativo")
        }

        if (paymentData.discountAmount != null && paymentData.discountAmount > paymentData.amountPaid) {
            errors.add("El descuento no puede ser mayor al monto pagado")
        }

        if (paymentData.method.isBlank()) {
            errors.add("Debe seleccionar un método de pago")
        }

        if ((paymentData.method == "Yape" || paymentData.method == "Plin") &&
            (paymentData.electronicPayerName.isNullOrBlank())) {
            errors.add("Debe ingresar el nombre del pagador para pagos electrónicos")
        }

        if (paymentData.discountAmount != null && paymentData.discountAmount > 0 &&
            paymentData.discountReason.isNullOrBlank()) {
            warnings.add("Se recomienda especificar una razón para el descuento")
        }

        val isValid = errors.isEmpty()

        return ResponseEntity.ok(
            PaymentValidationResponse(
                isValid = isValid,
                errors = errors,
                warnings = warnings
            )
        )
    }

    @PostMapping("/{id}/send-whatsapp-reminder")
    fun sendPaymentReminderByWhatsApp(@PathVariable id: Int): ResponseEntity<BaseResponse> {
        return try {
            paymentWhatsAppNotificationService.sendPaymentReminder(id)

            ResponseEntity.ok(
                BaseResponse(
                    status = 200,
                    message = "Recordatorio enviado correctamente por WhatsApp.",
                    data = null
                )
            )
        } catch (e: IllegalStateException) {
            ResponseEntity.status(409).body(
                BaseResponse(
                    status = 409,
                    message = e.message ?: "Ya existe un recordatorio enviado hoy.",
                    data = null
                )
            )
        } catch (e: Exception) {
            val status = whatsAppHttpStatus(e.message)

            ResponseEntity.status(status).body(
                BaseResponse(
                    status = status,
                    message = e.message ?: "No se pudo enviar el recordatorio por WhatsApp.",
                    data = null
                )
            )
        }
    }

    @PostMapping("/send-whatsapp-reminders")
    fun sendPendingPaymentRemindersByWhatsApp(
        @RequestParam(defaultValue = "5") limit: Int
    ): ResponseEntity<BaseResponse> {
        val result = paymentWhatsAppNotificationService.sendPendingPaymentReminders(limit)
        return ResponseEntity.ok(
            BaseResponse(
                status = 200,
                message = "Proceso de recordatorios WhatsApp finalizado.",
                data = result
            )
        )
    }

    @PostMapping("/send-monthly-whatsapp-reminders")
    fun sendMonthlyPaymentRemindersByWhatsApp(
        @RequestParam(required = false) limit: Int?
    ): ResponseEntity<BaseResponse> {
        val result = paymentWhatsAppNotificationService.sendMonthlyPaymentReminders(limit)
        return ResponseEntity.ok(
            BaseResponse(
                status = 200,
                message = "Proceso mensual de recordatorios WhatsApp finalizado.",
                data = result
            )
        )
    }

    private fun whatsAppHttpStatus(errorMessage: String?): Int {
        val message = errorMessage.orEmpty().lowercase()

        return when {
            "ya se envio" in message -> 409
            "token de whatsapp invalido" in message -> 401
            "telefono debe ser un celular peruano valido" in message -> 400
            "numero no esta autorizado" in message -> 422
            "plantilla de whatsapp no existe" in message -> 400
            else -> 500
        }
    }
}
