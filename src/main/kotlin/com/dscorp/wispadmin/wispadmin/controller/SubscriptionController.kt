package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.data.model.*
import com.dscorp.wispadmin.wispadmin.data.model.util.BaseResponse
import com.dscorp.wispadmin.wispadmin.dto.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import com.dscorp.wispadmin.wispadmin.repository.*
import com.dscorp.wispadmin.wispadmin.requestbody.*
import com.dscorp.wispadmin.wispadmin.requestbody.smartoltrequest.MoveOnuRequest
import com.dscorp.wispadmin.wispadmin.requestbody.smartoltrequest.OnuAuthorizationRequest
import com.dscorp.wispadmin.wispadmin.service.SubscriptionService
import com.dscorp.wispadmin.wispadmin.service.BorneValidationResult
import org.apache.poi.xssf.usermodel.XSSFWorkbook
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
class SubscriptionController(
    private val repository: SubscriptionRepository,
    private val subscriptionService: SubscriptionService,
    private val placeRepository: PlaceRepository,
    private val planRepository: PlanRepository,
    private val napBoxRepository: NapBoxRepository,
    private val networkDeviceRepository: NetworkDeviceRepository,
    private val couponRepository: CouponRepository,
    private val subscriptionLogRepository: SubscriptionLogRepository,
    private val storageService: FirebaseStorageService
) {

    @GetMapping("/findByElectronicPayerName")
    fun findByElectronicPayerName(@RequestParam("electronicPayerName") electronicPayerName: String): BaseResponse {
        val electronicPayers = repository.findByElectronicPayerName(electronicPayerName).map { it.toPayerFinderResultDto() }

        return BaseResponse(
            status = 200,
            message = "ok",
            data = electronicPayers
        )
    }

    @PutMapping("/changeNapBox")
    fun changeNapBox(@RequestBody request: MoveOnuRequest): ResponseEntity<NapBoxDto> {
        val subscription = subscriptionService.changeNapBox(request)
        subscriptionLogRepository.save(
            SubscriptionLog(
                subscription = subscription,
                actionType = SubscriptionActionType.CHANGE_NAP_BOX,
                planName = subscription.plan?.name,
                planPrice = subscription.plan?.price ?: 0.0,
                planId = subscription.plan?.id
            )
        )
        return ResponseEntity.ok(subscription.napBox?.toDto())
    }

    private fun monthRange(monthsAgo: Long): Pair<LocalDateTime, LocalDateTime> {
        val zone = ZoneId.of("America/Lima")
        val targetMonth = LocalDate.now(zone).minusMonths(monthsAgo)

        val startDate = targetMonth.withDayOfMonth(1).atStartOfDay()
        val endDate = targetMonth.plusMonths(1).withDayOfMonth(1).atStartOfDay()

        return startDate to endDate
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
                    planPrice = subscription.plan?.price ?: 0.0,
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
        }
    }

    @GetMapping("/{subscriptionId}")
    fun getSubscription(@PathVariable subscriptionId: Int): ResponseEntity<SubscriptionDto> {
        val subscription = repository.findById(subscriptionId).orElse(null)
        return if (subscription != null) {
            ResponseEntity.ok(subscription.toDto())
        } else {
            ResponseEntity.notFound().build()
        }
    }

    @PostMapping("/login")
    fun login(@RequestBody user: LoginBody): ResponseEntity<SubscriptionUserDto> {
        val foundUser = repository.logIn(user.username, user.password)
        if (foundUser != null) {
            return ResponseEntity.ok(foundUser.toSubscriptionUserDto())
        }
        return ResponseEntity.notFound().build()
    }

    @PostMapping("/generate-simple-queues")
    fun generateSimpleQueue(): BaseResponse {
        val stats = subscriptionService.createSubscriptionsSimpleQueue().get()

        return BaseResponse(
            status = 200,
            message = "Colas generadas correctamente",
            data = stats
        )
    }

    @PutMapping("/migration")
    fun migrateToFiber(@RequestBody request: MigrationRequest): BaseResponse {
        val subscription = subscriptionService.migrateToFiber(request)
        subscriptionLogRepository.save(
            SubscriptionLog(
                subscription = subscription,
                actionType = SubscriptionActionType.MIGRATE_SUBSCRIPTION,
                planName = subscription.plan?.name,
                planPrice = subscription.plan?.price ?: 0.0,
                planId = subscription.plan?.id
            )
        )
        return BaseResponse(status = 200, data = subscription.toDto())
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
        }
    }

    @PutMapping("/reactivate-service")
    fun reactivateService(
        @RequestParam subscriptionId: Int,
        @RequestParam responsibleId: Int,
        @RequestParam(required = false) notes: String? = null,
        @RequestParam(required = false) newBorneNumber: String? = null
    ): ResponseEntity<Any> {
        subscriptionService.reactivateService(subscriptionId, responsibleId, notes, newBorneNumber)
        val response = ReactivateServiceResponseDto(
            message = "Servicio reactivado correctamente",
            subscriptionId = subscriptionId
        )
        return ResponseEntity.ok(response)
    }

    @GetMapping("/{subscriptionId}/reactivation-validation")
    fun validateReactivation(@PathVariable subscriptionId: Int): ResponseEntity<Any> {
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

        return ResponseEntity.ok(response)
    }

    @GetMapping("/napbox/{napBoxId}/available-bornes")
    fun getAvailableBornes(@PathVariable napBoxId: Int): ResponseEntity<BaseResponse> {
        val availableBornes = subscriptionService.borneManagementService.getAvailableBornes(napBoxId)
        val response = BaseResponse(
            status = 200,
            data = availableBornes,
            message = "Bornes disponibles obtenidos correctamente"
        )
        return ResponseEntity.ok(response)
    }

    @GetMapping("/napbox/{napBoxId}/borne-status")
    fun getBorneStatus(@PathVariable napBoxId: Int): ResponseEntity<BaseResponse> {
        val borneStatus = (1..16).associate { borneNumber ->
            borneNumber.toString() to subscriptionService.borneManagementService.isBorneAvailable(napBoxId, borneNumber.toString())
        }
        val response = BaseResponse(
            status = 200,
            data = borneStatus,
            message = "Estado de bornes obtenido correctamente"
        )
        return ResponseEntity.ok(response)
    }

    @PutMapping("/update-location/v2")
    fun updateLocationV2(@RequestBody request: UpdateLocationRequest): ResponseEntity<BaseResponse> {
        subscriptionService.updateSubscriptionLocation(request.subscriptionId, request.location)
        val response = BaseResponse(status = HttpStatus.OK.value(), message = "Ubicación actualizada correctamente")
        return ResponseEntity.ok(response)
    }

    @PutMapping("/payment-commitment")
    fun registerPaymentCommitment(@RequestParam subscriptionId: Int): ResponseEntity<Unit> {
        subscriptionService.registerPaymentCommitment(subscriptionId)
        return ResponseEntity.ok(null)
    }

    @GetMapping("/apply-coupon/{code}")
    fun applyCoupon(@PathVariable code: String): ResponseEntity<Coupon> {
        val coupon = couponRepository.findByCodeAndExpirationDateGreaterThan(code, Date().time)
            ?: return ResponseEntity.status(404).body(Coupon())
        return ResponseEntity.ok(coupon)
    }

    @PutMapping("/update-plan")
    fun updatePlan(@RequestBody updatePlanData: UpdateSubscriptionPlanBody): ResponseEntity<SubscriptionDto> {
        val subscription = subscriptionService.updateSubscriptionPlan(
            subscriptionId = updatePlanData.subscriptionId,
            planId = updatePlanData.planId
        )

        subscriptionLogRepository.save(
            SubscriptionLog(
                subscription = subscription,
                actionType = SubscriptionActionType.CHANGE_PLAN,
                planName = subscription.plan?.name,
                planPrice = subscription.plan?.price ?: 0.0,
                planId = subscription.plan?.id
            )
        )

        return ResponseEntity.ok(subscription.toDto())
    }

    @PutMapping("/update-subscription-data")
    fun updatePlanData(@RequestBody updateSubscriptionData: UpdateSubscriptionDataBody): ResponseEntity<Unit> {
        subscriptionService.updateSubscriptionData(updateSubscriptionData)
        return ResponseEntity.ok(null)
    }

    @PutMapping("/cortarDeudores")
    fun cutInternet(): ResponseEntity<CutServiceSummaryDto> =
        ResponseEntity.ok(subscriptionService.cutInternetService())

    @PutMapping("/cancel-subscription")
    fun cancelSubscription(@RequestParam("subscriptionId") subscriptionId: Int, @RequestParam("responsibleId") responsibleId: Int): ResponseEntity<Any> {
        subscriptionService.cancelService(
            idSubscription = subscriptionId,
            onSuccess = { subscription ->
                subscriptionLogRepository.save(
                    SubscriptionLog(
                        subscription = subscription,
                        actionType = SubscriptionActionType.CANCEL_SUBSCRIPTION,
                        planName = subscription.plan?.name,
                        planPrice = subscription.plan?.price ?: 0.0,
                        planId = subscription.plan?.id,
                        responsibleId = responsibleId.toString()
                    )
                )
            }
        )
        return ResponseEntity.ok(null)
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
                            planPrice = it.plan?.price ?: 0.0,
                            planId = it.plan?.id
                        )
                    )
                }
            )

            BaseResponse(data = subscription, status = 200)
        } catch (e: DataIntegrityViolationException) {
            BaseResponse(
                status = 409,
                error = "Este usuario ya se encuentra registrado",
            )
        }
    }

    @PostMapping(
        "/with-facade-photo",
        consumes = [MediaType.MULTIPART_FORM_DATA_VALUE]
    )
    fun newSubcriptionWithFacade(
        @RequestPart("subscription") newSubscription: SubscriptionRequest,
        @RequestPart("facadePhoto") facadephoto: MultipartFile
    ): BaseResponse {
        return try {
            val facadePhotoUrl = storageService.uploadFileToFolder(facadephoto, "facades")

            newSubscription.facadePhotoUrl = facadePhotoUrl

            val subscription = subscriptionService.registerSubscription(
                newSubscription = newSubscription,
                onSuccess = {
                    subscriptionLogRepository.save(
                        SubscriptionLog(
                            subscription = it,
                            actionType = SubscriptionActionType.NEW_SUBSCRIPTION,
                            planName = it.plan?.name,
                            planPrice = it.plan?.price ?: 0.0,
                            planId = it.plan?.id
                        )
                    )
                }
            )

            BaseResponse(data = subscription, status = 200)
        } catch (e: DataIntegrityViolationException) {
            BaseResponse(
                status = 409,
                error = "Este usuario no se encuentra registrado",
            )
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
        val subscription = repository.findById(subscriptionId).orElseThrow {
            Exception("Suscripcion no encontrada")
        }

        val facadePhotoUrl = storageService.uploadFileToFolder(facadephoto, "facades")

        subscription.facadePhotoUrl = facadePhotoUrl
        val savedSubscription = repository.save(subscription)

        return BaseResponse(
            status = 200,
            data = savedSubscription.toDto(),
            message = "Foto de fachada actualizada correctamente"
        )
    }

    @PutMapping
    fun updateSubscription(@RequestBody updatedSubscription: SubscriptionRequest): ResponseEntity<SubscriptionDto> {
        val (subscription, actionType) = subscriptionService.updateSubscription(updatedSubscription)

        subscriptionLogRepository.save(
            SubscriptionLog(
                subscription = subscription,
                actionType = actionType,
                planName = subscription.plan?.name,
                planPrice = subscription.plan?.price ?: 0.0,
                planId = subscription.plan?.id
            )
        )

        return ResponseEntity.ok(subscription.toDto())
    }

    @PutMapping("/profile/update")
    fun updateSubscriptionProfile(@RequestBody updatedSubscriptionRequest: UpdateSubscriptionRequest): ResponseEntity<UpdateSubscriptionRequest> {
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
    }

    @PutMapping("/update-location")
    fun updateSubscriptionLocation(
        @RequestParam subscriptionId: Int,
        @RequestParam latitude: Double,
        @RequestParam longitude: Double
    ): ResponseEntity<BaseResponse> {
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
                planPrice = subscription.plan?.price ?: 0.0,
                planId = subscription.plan?.id
            )
        )

        return ResponseEntity.ok(
            BaseResponse(
                status = 200,
                message = "Location updated successfully",
                data = subscription.toDto()
            )
        )
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
        val subscriptions = repository.findSubscriptionsWithUnpaidAndAutoCutFlagActivePayments()
        val clientsList = subscriptions.map {
            SubscriptionBasicInfoDto(
                id = it.id,
                nombre = "${it.firstName} ${it.lastName}",
                ip = it.ip,
                facturasNoPagadas = it.payments.count { p -> p.paid == false }
            )
        }

        val resource = ClassPathResource("static/unpaid-auto-cut.html")
        var htmlContent = resource.inputStream.readAllBytes().toString(Charsets.UTF_8)

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

        htmlContent = htmlContent.replace("{{TOTAL_CLIENTES}}", clientsList.size.toString())
        htmlContent = htmlContent.replace("{{TABLA_CLIENTES}}", tablasClientesHtml)

        return ResponseEntity.ok()
            .contentType(MediaType.TEXT_HTML)
            .body(htmlContent)
    }

    @GetMapping("/unpaid-auto-cut-excel")
    fun getUnpaidAutoCutSubscriptionsExcel(): ResponseEntity<ByteArray> {
        val subscriptions = repository.findSubscriptionsWithUnpaidAndAutoCutFlagActivePayments()
        val clientsList = subscriptions.map {
            SubscriptionBasicInfoDto(
                id = it.id,
                nombre = "${it.firstName} ${it.lastName}",
                ip = it.ip,
                facturasNoPagadas = it.payments.count { p -> p.paid == false }
            )
        }

        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Clientes con Facturas Pendientes")

        val headerRow = sheet.createRow(0)
        headerRow.createCell(0).setCellValue("ID")
        headerRow.createCell(1).setCellValue("Nombre")
        headerRow.createCell(2).setCellValue("IP")
        headerRow.createCell(3).setCellValue("Facturas Pendientes")

        var rowNum = 1
        for (cliente in clientsList) {
            val row = sheet.createRow(rowNum++)
            row.createCell(0).setCellValue(cliente.id?.toString() ?: "")
            row.createCell(1).setCellValue(cliente.nombre ?: "")
            row.createCell(2).setCellValue(cliente.ip ?: "")
            row.createCell(3).setCellValue(cliente.facturasNoPagadas.toDouble())
        }

        for (i in 0..3) {
            sheet.autoSizeColumn(i)
        }

        val stream = ByteArrayOutputStream()
        workbook.write(stream)
        workbook.close()

        val headers = HttpHeaders()
        headers.contentType = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
        headers.contentDisposition = ContentDisposition
            .builder("attachment")
            .filename("clientes_facturas_pendientes.xlsx")
            .build()
        headers.contentLength = stream.size().toLong()

        return ResponseEntity.ok()
            .headers(headers)
            .body(stream.toByteArray())
    }

    @GetMapping("debtors-with-active-subscription-report-document")
    fun getDebtorsWithActiveSubscriptionReport(): ResponseEntity<DownloadDocumentDto> {
        val subscriptions = repository.getDebtorsWithActiveSubscriptionReport()
            .map { Pair((it[0] as Subscription).toDto(), (it[1] as Double)) }
        val response = createDebtAmountSubscriptionDocument(subscriptions, "clientes_deudores")
        return ResponseEntity.ok().body(response)
    }

    @GetMapping("debtors-with-cancelled-subscription-report-document")
    fun getDebtorsWithCancelledSubscriptionReport(): ResponseEntity<DownloadDocumentDto> {
        val subscriptions = repository.getDebtorsWithCancelledServiceReport()
            .map { Pair((it[0] as Subscription).toDto(), (it[1] as Double)) }
        val response = createDebtAmountSubscriptionDocument(subscriptions, "clientes_deudores")
        return ResponseEntity.ok().body(response)
    }

    @GetMapping("debtors-with-cut-report-document")
    fun getDebtorsWithCutReport(): ResponseEntity<DownloadDocumentDto> {
        val subscriptions = repository.getDebtorsWithCut().map { Pair((it[0] as Subscription).toDto(), (it[1] as Double)) }
        val response = createDebtAmountSubscriptionDocument(subscriptions, "clientes_candidatos_a_corte")
        return ResponseEntity.ok().body(response)
    }

    @GetMapping("with-payment-commitment-report-document")
    fun getWithPaymentCommitmentReport(): ResponseEntity<DownloadDocumentDto> {
        val subscriptions: List<SubscriptionDto> = repository.getWithPaymentCommitment().map { it.toDto() }
        val response = createGenericSubscriptionDocument(subscriptions, "clientes_con-compromiso-de-pago")
        return ResponseEntity.ok().body(response)
    }

    @GetMapping("suspended-report-document")
    fun getSuspendedSubscriptionsReport(): ResponseEntity<DownloadDocumentDto> {
        val subscriptions: List<SubscriptionDto> =
            repository.findByServiceStatus(ServiceStatus.SUSPENDED).map { it.toDto() }
        val response = createGenericSubscriptionDocument(subscriptions, "clientes_suspendidos")
        return ResponseEntity.ok().body(response)
    }

    @GetMapping("cutoff-report-document")
    fun getCutOffSubscriptionsReport(): ResponseEntity<DownloadDocumentDto> {
        val subscriptions: List<SubscriptionDto> =
            repository.findByServiceStatus(ServiceStatus.CUT_OFF).map { it.toDto() }
        val response = createGenericSubscriptionDocument(subscriptions, "clientes_cortados")
        return ResponseEntity.ok().body(response)
    }

    @GetMapping("last-month-debtors-report-document")
    fun getDebtorsFromLastMonth(): ResponseEntity<DownloadDocumentDto> {
        val (startDate, endDate) = monthRange(monthsAgo = 1)

        val subscriptions: List<SubscriptionDto> =
            repository.getDebtorsFromLastMonth(startDate, endDate).map { it.toDto() }

        val response = createGenericSubscriptionDocument(subscriptions, "clientes_cortados")
        return ResponseEntity.ok().body(response)
    }

    private fun createGenericSubscriptionDocument(
        subscriptions: List<SubscriptionDto>,
        documentName: String
    ): DownloadDocumentDto {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet(documentName)

        val headerRow = sheet.createRow(0)
        headerRow.createCell(0).setCellValue("DNI")
        headerRow.createCell(1).setCellValue("Nombres")
        headerRow.createCell(2).setCellValue("Apellidos")
        headerRow.createCell(2).setCellValue("Telefono")
        headerRow.createCell(3).setCellValue("Direccion")

        var rowNum = 1
        for (subscription in subscriptions) {
            val row = sheet.createRow(rowNum++)
            row.createCell(0).setCellValue(subscription.dni)
            row.createCell(1).setCellValue(subscription.firstName)
            row.createCell(2).setCellValue(subscription.lastName)
            row.createCell(2).setCellValue(subscription.phone)
            row.createCell(3).setCellValue("${subscription.place?.name} ${subscription.address}")
        }

        val stream = ByteArrayOutputStream()
        workbook.write(stream)

        val bytes = stream.toByteArray()

        val bytesToBase64 = Base64.getEncoder().encodeToString(bytes)

        return DownloadDocumentDto(name = documentName, type = "xlsx", base64 = bytesToBase64)
    }

    private fun createDebtAmountSubscriptionDocument(
        dataList: List<Pair<SubscriptionDto, Double>>,
        documentName: String,
    ): DownloadDocumentDto {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet(documentName)

        val headerRow = sheet.createRow(0)
        headerRow.createCell(0).setCellValue("DNI")
        headerRow.createCell(1).setCellValue("Nombres")
        headerRow.createCell(2).setCellValue("Apellidos")
        headerRow.createCell(3).setCellValue("Deuda")
        headerRow.createCell(4).setCellValue("Ip")
        headerRow.createCell(5).setCellValue("Telefono")
        headerRow.createCell(6).setCellValue("Direccion")

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

        val stream = ByteArrayOutputStream()
        workbook.write(stream)

        val bytes = stream.toByteArray()

        val bytesToBase64 = Base64.getEncoder().encodeToString(bytes)

        return DownloadDocumentDto(name = documentName, type = "xlsx", base64 = bytesToBase64)
    }

    @GetMapping("/all")
    fun getAllSubscriptions(): ResponseEntity<List<SubscriptionDto>> =
        ResponseEntity.ok(repository.findAll().map { it.toDto() })

    @GetMapping("debtors")
    fun getDebtors(): ResponseEntity<List<SubscriptionDto>> =
        ResponseEntity.ok(repository.getDebtors().map { it.toDto() })

    @GetMapping("find/dni")
    fun find(@RequestParam(value = "dni") dni: String): ResponseEntity<List<SubscriptionDto>> =
        ResponseEntity.ok(repository.findByDni(dni).map { it.toDto() })

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
    fun findByIP(@RequestParam(value = "ip") ip: String): ResponseEntity<List<SubscriptionDto>> =
        ResponseEntity.ok(repository.findTop20ByIpContainingIgnoreCase(ip).map { it.toDto() })

    @GetMapping("fastSearch")
    fun findByNameOrLastName(@RequestParam("keyword") keyword: String): ResponseEntity<List<SubscriptionSearchDto>> {
        val fullNameSplit = keyword.split(" ")

        when (fullNameSplit.size) {
            2 -> {
                val result =
                    repository.findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCaseAndServiceStatusNot(
                        fullNameSplit[0],
                        fullNameSplit[1]
                    )
                ResponseEntity.ok(result.map { it.toSearchDto() })
            }

            3 -> {
                val result =
                    repository.findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCaseAndServiceStatusNot(
                        fullNameSplit[0],
                        fullNameSplit[2]
                    )
                ResponseEntity.ok(result.map { it.toSearchDto() })
            }

            else -> {
                ResponseEntity.ok(emptyList())
            }
        }

        val result = repository.searchByNameOrLastName(keyword)
        return ResponseEntity.ok(result.map { it.toSearchDto() })
    }

    fun String.toddMMyyyyDate(): Calendar {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = SimpleDateFormat(DATE_FORMAT).parse(this).time
        return calendar
    }

    @PutMapping("update-customer-data")
    fun updateCustomerData(@RequestBody updateCustomerData: UpdateSubscriptionData): ResponseEntity<SubscriptionDto> {
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

            if (updateCustomerData.placeId != null) {
                val place = placeRepository.findById(updateCustomerData.placeId!!)
                subscription.place = place.get()
            }

            repository.save(subscription)

            ResponseEntity.status(HttpStatus.OK).body(subscription.toDto())
        } else {
            ResponseEntity.status(HttpStatus.NOT_FOUND).build()
        }
    }

    @GetMapping("find/date")
    fun find(
        @RequestParam(value = "startDate") startDate: String,
        @RequestParam(value = "endDate") endDate: String
    ): ResponseEntity<List<SubscriptionDto>> {
        val formatter = SimpleDateFormat(DATE_FORMAT)

        val initDate = formatter.parse(startDate)
            .toInstant()
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDateTime()

        val finalDate = formatter.parse(endDate)
            .toInstant()
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDateTime()

        val searchEndDate = if (initDate == finalDate) {
            initDate.plusDays(1)
        } else {
            finalDate
        }

        val result = repository.findBySubscriptionDateGreaterThanEqualAndSubscriptionDateLessThanEqual(
            initDate,
            searchEndDate
        )

        return ResponseEntity.ok(result.map { it.toDto() })
    }

    @GetMapping("/locations")
    fun getSubscriptionsLocations(): ResponseEntity<List<ClienteUbicacionDto>> {
        val subscriptions = repository.findAll()

        val subscriptionsWithLocation = subscriptions
            .filter { it.location != null && it.location!!.latitude != 0.0 && it.location!!.longitude != 0.0 }
            .map { subscription ->
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
                    subscriptionDate = subscription.subscriptionDatetime
                        ?.atZone(java.time.ZoneId.systemDefault())
                        ?.toInstant()
                        ?.toEpochMilli(),
                    lastCutOffDate = subscription.lastCutOffDate,
                    pendingInvoiceQuantity = pendingInvoices,
                    totalDebt = totalDebt,
                    place = subscription.place?.name,
                    installationType = subscription.installationType?.name
                )
            }

        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(subscriptionsWithLocation)
    }

    @GetMapping("/logs/summary")
    fun getSubscriptionLogSummary(): ResponseEntity<SubscriptionLogSummaryResponse> {
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

        return ResponseEntity.ok(
            SubscriptionLogSummaryResponse(
                summary = groupedSummary,
                totalOperations = totalOperations
            )
        )
    }

    @PostMapping("/generate-address-list-cancelled-subscriptions")
    fun generateCancelledSubscriptionsAddressList(): ResponseEntity<Map<String, Any>> {
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

        return ResponseEntity.ok(response)
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
