package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.data.model.*
import com.dscorp.wispadmin.wispadmin.data.model.util.BaseResponse
import com.dscorp.wispadmin.wispadmin.dto.*
import com.dscorp.wispadmin.wispadmin.extensions.getFirstDayOfMonthInMillis
import com.dscorp.wispadmin.wispadmin.extensions.getLastDayOfMonthInMillis
import com.dscorp.wispadmin.wispadmin.extensions.toErrorLog
import com.dscorp.wispadmin.wispadmin.repository.*
import com.dscorp.wispadmin.wispadmin.requestbody.*
import com.dscorp.wispadmin.wispadmin.requestbody.smartoltrequest.MoveOnuRequest
import com.dscorp.wispadmin.wispadmin.requestbody.smartoltrequest.OnuAuthorizationRequest
import com.dscorp.wispadmin.wispadmin.service.SubscriptionService
import com.dscorp.wispadmin.wispadmin.service.BorneValidationResult
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.io.ClassPathResource
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.time.YearMonth
import com.dscorp.wispadmin.wispadmin.service.FirebaseStorageService
import org.springframework.web.multipart.MultipartFile

const val DATE_FORMAT = "dd/MM/yyyy"

@CrossOrigin(origins = ["*"], maxAge = 3600)
@RestController
@RequestMapping("/subscription")
class SubscriptionController @Autowired constructor(
    private val repository: SubscriptionRepository,
    private val subscriptionService: SubscriptionService,
    private val placeRepository: PlaceRepository,
    private val planRepository: PlanRepository,
    private val napBoxRepository: NapBoxRepository,
    private val networkDeviceRepository: NetworkDeviceRepository,
    private val couponRepository: CouponRepository,
    private val errorLogRepository: ErrorLogRepository,
    private val subscriptionLogRepository: SubscriptionLogRepository,
    private val storageService: FirebaseStorageService
) {

    val objectErrorResponse: ResponseEntity<SubscriptionDto> = ResponseEntity.status(500).body(null)
    val objectUSerErrorResponse: ResponseEntity<SubscriptionUserDto> = ResponseEntity.status(500).body(null)
    val listErrorResponse: ResponseEntity<List<SubscriptionDto>> = ResponseEntity.status(500).body(null)

    @GetMapping("/findByElectronicPayerName")
    fun findByElectronicPayerName(@RequestParam("electronicPayerName") electronicPayerName: String): BaseResponse {
        return try {
            val electronicPayers = repository.findByElectronicPayerName(electronicPayerName).map { it.toPayerFinderResultDto()}

            BaseResponse(
                status = 200,
                message = "ok",
                data = electronicPayers
            )
        } catch (e: Exception) {
            e.printStackTrace()
            BaseResponse(
                status = 500,
                message = e.message,
                data = null
            )
        }
    }

    @PutMapping("/changeNapBox")
    fun changeNapBox(@RequestBody request: MoveOnuRequest): ResponseEntity<NapBoxDto> {
        return try {
            val subscription = subscriptionService.changeNapBox(request)
            subscriptionLogRepository.save(
                SubscriptionLog(
                    subscription = subscription,
                    actionType = SubscriptionActionType.CHANGE_NAP_BOX,
                    planName = subscription.plan?.name,
                    planPrince = subscription.plan?.price ?: 0.0,
                    planId = subscription.plan?.id
                )
            )
            return ResponseEntity.status(200).body(subscription.napBox?.toDto())
        } catch (e: Exception) {
            e.printStackTrace()
            errorLogRepository.save(e.toErrorLog(Modules.SUBSCRIPTION))
            ResponseEntity.status(500).body(null)
        }
    }

    @PutMapping("/reboot-fiber-onu")
    fun rebootFiberOnu(@RequestParam subscriptionId: Int): ResponseEntity<Any> {
        return try {
            val subscription = subscriptionService.rebootFiberOnu(subscriptionId)
            subscriptionLogRepository.save(
                SubscriptionLog(
                    subscription = subscription,
                    actionType = SubscriptionActionType.REBOOT_FIBER_ONU,
                    planName = subscription.plan?.name,
                    planPrince = subscription.plan?.price ?: 0.0,
                    planId = subscription.plan?.id
                )
            )
            ResponseEntity.ok(
                RebootFiberOnuResponseDto(
                    message = "Se envió el reinicio de la ONU correctamente",
                    subscriptionId = subscriptionId
                )
            )
        } catch (e: IllegalArgumentException) {
            ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("error" to (e.message ?: "")))
        } catch (e: Exception) {
            e.printStackTrace()
            errorLogRepository.save(e.toErrorLog(Modules.SUBSCRIPTION))
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(mapOf("error" to (e.message ?: "Error al reiniciar la ONU")))
        }
    }

    @GetMapping("/{subscriptionId}")
    fun getSubscription(@PathVariable subscriptionId: Int): ResponseEntity<SubscriptionDto> {
        return try {
            val subscription = repository.findById(subscriptionId).orElse(null)
            if (subscription != null) {
                ResponseEntity.status(200).body(subscription.toDto())
            } else {
                ResponseEntity.status(404).body(null)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            errorLogRepository.save(e.toErrorLog(Modules.SUBSCRIPTION))
            objectErrorResponse
        }
    }

    @PostMapping("/login")
    fun login(@RequestBody user: LoginBody): ResponseEntity<SubscriptionUserDto> {
        return try {
            val foundUser = repository.logIn(user.username, user.password)
            if (foundUser != null) {
                return ResponseEntity.status(200).body(foundUser.toSubscriptionUserDto())
            }
            return ResponseEntity.notFound().build()

        } catch (e: Exception) {
            e.printStackTrace()
            errorLogRepository.save(e.toErrorLog(Modules.SUBSCRIPTION))

            objectUSerErrorResponse
        }
    }


    @PostMapping("/generate-simple-queues")
    fun generateSimpleQueue(): BaseResponse {
        return try {
            val stats = subscriptionService.createSubscriptionsSimpleQueue().get()

            BaseResponse(
                status = 200, 
                message = "Colas generadas correctamente",
                data = stats
            )

        } catch (e: Exception) {
            e.printStackTrace()
            BaseResponse(status = 500, error = e.message)
        }
    }



    @PutMapping("/migration")
    fun migrateToFiber(@RequestBody request: MigrationRequest): BaseResponse {
        return try {
            val subscription = subscriptionService.migrateToFiber(request)
            subscriptionLogRepository.save(
                SubscriptionLog(
                    subscription = subscription,
                    actionType = SubscriptionActionType.MIGRATE_SUBSCRIPTION,
                    planName = subscription.plan?.name,
                    planPrince = subscription.plan?.price ?: 0.0,
                    planId = subscription.plan?.id
                )
            )
            return BaseResponse(status = 200, data = subscription.toDto())
        } catch (e: Exception) {
            e.printStackTrace()
            errorLogRepository.save(e.toErrorLog(Modules.SUBSCRIPTION))

            BaseResponse(status = 500, error = e.message)
        }
    }

    @GetMapping("/profile")
    fun getSubscriptionUserProfileData(@RequestParam("subscriptionId") subscriptionId: String): ResponseEntity<UpdateSubscriptionRequest> {

        return repository.findById(subscriptionId.toInt()).orElse(null)?.let {
            ResponseEntity.status(HttpStatus.OK).body(it.toUpdateSubscriptionRequest())
        } ?: ResponseEntity.status(HttpStatus.NOT_FOUND).build()
    }

    @PutMapping("/restore-internet-connection")
    fun restoreInternetConnection(
        @RequestParam subscriptionId: Int,
        @RequestParam responsibleId: Int,
        @RequestParam(required = false) notes: String? = null
    ): ResponseEntity<Any> {
        return try {
            subscriptionService.restoreInternetConnection(subscriptionId, responsibleId, notes)
            val response = RestoreInternetConnectionResponseDto(
                message = "Conexión a internet restablecida correctamente",
                subscriptionId = subscriptionId
            )
            ResponseEntity.status(HttpStatus.OK).body(response)
        } catch (e: IllegalStateException) {
            ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                mapOf("error" to e.message)
            )
        } catch (e: Exception) {
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                mapOf("error" to "Error interno del servidor: ${e.message}")
            )
        }
    }

    @PutMapping("/reactivate-service")
    fun reactivateService(
        @RequestParam subscriptionId: Int,
        @RequestParam responsibleId: Int,
        @RequestParam(required = false) notes: String? = null,
        @RequestParam(required = false) newBorneNumber: String? = null
    ): ResponseEntity<Any> {
        return try {
            subscriptionService.reactivateService(subscriptionId, responsibleId, notes, newBorneNumber)
            val response = ReactivateServiceResponseDto(
                message = "Servicio reactivado correctamente",
                subscriptionId = subscriptionId
            )
            ResponseEntity.ok(response)
        } catch (e: Exception) {
            e.printStackTrace()
            errorLogRepository.save(e.toErrorLog(Modules.SUBSCRIPTION))
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(mapOf("error" to (e.message ?: "Error al reactivar el servicio")))
        }
    }

    /**
     * Valida si una suscripción puede ser reactivada
     */
    @GetMapping("/{subscriptionId}/reactivation-validation")
    fun validateReactivation(@PathVariable subscriptionId: Int): ResponseEntity<Any> {
        return try {
            val validation = subscriptionService.getBorneValidationForReactivation(subscriptionId)
            
            val response = when (validation) {
                is BorneValidationResult.Valid -> ReactivationValidationResponseDto(
                    canReactivate = true,
                    borneNumber = validation.borneNumber
                )
                is BorneValidationResult.BorneReassigned -> ReactivationValidationResponseDto(
                    canReactivate = false,
                    originalBorne = validation.originalBorne,
                    napBoxId = validation.napBoxId,
                    napBoxCode = validation.napBoxCode,
                    availableBornes = validation.availableBornes,
                    message = validation.message
                )
                is BorneValidationResult.NoBornesAvailable -> ReactivationValidationResponseDto(
                    canReactivate = false,
                    napBoxId = validation.napBoxId,
                    napBoxCode = validation.napBoxCode,
                    message = validation.message
                )
            }
            
            ResponseEntity.ok(response)
        } catch (e: Exception) {
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(mapOf("error" to (e.message ?: "Error al validar reactivación")))
        }
    }

    /**
     * Obtiene los bornes disponibles para una NAP Box específica
     */
    @GetMapping("/napbox/{napBoxId}/available-bornes")
    fun getAvailableBornes(@PathVariable napBoxId: Int): ResponseEntity<BaseResponse> {
        return try {
            val availableBornes = subscriptionService.borneManagementService.getAvailableBornes(napBoxId)
            val response = BaseResponse(
                status = 200, 
                data = availableBornes,
                message = "Bornes disponibles obtenidos correctamente"
            )
            ResponseEntity.ok(response)
        } catch (e: Exception) {
            val response = BaseResponse(status = 500, error = e.message)
            ResponseEntity.status(500).body(response)
        }
    }
    
    /**
     * Obtiene el estado de todos los bornes de una NAP Box
     */
    @GetMapping("/napbox/{napBoxId}/borne-status")
    fun getBorneStatus(@PathVariable napBoxId: Int): ResponseEntity<BaseResponse> {
        return try {
            val borneStatus = (1..16).associate { borneNumber ->
                borneNumber.toString() to subscriptionService.borneManagementService.isBorneAvailable(napBoxId, borneNumber.toString())
            }
            val response = BaseResponse(
                status = 200, 
                data = borneStatus,
                message = "Estado de bornes obtenido correctamente"
            )
            ResponseEntity.ok(response)
        } catch (e: Exception) {
            val response = BaseResponse(status = 500, error = e.message)
            ResponseEntity.status(500).body(response)
        }
    }

    @PutMapping("/update-location/v2")
    fun updateLocationV2(@RequestBody request: UpdateLocationRequest): ResponseEntity<BaseResponse> {
        return try {
            subscriptionService.updateSubscriptionLocation(request.subscriptionId, request.location)
            val response = BaseResponse(status = HttpStatus.OK.value(), message = "Ubicación actualizada correctamente")
            ResponseEntity.status(200).body(response)
        } catch (e: Exception) {
            e.printStackTrace()
            errorLogRepository.save(e.toErrorLog(Modules.SUBSCRIPTION))
            val response = BaseResponse(status = HttpStatus.INTERNAL_SERVER_ERROR.value(), error = e.message)
            ResponseEntity.status(500).body(response)
        }
    }

    @PutMapping("/payment-commitment")
    fun registerPaymentCommitment(@RequestParam subscriptionId: Int): ResponseEntity<Unit> {
        return try {
            subscriptionService.registerPaymentCommitment(subscriptionId)
            ResponseEntity.status(200).body(null)
        } catch (e: Exception) {
            e.printStackTrace()
            errorLogRepository.save(e.toErrorLog(Modules.SUBSCRIPTION))

            ResponseEntity.status(500).body(null)
        }
    }

    @GetMapping("/apply-coupon/{code}")
    fun applyCoupon(@PathVariable code: String): ResponseEntity<Coupon> {
        return try {
            val coupon = couponRepository.findByCodeAndExpirationDateGreaterThan(code, Date().time)
                ?: return ResponseEntity.status(404).body(Coupon())
            ResponseEntity.status(200).body(coupon)
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
            errorLogRepository.save(e.toErrorLog(Modules.SUBSCRIPTION))

            ResponseEntity.status(500).body(null)
        }
    }


    @PutMapping("/update-plan")
    fun updatePlan(@RequestBody updatePlanData: UpdateSubscriptionPlanBody): ResponseEntity<SubscriptionDto> =
        try {
            val subscription =
                subscriptionService.updateSubscriptionPlan(
                    subscriptionId = updatePlanData.subscriptionId,
                    planId = updatePlanData.planId
                )

            subscriptionLogRepository.save(
                SubscriptionLog(
                    subscription = subscription,
                    actionType = SubscriptionActionType.CHANGE_PLAN,
                    planName = subscription.plan?.name,
                    planPrince = subscription.plan?.price ?: 0.0,
                    planId = subscription.plan?.id
                )
            )

            ResponseEntity.status(200).body(subscription.toDto())
        } catch (e: Exception) {
            e.printStackTrace()
            errorLogRepository.save(e.toErrorLog(Modules.SUBSCRIPTION))

            ResponseEntity.status(500).body(null)
        }

    @PutMapping("/update-subscription-data")
    fun updatePlanData(@RequestBody updateSubscriptionData: UpdateSubscriptionDataBody): ResponseEntity<Unit> =
        try {
            subscriptionService.updateSubscriptionData(updateSubscriptionData)
            ResponseEntity.status(200).body(null)
        } catch (e: Exception) {
            e.printStackTrace()
            errorLogRepository.save(e.toErrorLog(Modules.SUBSCRIPTION))

            ResponseEntity.status(500).body(null)
        }

    @PutMapping("/cortarDeudores")
    fun cutInternet(): ResponseEntity<CutServiceSummaryDto> =
        try {
            val result = subscriptionService.cutInternetService()
            ResponseEntity.status(200).body(result)
        } catch (e: Exception) {
            e.printStackTrace()
            errorLogRepository.save(e.toErrorLog(Modules.SUBSCRIPTION))
            ResponseEntity.status(500).body(null)
        }


    @PutMapping("/cancel-subscription")
    fun cancelSubscription(@RequestParam("subscriptionId") subscriptionId: Int): ResponseEntity<Any> {
        return try {
            subscriptionService.cancelService(
                idSubscription = subscriptionId,
                onSuccess = { subscription ->
                    subscriptionLogRepository.save(
                        SubscriptionLog(
                            subscription = subscription,
                            actionType = SubscriptionActionType.CANCEL_SUBSCRIPTION,
                            planName = subscription.plan?.name,
                            planPrince = subscription.plan?.price ?: 0.0,
                            planId = subscription.plan?.id
                        )
                    )
                }
            )
            ResponseEntity.status(200).body(null)
        } catch (e: Exception) {
            e.printStackTrace()
            errorLogRepository.save(e.toErrorLog(Modules.SUBSCRIPTION))

            ResponseEntity.status(500).body(null)
        }

    }

    @PostMapping
    fun newSubscription(@RequestBody newSubscription: SubscriptionRequest): BaseResponse {
        return try {
            val subscription = subscriptionService.registerSubscription(
                newSubscription = newSubscription,
                onSuccess = {
                    subscriptionLogRepository.save(
                        SubscriptionLog(
                            subscription = it,
                            actionType = SubscriptionActionType.NEW_SUBSCRIPTION,
                            planName = it.plan?.name,
                            planPrince = it.plan?.price ?: 0.0,
                            planId = it.plan?.id
                        )
                    )
                }
            )

            BaseResponse(data = subscription, status = 200)
        } catch (e: Exception) {
            e.printStackTrace()
            errorLogRepository.save(e.toErrorLog(Modules.SUBSCRIPTION))

            when (e) {
                is DataIntegrityViolationException -> BaseResponse(
                    status = 409,
                    error = "Este usuario ya se encuentra registrado",
                )

                else -> BaseResponse(status = 500, error = e.message)
            }
        }
    }

    @PostMapping("/with-facade-photo",
        consumes = [MediaType.MULTIPART_FORM_DATA_VALUE]
        )
    fun newSubcriptionWithFacade(
        @RequestPart("subscription") newSubscription: SubscriptionRequest,
        @RequestPart("facadePhoto") facadephoto: MultipartFile
    ): BaseResponse{
        return try {
            // sube la foto de fachada a firebase
            val facadePhotoUrl = storageService.uploadFileToFolder(facadephoto,"facades")

            // solo se guarda la URL en la suscripcion la imagen queda almacenada en firebase
            newSubscription.facadePhotoUrl = facadePhotoUrl

            val subscription = subscriptionService.registerSubscription(
                newSubscription = newSubscription,
                onSuccess = {
                    subscriptionLogRepository.save(
                        SubscriptionLog(
                            subscription = it,
                            actionType = SubscriptionActionType.NEW_SUBSCRIPTION,
                            planName = it.plan?.name,
                            planPrince = it.plan?.price ?: 0.0,
                            planId = it.plan?.id
                        )
                    )
                }
            )

            BaseResponse(data = subscription, status = 200)
        } catch (e: Exception){
            e.printStackTrace()
            errorLogRepository.save(e.toErrorLog(Modules.SUBSCRIPTION))

            when (e){
                is DataIntegrityViolationException -> BaseResponse(
                    status = 409,
                    error = "Este usuario no se encuentra registrado",
                )

                else -> BaseResponse(status = 500, error = e.message)
            }
        }
    }

    @RequestMapping(
        value = ["/{subscriptionId}/facade-photo"],
        method = [RequestMethod.PUT],
        consumes = [MediaType.MULTIPART_FORM_DATA_VALUE]
    )
    fun updateFacadePhoto(
        @PathVariable subscriptionId: Int,
        @RequestPart("facadePhoto") facadephoto: MultipartFile
    ): BaseResponse {
        return try {
            // Busca la suscripcion existente que recibira la nueva foto de fachada.
            val subscription = repository.findById(subscriptionId).orElseThrow {
                Exception("Suscripcion no encontrada")
            }

            // Sube la nueva foto de fachada a Firebase Storage.
            val facadePhotoUrl = storageService.uploadFileToFolder(
                facadephoto,
                "facades"
            )

            // Guarda solo la URL en la columna facade_photo_url.
            subscription.facadePhotoUrl = facadePhotoUrl
            val savedSubscription = repository.save(subscription)

            BaseResponse(
                status = 200,
                data = savedSubscription.toDto(),
                message = "Foto de fachada actualizada correctamente"
            )
        } catch (e: Exception) {
            e.printStackTrace()
            errorLogRepository.save(e.toErrorLog(Modules.SUBSCRIPTION))

            BaseResponse(
                status = 500,
                error = e.message ?: "No se pudo actualizar la foto de fachada"
            )
        }
    }
    @PutMapping
    fun updateSubscription(@RequestBody updatedSubscription: SubscriptionRequest): ResponseEntity<SubscriptionDto> {
        return try {
            val (subscription, actionType) = subscriptionService.updateSubscription(updatedSubscription)
            
            // Registrar la acción en el log
            subscriptionLogRepository.save(
                SubscriptionLog(
                    subscription = subscription,
                    actionType = actionType,
                    planName = subscription.plan?.name,
                    planPrince = subscription.plan?.price ?: 0.0,
                    planId = subscription.plan?.id
                )
            )
            
            ResponseEntity.status(200).body(subscription.toDto())
        } catch (e: Exception) {
            e.printStackTrace()
            errorLogRepository.save(e.toErrorLog(Modules.SUBSCRIPTION))
            objectErrorResponse
        }
    }


    @PutMapping("/profile/update")
    fun updateSubscriptionProfile(@RequestBody updatedSubscriptionRequest: UpdateSubscriptionRequest): ResponseEntity<UpdateSubscriptionRequest>? {
        try {
            val subscription = updatedSubscriptionRequest.id?.let { repository.findById(it).orElse(null) }

            return if (subscription != null) {
                subscription.apply {
                    firstName = updatedSubscriptionRequest.firstName
                    lastName = updatedSubscriptionRequest.lastName
                    dni = updatedSubscriptionRequest.dni
                    phone = updatedSubscriptionRequest.phone
                    address = updatedSubscriptionRequest.address
                }
                repository.save(subscription)

                ResponseEntity.status(HttpStatus.OK).body(subscription.toUpdateSubscriptionRequest())
            } else {
                ResponseEntity.status(HttpStatus.NOT_FOUND).build()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            errorLogRepository.save(e.toErrorLog(Modules.SUBSCRIPTION))

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }

    @PutMapping("/update-location")
    fun updateSubscriptionLocation(
        @RequestParam subscriptionId: Int,
        @RequestParam latitude: Double,
        @RequestParam longitude: Double
    ): ResponseEntity<BaseResponse> {
        return try {
            val subscription = repository.findById(subscriptionId).orElse(null)
                ?: return ResponseEntity.status(404).body(
                    BaseResponse(
                        status = 404,
                        message = "Subscription not found",
                        error = "No se encontró la suscripción con el ID proporcionado"
                    )
                )

            subscription.location = GeoLocation(latitude, longitude)
            repository.save(subscription)
            
            subscriptionLogRepository.save(
                SubscriptionLog(
                    subscription = subscription,
                    actionType = SubscriptionActionType.UPDATE_LOCATION,
                    planName = subscription.plan?.name,
                    planPrince = subscription.plan?.price ?: 0.0,
                    planId = subscription.plan?.id
                )
            )

            ResponseEntity.status(200).body(
                BaseResponse(
                    status = 200,
                    message = "Location updated successfully",
                    data = subscription.toDto()
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
            errorLogRepository.save(e.toErrorLog(Modules.SUBSCRIPTION))
            
            ResponseEntity.status(500).body(
                BaseResponse(
                    status = 500,
                    error = e.message,
                    message = "Error updating location"
                )
            )
        }
    }

    data class UpdateSubscriptionRequest(
        var id: Int? = null,
        var firstName: String,
        var lastName: String,
        var dni: String,
        var address: String,
        var phone: String,
    )

    data class UpdateLocationRequest(
        val subscriptionId: Int,
        val location: GeoLocation
    )

    data class SubscriptionBasicInfoDto(
        val id: Int?,
        val nombre: String?,
        val ip: String?,
        val facturasNoPagadas: Int
    )

    data class UnpaidAutoCutSubscriptionsResponse(
        val total: Int,
        val clientes: List<SubscriptionBasicInfoDto>
    )

    @GetMapping("/unpaid-auto-cut")
    fun getUnpaidAutoCutSubscriptions(): ResponseEntity<String> {
        return try {
            val subscriptions = repository.findSubscriptionsWithUnpaidAndAutoCutFlagActivePayments()
            val clientsList = subscriptions.map {
                SubscriptionBasicInfoDto(
                    id = it.id,
                    nombre = "${it.firstName} ${it.lastName}",
                    ip = it.ip,
                    facturasNoPagadas = it.payments.count { p -> p.paid == false }
                )
            }
            
            // Cargar el archivo HTML desde los recursos estáticos
            val resource = ClassPathResource("static/unpaid-auto-cut.html")
            var htmlContent = resource.inputStream.readAllBytes().toString(Charsets.UTF_8)
            
            // Generar las filas de la tabla
            val tablasClientesHtml = clientsList.joinToString("") { cliente ->
                """
                <tr>
                    <td>${cliente.id ?: ""}</td>
                    <td>${cliente.nombre ?: ""}</td>
                    <td>${cliente.ip ?: ""}</td>
                    <td>${cliente.facturasNoPagadas}</td>
                </tr>
                """.trimIndent()
            }
            
            // Reemplazar los placeholders con datos dinámicos
            htmlContent = htmlContent.replace("{{TOTAL_CLIENTES}}", clientsList.size.toString())
            htmlContent = htmlContent.replace("{{TABLA_CLIENTES}}", tablasClientesHtml)
            
            ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(htmlContent)
        } catch (e: Exception) {
            e.printStackTrace()
            errorLogRepository.save(e.toErrorLog(Modules.SUBSCRIPTION))
            ResponseEntity.status(500).body("<h1>Error al procesar la solicitud</h1><p>${e.message}</p>")
        }
    }

    @GetMapping("/unpaid-auto-cut-excel")
    fun getUnpaidAutoCutSubscriptionsExcel(): ResponseEntity<ByteArray> {
        return try {
            val subscriptions = repository.findSubscriptionsWithUnpaidAndAutoCutFlagActivePayments()
            val clientsList = subscriptions.map {
                SubscriptionBasicInfoDto(
                    id = it.id,
                    nombre = "${it.firstName} ${it.lastName}",
                    ip = it.ip,
                    facturasNoPagadas = it.payments.count { p -> p.paid == false }
                )
            }
            
            // Crear el archivo de Excel utilizando Apache POI
            val workbook = XSSFWorkbook()
            val sheet = workbook.createSheet("Clientes con Facturas Pendientes")
            
            // Crear la primera fila con los encabezados
            val headerRow = sheet.createRow(0)
            headerRow.createCell(0).setCellValue("ID")
            headerRow.createCell(1).setCellValue("Nombre")
            headerRow.createCell(2).setCellValue("IP")
            headerRow.createCell(3).setCellValue("Facturas Pendientes")
            
            // Llenar las filas con los datos de los clientes
            var rowNum = 1
            for (cliente in clientsList) {
                val row = sheet.createRow(rowNum++)
                row.createCell(0).setCellValue(cliente.id?.toString() ?: "")
                row.createCell(1).setCellValue(cliente.nombre ?: "")
                row.createCell(2).setCellValue(cliente.ip ?: "")
                row.createCell(3).setCellValue(cliente.facturasNoPagadas.toDouble())
            }
            
            // Ajustar el ancho de las columnas automáticamente
            for (i in 0..3) {
                sheet.autoSizeColumn(i)
            }
            
            // Guardar el libro de Excel en un objeto ByteArrayOutputStream
            val stream = ByteArrayOutputStream()
            workbook.write(stream)
            workbook.close()
            
            // Configurar los headers para la descarga directa del archivo
            val headers = HttpHeaders()
            headers.contentType = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
            headers.contentDisposition = ContentDisposition
                .builder("attachment")
                .filename("clientes_facturas_pendientes.xlsx")
                .build()
            headers.contentLength = stream.size().toLong()
            
            // Retornar el archivo directamente para descarga
            ResponseEntity.ok()
                .headers(headers)
                .body(stream.toByteArray())
        } catch (e: Exception) {
            e.printStackTrace()
            errorLogRepository.save(e.toErrorLog(Modules.SUBSCRIPTION))
            ResponseEntity.status(500).body(null)
        }
    }

    @GetMapping("debtors-with-active-subscription-report-document")
    fun getDebtorsWithActiveSubscriptionReport(): ResponseEntity<DownloadDocumentDto> {
        return try {
            val subscriptions =
                repository.getDebtorsWithActiveSubscriptionReport()
                    .map { Pair((it[0] as Subscription).toDto(), (it[1] as Double)) }
            val response = createDebtAmountSubscriptionDocument(subscriptions, "clientes_deudores")
            ResponseEntity.ok().body(response)
        } catch (e: Exception) {
            e.printStackTrace()
            errorLogRepository.save(e.toErrorLog(Modules.SUBSCRIPTION))

            ResponseEntity.status(500).body(null)
        }
    }

    @GetMapping("debtors-with-cancelled-subscription-report-document")
    fun getDebtorsWithCancelledSubscriptionReport(): ResponseEntity<DownloadDocumentDto> {
        return try {
            val subscriptions =
                repository.getDebtorsWithCancelledServiceReport()
                    .map { Pair((it[0] as Subscription).toDto(), (it[1] as Double)) }
            val response = createDebtAmountSubscriptionDocument(subscriptions, "clientes_deudores")
            ResponseEntity.ok().body(response)
        } catch (e: Exception) {
            e.printStackTrace()
            errorLogRepository.save(e.toErrorLog(Modules.SUBSCRIPTION))

            ResponseEntity.status(500).body(null)
        }
    }

    @GetMapping("debtors-with-cut-report-document")
    fun getDebtorsWithCutReport(): ResponseEntity<DownloadDocumentDto> {
        return try {
            val subscriptions =
                repository.getDebtorsWithCut().map { Pair((it[0] as Subscription).toDto(), (it[1] as Double)) }
            val response = createDebtAmountSubscriptionDocument(subscriptions, "clientes_candidatos_a_corte")
            ResponseEntity.ok().body(response)
        } catch (e: Exception) {
            e.printStackTrace()
            errorLogRepository.save(e.toErrorLog(Modules.SUBSCRIPTION))

            ResponseEntity.status(500).body(null)
        }
    }

    @GetMapping("with-payment-commitment-report-document")
    fun getWithPaymentCommitmentReport(): ResponseEntity<DownloadDocumentDto> {
        return try {
            val subscriptions: List<SubscriptionDto> = repository.getWithPaymentCommitment().map { it.toDto() }
            val response = createGenericSubscriptionDocument(subscriptions, "clientes_con-compromiso-de-pago")
            return ResponseEntity.ok().body(response)
        } catch (e: Exception) {
            e.printStackTrace()
            errorLogRepository.save(e.toErrorLog(Modules.SUBSCRIPTION))

            return ResponseEntity.status(500).body(null)
        }
    }

    @GetMapping("suspended-report-document")
    fun getSuspendedSubscriptionsReport(): ResponseEntity<DownloadDocumentDto> {
        return try {
            val subscriptions: List<SubscriptionDto> =
                repository.findByServiceStatus(ServiceStatus.SUSPENDED).map { it.toDto() }
            val response = createGenericSubscriptionDocument(subscriptions, "clientes_suspendidos")
            return ResponseEntity.ok().body(response)
        } catch (e: Exception) {
            e.printStackTrace()
            errorLogRepository.save(e.toErrorLog(Modules.SUBSCRIPTION))

            return ResponseEntity.status(500).body(null)
        }
    }

    @GetMapping("cutoff-report-document")
    fun getCutOffSubscriptionsReport(): ResponseEntity<DownloadDocumentDto> {
        return try {
            val subscriptions: List<SubscriptionDto> =
                repository.findByServiceStatus(ServiceStatus.CUT_OFF).map { it.toDto() }
            val response = createGenericSubscriptionDocument(subscriptions, "clientes_cortados")
            return ResponseEntity.ok().body(response)
        } catch (e: Exception) {
            e.printStackTrace()
            errorLogRepository.save(e.toErrorLog(Modules.SUBSCRIPTION))

            return ResponseEntity.status(500).body(null)
        }
    }


    @GetMapping("last-month-debtors-report-document")
    fun getDebtorsFromLastMonth(): ResponseEntity<DownloadDocumentDto> {
        return try {
            val lastMonth = Calendar.getInstance()
            lastMonth.add(Calendar.MONTH, -1)
            val initialDate = lastMonth.getFirstDayOfMonthInMillis()
            val endDate = lastMonth.getLastDayOfMonthInMillis()
            val subscriptions: List<SubscriptionDto> =
                repository.getDebtorsFromLastMonth(initialDate, endDate).map { it.toDto() }
            val response = createGenericSubscriptionDocument(subscriptions, "clientes_cortados")
            return ResponseEntity.ok().body(response)
        } catch (e: Exception) {
            e.printStackTrace()
            errorLogRepository.save(e.toErrorLog(Modules.SUBSCRIPTION))

            return ResponseEntity.status(500).body(null)
        }
    }


    private fun createGenericSubscriptionDocument(
        subscriptions: List<SubscriptionDto>,
        documentName: String
    ): DownloadDocumentDto {
        // Crear el archivo de Excel utilizando Apache POI
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet(documentName)

        // Crear la primera fila con los encabezados
        val headerRow = sheet.createRow(0)
        headerRow.createCell(0).setCellValue("DNI")
        headerRow.createCell(1).setCellValue("Nombres")
        headerRow.createCell(2).setCellValue("Apellidos")
        headerRow.createCell(2).setCellValue("Telefono")
        headerRow.createCell(3).setCellValue("Direccion")

        // Llenar las filas restantes con los datos de los pagos
        var rowNum = 1
        for (subscription in subscriptions) {
            val row = sheet.createRow(rowNum++)
            row.createCell(0).setCellValue(subscription.dni)
            row.createCell(1).setCellValue(subscription.firstName)
            row.createCell(2).setCellValue(subscription.lastName)
            row.createCell(2).setCellValue(subscription.phone)
            row.createCell(3).setCellValue("${subscription.place?.name} ${subscription.address}")
        }

        // Guardar el libro de Excel en un objeto ByteArrayOutputStream
        val stream = ByteArrayOutputStream()
        workbook.write(stream)

        // Crear la respuesta HTTP con los datos del archivo de Excel
        val bytes = stream.toByteArray()

        val bytesToBase64 = Base64.getEncoder().encodeToString(bytes)

        return DownloadDocumentDto(name = documentName, type = "xlsx", base64 = bytesToBase64)
    }

    private fun createDebtAmountSubscriptionDocument(
        dataList: List<Pair<SubscriptionDto, Double>>,
        documentName: String,
    ): DownloadDocumentDto {
        // Crear el archivo de Excel utilizando Apache POI
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet(documentName)

        // Crear la primera fila con los encabezados
        val headerRow = sheet.createRow(0)
        headerRow.createCell(0).setCellValue("DNI")
        headerRow.createCell(1).setCellValue("Nombres")
        headerRow.createCell(2).setCellValue("Apellidos")
        headerRow.createCell(3).setCellValue("Deuda")
        headerRow.createCell(4).setCellValue("Ip")
        headerRow.createCell(5).setCellValue("Telefono")
        headerRow.createCell(6).setCellValue("Direccion")

        // Llenar las filas restantes con los datos de los pagos
        var rowNum = 1
        for (data in dataList) {
            val row = sheet.createRow(rowNum++)
            row.createCell(0).setCellValue(data.first.dni)
            row.createCell(1).setCellValue(data.first.firstName)
            row.createCell(2).setCellValue(data.first.lastName)
            row.createCell(3).setCellValue(data.second.toString())
            row.createCell(4).setCellValue(data.first.ip)
            row.createCell(5).setCellValue(data.first.phone)

            row.createCell(6).setCellValue("${data.first.place?.name} - ${data.first.address}")
        }

        // Guardar el libro de Excel en un objeto ByteArrayOutputStream
        val stream = ByteArrayOutputStream()
        workbook.write(stream)

        // Crear la respuesta HTTP con los datos del archivo de Excel
        val bytes = stream.toByteArray()

        val bytesToBase64 = Base64.getEncoder().encodeToString(bytes)

        return DownloadDocumentDto(name = documentName, type = "xlsx", base64 = bytesToBase64)
    }


    @GetMapping("/all")
    fun getAllSubscriptions(): ResponseEntity<List<SubscriptionDto>> {
        return try {
            val subscriptions: List<SubscriptionDto> = repository.findAll().map { it.toDto() }
            ResponseEntity.status(200).body(subscriptions)
        } catch (e: Exception) {
            e.printStackTrace()
            errorLogRepository.save(e.toErrorLog(Modules.SUBSCRIPTION))
            listErrorResponse
        }
    }

    @GetMapping("debtors")
    fun getDebtors(): ResponseEntity<List<SubscriptionDto>>? {
        return try {
            val subscriptions: List<SubscriptionDto> = repository.getDebtors().map { it.toDto() }
            ResponseEntity.status(200).body(subscriptions)
        } catch (e: Exception) {
            e.printStackTrace()
            errorLogRepository.save(e.toErrorLog(Modules.SUBSCRIPTION))

            listErrorResponse
        }
    }

    @GetMapping("find/dni")
    fun find(@RequestParam(value = "dni") dni: String): ResponseEntity<List<SubscriptionDto>> {
        return try {
            val result = repository.findByDni(dni)
            ResponseEntity.status(200).body(result.map { it.toDto() })
        } catch (e: Exception) {
            e.printStackTrace()
            errorLogRepository.save(e.toErrorLog(Modules.SUBSCRIPTION))

            listErrorResponse
        }
    }

    @GetMapping("find/nameAndLastName")
    fun findByNameAndLastName(
        @RequestParam(value = "name", required = false) name: String?,
        @RequestParam(value = "lastName", required = false) lastName: String?
    ): ResponseEntity<List<SubscriptionDto>> {
        val results = when {
            !name.isNullOrEmpty() && !lastName.isNullOrEmpty() -> repository.findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCaseAndServiceStatusNot(
                name,
                lastName
            )

            !name.isNullOrEmpty() -> repository.findByFirstNameContainingIgnoreCase(name)
            !lastName.isNullOrEmpty() -> repository.findByLastNameContainingIgnoreCase(lastName)
            else -> emptyList()
        }.map { it.toDto() }
        return ResponseEntity.ok(results)
    }

    @GetMapping("find/ip")
    fun findByIP(@RequestParam(value = "ip") ip: String): ResponseEntity<List<SubscriptionDto>> {
        return try {
            val result = repository.findTop20ByIpContainingIgnoreCase(ip)
            ResponseEntity.status(200).body(result.map { it.toDto() })
        } catch (e: Exception) {
            e.printStackTrace()
            errorLogRepository.save(e.toErrorLog(Modules.SUBSCRIPTION))

            listErrorResponse
        }
    }

    @GetMapping("fastSearch")
    fun findByNameOrLastName(@RequestParam("keyword") keyword: String): ResponseEntity<List<SubscriptionSearchDto>> {
        return try {

            val fullNameSplit = keyword.split(" ")

            when (fullNameSplit.size) {
                2 -> {
                    val result =
                        repository.findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCaseAndServiceStatusNot(
                            fullNameSplit[0],
                            fullNameSplit[1]
                        )
                    ResponseEntity.status(200).body(result.map { it.toSearchDto() })
                }

                3 -> {
                    val result =
                        repository.findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCaseAndServiceStatusNot(
                            fullNameSplit[0],
                            fullNameSplit[2]
                        )
                    ResponseEntity.status(200).body(result.map { it.toSearchDto() })
                }

                else -> {
                    ResponseEntity.status(200).body(emptyList())
                }
            }

            val result = repository.searchByNameOrLastName(keyword)
            ResponseEntity.status(200).body(result.map { it.toSearchDto() })
        } catch (e: Exception) {
            e.printStackTrace()
            errorLogRepository.save(e.toErrorLog(Modules.SUBSCRIPTION))

            ResponseEntity.status(500).body(null)
        }
    }

    fun String.toddMMyyyyDate(): Calendar {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = SimpleDateFormat(DATE_FORMAT).parse(this).time
        return calendar
    }

    @PutMapping("update-customer-data")
    fun updateCustomerData(@RequestBody updateCustomerData: UpdateSubscriptionData): ResponseEntity<SubscriptionDto>? {
        try {
            val subscription = updateCustomerData.subscriptionId.let { repository.findById(it).orElse(null) }

            return if (subscription != null) {
                subscription.apply {
                    firstName = updateCustomerData.name
                    lastName = updateCustomerData.lastName
                    dni = updateCustomerData.dni
                    phone = updateCustomerData.phone
                    address = updateCustomerData.address
                    email = updateCustomerData.email
                }

                if(updateCustomerData.placeId!=null){
                    val place = placeRepository.findById(updateCustomerData.placeId!!)
                    subscription.place = place.get()
                }

                repository.save(subscription)

                ResponseEntity.status(HttpStatus.OK).body(subscription.toDto())
            } else {
                ResponseEntity.status(HttpStatus.NOT_FOUND).build()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            errorLogRepository.save(e.toErrorLog(Modules.SUBSCRIPTION))
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }

    @GetMapping("find/date")
    fun find(
        @RequestParam(value = "startDate") startDate: String,
        @RequestParam(value = "endDate") endDate: String
    ): ResponseEntity<List<SubscriptionDto>> {
        return try {

            val initDate = Calendar.getInstance().apply {
                timeInMillis = SimpleDateFormat(DATE_FORMAT).parse(startDate).time
            }.timeInMillis

            val finalDate = Calendar.getInstance().apply {
                timeInMillis = SimpleDateFormat(DATE_FORMAT).parse(endDate).time
            }.timeInMillis

            if (initDate == finalDate) {
                val calendar = Calendar.getInstance().apply { timeInMillis = initDate }
                calendar.add(Calendar.DAY_OF_MONTH, 1)
                val nextDay = calendar.timeInMillis
                val result = repository.findBySubscriptionDateGreaterThanEqualAndSubscriptionDateLessThanEqual(
                    initDate,
                    nextDay
                )
                ResponseEntity.status(200).body(result.map { it.toDto() })
            } else {
                val result = repository.findBySubscriptionDateGreaterThanEqualAndSubscriptionDateLessThanEqual(
                    initDate,
                    finalDate
                )
                ResponseEntity.status(200).body(result.map { it.toDto() })
            }

        } catch (e: Exception) {
            e.printStackTrace()
            errorLogRepository.save(e.toErrorLog(Modules.SUBSCRIPTION))
            listErrorResponse
        }
    }

    @GetMapping("/locations")
    fun getSubscriptionsLocations(): ResponseEntity<List<ClienteUbicacionDto>> {
        return try {
            val subscriptions = repository.findAll()
            
            // Filtramos las suscripciones que tienen ubicación válida
            val subscriptionsWithLocation = subscriptions
                .filter { it.location != null && it.location!!.latitude != 0.0 && it.location!!.longitude != 0.0 }
                .map { subscription ->
                    // Calcular datos relevantes
                    val pendingInvoices = subscription.payments
                        ?.filter { !it.paid }
                        ?.size ?: 0
                    
                    val totalDebt = subscription.payments
                        ?.filter { !it.paid }
                        ?.sumOf { it.amountToPay }
                        ?: 0.0
                    
                    ClienteUbicacionDto(
                        id = subscription.id!!,
                        firstName = subscription.firstName ?: "",
                        lastName = subscription.lastName ?: "",
                        plan = subscription.plan?.name,
                        location = subscription.location!!.toDto(),
                        serviceStatus = subscription.serviceStatus,
                        address = subscription.address,
                        phone = subscription.phone,
                        dni = subscription.dni,
                        ip = subscription.ip,
                        subscriptionDate = subscription.subscriptionDate,
                        lastCutOffDate = subscription.lastCutOffDate,
                        pendingInvoiceQuantity = pendingInvoices,
                        totalDebt = totalDebt,
                        place = subscription.place?.name,
                        installationType = subscription.installationType?.name
                    )
                }
            
            ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(subscriptionsWithLocation)
        } catch (e: Exception) {
            e.printStackTrace()
            errorLogRepository.save(e.toErrorLog(Modules.SUBSCRIPTION))
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null)
        }
    }

    @GetMapping("/logs/summary")
    fun getSubscriptionLogSummary(): ResponseEntity<SubscriptionLogSummaryResponse> {
        return try {
            val startDate = Calendar.getInstance().apply {
                add(Calendar.MONTH, -12)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.time

            val summary = subscriptionLogRepository.getSubscriptionLogSummary(startDate)
                .map {
                    val year = (it["year"] as Number).toInt()
                    val month = (it["month"] as Number).toInt()
                    SubscriptionLogSummaryDto(
                        actionType = it["actionType"] as SubscriptionActionType,
                        count = (it["count"] as Number).toLong(),
                        period = YearMonth.of(year, month)
                    )
                }

            val totalOperations = summary.sumOf { it.count }

            // Agrupar por tipo de acción
            val groupedSummary = summary
                .groupBy { it.actionType }
                .mapValues { (actionType, logs) ->
                    ActionTypeSummary(
                        actionType = actionType,
                        totalCount = logs.sumOf { it.count },
                        monthlyDetails = logs
                            .sortedWith(
                                compareByDescending<SubscriptionLogSummaryDto> { it.period }
                                    .thenByDescending { it.count }
                            )
                            .map { MonthlyDetailDto(count = it.count, period = it.period) }
                    )
                }
                .toList()
                .sortedByDescending { it.second.totalCount }
                .toMap()

            ResponseEntity.ok(
                SubscriptionLogSummaryResponse(
                    summary = groupedSummary,
                    totalOperations = totalOperations
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
            errorLogRepository.save(e.toErrorLog(Modules.SUBSCRIPTION))
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }

    @PostMapping("/generate-address-list-cancelled-subscriptions")
    fun generateCancelledSubscriptionsAddressList(): ResponseEntity<Map<String, Any>> {
        return try {
            val result = subscriptionService.generateAddressListForCancelledSubscriptions()
            
            val response = mapOf(
                "processedCount" to result.processedCount,
                "createdCount" to result.createdCount,
                "alreadyExistsCount" to result.alreadyExistsCount,
                "deletedCount" to result.deletedCount,
                "errorCount" to result.errorCount,
                "totalNotCreated" to result.totalNotCreated,
                "message" to result.message,
                "createdSubscriptions" to result.createdSubscriptions,
                "alreadyExistsSubscriptions" to result.alreadyExistsSubscriptions,
                "notProcessedSubscriptions" to result.notProcessedSubscriptions,
                "failedSubscriptions" to result.failedSubscriptions
            )
            
            ResponseEntity.ok(response)
        } catch (e: Exception) {
            e.printStackTrace()
            errorLogRepository.save(e.toErrorLog(Modules.SUBSCRIPTION))
            
            val errorResponse = mapOf(
                "processedCount" to 0,
                "message" to "Error al generar Filter Rules: ${e.message}"
            )
            
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse)
        }
    }

}

fun OnuDto.toAuthorizationRequest(customerFullName: String) = OnuAuthorizationRequest(
    olt_id = olt_id,
    pon_type = pon_type,
    board = board,
    port = port,
    sn = sn,
    vlan = 1.toString(),
    onu_type = onu_type_name,
    zone = "Zone 1",
    name = customerFullName,
    onu_mode = "Routing",
    custom_profile = "Generic_1"
)

private fun Subscription.toUpdateSubscriptionRequest() = SubscriptionController.UpdateSubscriptionRequest(
    id = this.id,
    firstName = this.firstName ?: "",
    lastName = this.lastName ?: "",
    dni = this.dni ?: "",
    address = this.address ?: "",
    phone = this.phone ?: ""
)

fun Place?.toDto() = PlaceDto(
    id = this?.id,
    name = this?.name,
    latitude = this?.latitude,
    longitude = this?.longitude
)

fun List<Place>.toDtoList() = map {
    PlaceDto(
        id = it.id,
        name = it.name,
        latitude = it.latitude,
        longitude = it.longitude
    )
}

fun Plan.toDto(): PlanDto = PlanDto(
    id = this.id,
    name = this.name,
    price = this.price,
    downloadSpeed = this.downloadSpeed,
    uploadSpeed = this.uploadSpeed,
    type = this.type,
)

fun Subscription.toSearchDto() = SubscriptionSearchDto(
    id = id!!,
    fullName = "${firstName?.trim()} ${lastName?.trim()}"
)


