package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.data.model.Modules
import com.dscorp.wispadmin.wispadmin.data.model.Payment
import com.dscorp.wispadmin.wispadmin.data.model.util.BaseResponse
import com.dscorp.wispadmin.wispadmin.dto.PaymentDto
import com.dscorp.wispadmin.wispadmin.requestbody.PaymentRequest
import com.dscorp.wispadmin.wispadmin.requestbody.PaymentUpdateRequest
import com.dscorp.wispadmin.wispadmin.requestbody.PaymentCreateRequest
import com.dscorp.wispadmin.wispadmin.requestbody.PaymentInvoiceCreateRequest
import com.dscorp.wispadmin.wispadmin.requestbody.PaymentValidationResponse
import com.dscorp.wispadmin.wispadmin.repository.ErrorLogRepository
import com.dscorp.wispadmin.wispadmin.repository.PaymentRepository
import com.dscorp.wispadmin.wispadmin.service.MikrotikService
import com.dscorp.wispadmin.wispadmin.service.PaymentInvoiceService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import javax.persistence.EntityManager
import javax.persistence.PersistenceContext
import com.dscorp.wispadmin.wispadmin.util.toLocalDateTimeOrNull

@CrossOrigin(origins = ["*"])
@RestController
@RequestMapping("/payment")
class PaymentController @Autowired constructor(
    private val repository: PaymentRepository,
    private val mikrotikService: MikrotikService,
    private val errorLogRepository: ErrorLogRepository,
    private val paymentInvoiceService: PaymentInvoiceService
) {

    val objectErrorResponse: ResponseEntity<PaymentDto> = ResponseEntity.status(500).body(null)
    val listObjectErrorResponse: ResponseEntity<List<PaymentDto>> = ResponseEntity.status(500).body(null)

    @PersistenceContext
    private lateinit var entityManager: EntityManager

    @GetMapping("/getElectronicPayers")
    fun getElectronicPayers(@RequestParam subscriptionId: Int): BaseResponse {
        return try {
            val electronicPayers = repository.getElectronicPayers(subscriptionId)
            BaseResponse(
                status = 200,
                message = "ok",
                data = electronicPayers ?: emptyList<String>()
            )
        } catch (e: Exception) {
            BaseResponse(
                status = 500,
                message = e.message,
                data = null
            )
        }
    }

    @GetMapping("/{id}")
    fun getPaymentById(@PathVariable id: String): ResponseEntity<PaymentDto> {
        return try {
            val paymentId = id.toIntOrNull()
            if (paymentId == null) {
                return ResponseEntity.status(400).body(null)
            }
            
            val payment = repository.findById(paymentId)
            if (payment.isPresent) {
                ResponseEntity.status(200).body(payment.get().toDto())
            } else {
                ResponseEntity.status(404).body(null)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            errorLogRepository.save(e.toErrorLog(Modules.PAYMENT))
            objectErrorResponse
        }
    }

    @PutMapping
    fun registerPayment(@RequestBody newPayment: PaymentRequest): ResponseEntity<PaymentDto> {
        return try {
            val payment = mikrotikService.savePayment(newPayment)
            return ResponseEntity.status(200).body(payment.toDto())
        } catch (e: Exception) {
            e.printStackTrace()
            errorLogRepository.save(e.toErrorLog(Modules.PAYMENT))
            objectErrorResponse
        }
    }

    @GetMapping("/filtered")
    fun getPayments(
        @RequestParam(value = "subscriptionId", required = false) subscriptionCode: Int?,
        @RequestParam(value = "startDate", required = false) startDate: Long?,
        @RequestParam(value = "endDate", required = false) endDate: Long?,
    ): ResponseEntity<List<PaymentDto>> {
        return try {
            if (subscriptionCode == null) {
                return ResponseEntity.status(400).body(emptyList())
            }

            val initDate = (startDate ?: 0L).toLocalDateTimeOrNull()
                ?: java.time.LocalDateTime.of(1970, 1, 1, 0, 0)

            val finalDate = (endDate ?: System.currentTimeMillis()).toLocalDateTimeOrNull()
                ?: java.time.LocalDateTime.now()

            val payments = repository.findBySubscriptionFiltered(
                subscriptionCode,
                initDate,
                finalDate
            )
            ResponseEntity.status(200).body(payments.map { it.toDto() })
        } catch (e: Exception) {
            e.printStackTrace()
            errorLogRepository.save(e.toErrorLog(Modules.PAYMENT))
            listObjectErrorResponse
        }
    }

    @GetMapping
    @Transactional
    fun findTop10BySubscriptionIdOrderByPaymentDateDesc(
        @RequestParam(value = "subscriptionId", required = false) subscriptionId: Int?,
        @RequestParam(value = "limit", required = false) limit: Int?
    ): ResponseEntity<List<PaymentDto>> {
        return try {
            if (subscriptionId == null) {
                return ResponseEntity.status(400).body(emptyList())
            }

            val payments = repository.findBySubscriptionIdOrderByBillingDateDatetimeDesc(subscriptionId)
            // Aplicar límite si se especifica
            val limitedPayments = if (limit != null && limit > 0) {
                payments.take(limit)
            } else {
                payments
            }

            ResponseEntity.status(200).body(limitedPayments.map { it.toDto() })
        } catch (e: Exception) {
            e.printStackTrace()
            errorLogRepository.save(e.toErrorLog(Modules.PAYMENT))
            listObjectErrorResponse
        }
    }

    /**
     * Actualiza un pago existente
     */
    @PutMapping("/{id}")
    fun updatePayment(
        @PathVariable id: Int,
        @RequestBody updateRequest: PaymentUpdateRequest
    ): ResponseEntity<PaymentDto> {
        return try {
            val existingPayment = repository.findById(id)
            if (!existingPayment.isPresent) {
                return ResponseEntity.status(404).body(null)
            }

            val payment = existingPayment.get()
            
            // Actualizar campos si están presentes
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

            val updatedPayment = repository.save(payment)
            ResponseEntity.status(200).body(updatedPayment.toDto())
        } catch (e: Exception) {
            e.printStackTrace()
            errorLogRepository.save(e.toErrorLog(Modules.PAYMENT))
            objectErrorResponse
        }
    }

    /**
     * Elimina un pago
     */
    @DeleteMapping("/{id}")
    fun deletePayment(@PathVariable id: Int): ResponseEntity<BaseResponse> {
        return try {
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
            ResponseEntity.status(200).body(
                BaseResponse(
                    status = 200,
                    message = "Pago eliminado exitosamente",
                    data = null
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
            errorLogRepository.save(e.toErrorLog(Modules.PAYMENT))
            ResponseEntity.status(500).body(
                BaseResponse(
                    status = 500,
                    message = "Error al eliminar el pago: ${e.message}",
                    data = null
                )
            )
        }
    }

    /**
     * Crea un nuevo pago
     */
    @PostMapping
    fun createPayment(@RequestBody createRequest: PaymentCreateRequest): ResponseEntity<PaymentDto> {
        return try {
            // Crear nuevo pago usando el servicio existente
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
            ResponseEntity.status(201).body(payment.toDto())
        } catch (e: Exception) {
            e.printStackTrace()
            errorLogRepository.save(e.toErrorLog(Modules.PAYMENT))
            objectErrorResponse
        }
    }

    @PostMapping("/invoice")
    fun createInvoice(@RequestBody request: PaymentInvoiceCreateRequest): ResponseEntity<PaymentDto> {
        return try {
            val payment = paymentInvoiceService.createInvoice(request)
            ResponseEntity.status(201).body(payment.toDto())
        } catch (e: IllegalArgumentException) {
            ResponseEntity.status(400).body(null)
        } catch (e: Exception) {
            e.printStackTrace()
            errorLogRepository.save(e.toErrorLog(Modules.PAYMENT))
            objectErrorResponse
        }
    }

    /**
     * Valida los datos de un pago
     */
    @PostMapping("/validate")
    fun validatePayment(@RequestBody paymentData: PaymentRequest): ResponseEntity<PaymentValidationResponse> {
        return try {
            val errors = mutableListOf<String>()
            val warnings = mutableListOf<String>()

            // Validaciones básicas
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

            // Validaciones específicas para pagos electrónicos
            if ((paymentData.method == "Yape" || paymentData.method == "Plin") && 
                (paymentData.electronicPayerName.isNullOrBlank())) {
                errors.add("Debe ingresar el nombre del pagador para pagos electrónicos")
            }

            // Advertencias
            if (paymentData.discountAmount != null && paymentData.discountAmount > 0 && 
                paymentData.discountReason.isNullOrBlank()) {
                warnings.add("Se recomienda especificar una razón para el descuento")
            }

            val isValid = errors.isEmpty()
            
            ResponseEntity.status(200).body(
                PaymentValidationResponse(
                    isValid = isValid,
                    errors = errors,
                    warnings = warnings
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
            errorLogRepository.save(e.toErrorLog(Modules.PAYMENT))
            ResponseEntity.status(500).body(
                PaymentValidationResponse(
                    isValid = false,
                    errors = listOf("Error interno del servidor: ${e.message}"),
                    warnings = emptyList()
                )
            )
        }
    }


}