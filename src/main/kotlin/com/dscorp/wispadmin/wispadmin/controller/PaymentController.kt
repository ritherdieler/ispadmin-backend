package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.data.model.Payment
import com.dscorp.wispadmin.wispadmin.data.model.util.BaseResponse
import com.dscorp.wispadmin.wispadmin.dto.PaymentDto
import com.dscorp.wispadmin.wispadmin.requestbody.MultiPaymentRegisterRequest
import com.dscorp.wispadmin.wispadmin.requestbody.PaymentRequest
import com.dscorp.wispadmin.wispadmin.requestbody.PaymentUpdateRequest
import com.dscorp.wispadmin.wispadmin.requestbody.PaymentCreateRequest
import com.dscorp.wispadmin.wispadmin.requestbody.PaymentInvoiceCreateRequest
import com.dscorp.wispadmin.wispadmin.requestbody.PaymentValidationResponse
import com.dscorp.wispadmin.wispadmin.repository.PaymentRepository
import com.dscorp.wispadmin.wispadmin.service.PaymentInvoiceService
import com.dscorp.wispadmin.wispadmin.service.PaymentService
import org.springframework.http.ResponseEntity
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import com.dscorp.wispadmin.wispadmin.util.toLocalDateTimeOrNull

@CrossOrigin(origins = ["*"])
@RestController
@RequestMapping("/payment")
class PaymentController(
    private val repository: PaymentRepository,
    private val paymentService: PaymentService,
    private val paymentInvoiceService: PaymentInvoiceService
) {

    private fun Payment.asDto() = paymentService.toPublicDto(this)

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
            ResponseEntity.ok(payment.get().asDto())
        } else {
            ResponseEntity.notFound().build()
        }
    }

    @PutMapping
    fun registerPayment(@RequestBody newPayment: PaymentRequest): ResponseEntity<PaymentDto> =
        ResponseEntity.ok(paymentService.registerPayment(newPayment).asDto())

    @PutMapping("/batch")
    fun registerPayments(@RequestBody request: MultiPaymentRegisterRequest): ResponseEntity<List<PaymentDto>> =
        ResponseEntity.ok(paymentService.registerPayments(request).map { it.asDto() })

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
        return ResponseEntity.ok(payments.map { it.asDto() })
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

        return ResponseEntity.ok(limitedPayments.map { it.asDto() })
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

        return ResponseEntity.ok(repository.save(payment).asDto())
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
        val payment = paymentService.createPaidPayment(createRequest)
        return ResponseEntity.status(201).body(payment.asDto())
    }

    @PostMapping("/invoice")
    fun createInvoice(@RequestBody request: PaymentInvoiceCreateRequest): ResponseEntity<PaymentDto> {
        return try {
            val payment = paymentInvoiceService.createInvoice(request)
            ResponseEntity.status(201).body(payment.asDto())
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
}
