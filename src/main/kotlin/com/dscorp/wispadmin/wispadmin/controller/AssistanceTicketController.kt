package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.data.model.AssistanceTicket
import com.dscorp.wispadmin.wispadmin.data.model.AssistanceTicketStatus
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.data.model.applyReschedule
import com.dscorp.wispadmin.wispadmin.dto.AssistanceTicketDto
import com.dscorp.wispadmin.wispadmin.repository.*
import com.dscorp.wispadmin.wispadmin.requestbody.AssistanceTicketRequest
import com.dscorp.wispadmin.wispadmin.service.FirebaseStorageService
import com.dscorp.wispadmin.wispadmin.service.TicketNotificationService
import com.dscorp.wispadmin.wispadmin.service.whatsapp.CrmTicketCustomerNotifyService
import com.dscorp.wispadmin.wispadmin.service.whatsapp.CrmTicketLinkService
import com.dscorp.wispadmin.wispadmin.service.whatsapp.CsatSurveyService
import com.dscorp.wispadmin.wispadmin.util.fcm.FcmConstants
import com.dscorp.wispadmin.wispadmin.util.fcm.FcmMessage
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.Message
import com.google.gson.Gson
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.util.*
import org.springframework.http.HttpStatus

@RestController
@RequestMapping("/assistanceTicket")
class AssistanceTicketController(
    private val fcm: FirebaseMessaging,
    private val repository: AssistanceTicketRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val userRepository: UserRepository,
    private val fcmTokenRepository: FcmTokenRepository,
    private val storageService: FirebaseStorageService,
    private val ticketNotificationService: TicketNotificationService,
    private val customerNotifyService: CrmTicketCustomerNotifyService,
    private val crmTicketLinkService: CrmTicketLinkService,
    private val csatSurveyService: CsatSurveyService
) {

    @GetMapping("/byDateRange")
    fun getTicketsByDateRange(
        @RequestParam("status") status: AssistanceTicketStatus,
        @RequestParam("startDate") startDate: Long,
        @RequestParam("endDate") endDate: Long
    ): ResponseEntity<List<AssistanceTicketDto>> {
        val mStartDate = Date(startDate)
        val mEndDate = Date(endDate)

        val assistanceTickets = repository.findAllByStatusAndScheduledAtBetween(
            status = status,
            start = mStartDate,
            end = mEndDate
        )
        return ResponseEntity.ok(assistanceTickets.map { it.toDto() })
    }

    @PutMapping("/assignTicketToUser")
    fun updateTicketStatusToAssigned(
        @RequestParam("ticketId") ticketId: Int,
        @RequestParam("userId") userId: Int,
    ): ResponseEntity<AssistanceTicketDto> {
        val responsibleUser = userRepository.findById(userId).orElseThrow()
        val assistanceTicket = repository.findById(ticketId).orElseThrow()
        val oldStatus = assistanceTicket.status.name

        assistanceTicket.apply {
            status = AssistanceTicketStatus.ASSIGNED
            responsible = responsibleUser
            assignedAt = Date()
        }
        val mTicketDto: AssistanceTicketDto = repository.save(assistanceTicket).toDto()

        ticketNotificationService.notifyTicketAssigned(
            ticketId = ticketId.toLong(),
            oldStatus = oldStatus,
            newStatus = assistanceTicket.status.name,
            assignedTo = "${responsibleUser.name} ${responsibleUser.lastName}"
        )
        customerNotifyService.notifyStatusChange(
            ticket = assistanceTicket,
            oldStatus = oldStatus,
            newStatus = assistanceTicket.status.name,
            assignedTo = "${responsibleUser.name} ${responsibleUser.lastName}"
        )

        FcmMessage(
            title = "Ticket ${assistanceTicket.status.status}",
            message = "El ticket ${assistanceTicket.id} de ${assistanceTicket.subscription?.getFullName()} ha sido asignado a ${responsibleUser.name} ${responsibleUser.lastName}",
            topic = FcmConstants.ASSISTANCE_TICKET_ADMINS,
            data = mTicketDto.toJson(),
            type = FcmMessage.FcmMessageType.ASSISTANCE_TICKET,
            id = mTicketDto.id.toString()
        ).sendNotification(fcm)

        assistanceTicket.subscription?.id?.let {
            fcmTokenRepository.findById(it).ifPresent { customerToken ->
                FcmMessage(
                    title = "Ticket ${assistanceTicket.status.status}",
                    message = "Su ticket ${assistanceTicket.id} ha sido actualizado a asignado",
                    customerToken = customerToken.token,
                    data = mTicketDto.toJson(),
                    type = FcmMessage.FcmMessageType.ASSISTANCE_TICKET,
                    id = mTicketDto.id.toString()
                ).sendNotification(fcm)
            }
        }

        return ResponseEntity.ok(mTicketDto)
    }

    @PutMapping("/{id}/status")
    fun updateTicketStatus(
        @PathVariable id: Int,
        @RequestParam status: AssistanceTicketStatus
    ): ResponseEntity<AssistanceTicketDto> {
        if (status !in setOf(
                AssistanceTicketStatus.IN_PROGRESS,
                AssistanceTicketStatus.RESOLVED,
                AssistanceTicketStatus.REOPEN,
                AssistanceTicketStatus.CLOSED,
                AssistanceTicketStatus.CANCELLED
            )
        ) {
            return ResponseEntity.badRequest().build()
        }
        val assistanceTicket = repository.findById(id).orElseThrow()
        val oldStatus = assistanceTicket.status.name
        assistanceTicket.status = status
        when (status) {
            AssistanceTicketStatus.RESOLVED -> assistanceTicket.resolvedAt = Date()
            AssistanceTicketStatus.CLOSED, AssistanceTicketStatus.CANCELLED -> assistanceTicket.closedAt = Date()
            AssistanceTicketStatus.REOPEN -> {
                assistanceTicket.closedAt = null
                assistanceTicket.resolvedAt = null
            }
            else -> Unit
        }
        val mTicketDto = repository.save(assistanceTicket).toDto()
        ticketNotificationService.notifyTicketStatusChange(
            ticketId = id.toLong(),
            oldStatus = oldStatus,
            newStatus = status.name,
            assignedTo = mTicketDto.assignedTo
        )
        customerNotifyService.notifyStatusChange(
            ticket = assistanceTicket,
            oldStatus = oldStatus,
            newStatus = status.name,
            assignedTo = mTicketDto.assignedTo
        )
        if (status == AssistanceTicketStatus.RESOLVED || status == AssistanceTicketStatus.CLOSED) {
            runCatching { csatSurveyService.scheduleOnTicketClose(assistanceTicket) }
        }
        return ResponseEntity.ok(mTicketDto)
    }

    @GetMapping("/{id}/conversation")
    fun getLinkedConversation(@PathVariable id: Int): ResponseEntity<Map<String, Any>> {
        val conversationId = crmTicketLinkService.getConversationIdForTicket(id)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(mapOf("ticketId" to id, "conversationId" to conversationId))
    }

    @PutMapping("/closeAttendedTicket")
    fun closeAttendedTicket(
        @RequestParam("ticketId") ticketId: Int,
        @RequestParam("userId") userId: Int,
        @RequestParam("image") image: MultipartFile
    ): ResponseEntity<AssistanceTicketDto> {
        val assistanceTicket = repository.findById(ticketId).orElseThrow()
        val oldStatus = assistanceTicket.status.name

        val firebaseUrl = storageService.uploadFileToFolder(image, "tickets")
        assistanceTicket.apply {
            status = AssistanceTicketStatus.CLOSED
            closedAt = Date()
            sheetImageUrl = firebaseUrl
        }
        val mTicketDto: AssistanceTicketDto = repository.save(assistanceTicket).toDto()

        ticketNotificationService.notifyTicketClosed(
            ticketId = ticketId.toLong(),
            oldStatus = oldStatus,
            newStatus = assistanceTicket.status.name
        )
        customerNotifyService.notifyStatusChange(
            ticket = assistanceTicket,
            oldStatus = oldStatus,
            newStatus = assistanceTicket.status.name,
            assignedTo = mTicketDto.assignedTo
        )
        runCatching { csatSurveyService.scheduleOnTicketClose(assistanceTicket) }

        FcmMessage(
            title = "Ticket ${assistanceTicket.status.status}",
            message = "El ticket ${assistanceTicket.id} de ${assistanceTicket.subscription?.getFullName()} se ha marcado como cerrado",
            topic = FcmConstants.ASSISTANCE_TICKET_ADMINS,
            data = mTicketDto.toJson(),
            type = FcmMessage.FcmMessageType.ASSISTANCE_TICKET,
            id = mTicketDto.id.toString()
        ).sendNotification(fcm)

        assistanceTicket.subscription?.id?.let {
            fcmTokenRepository.findById(it).ifPresent { customerToken ->
                FcmMessage(
                    title = "Ticket ${assistanceTicket.status.status}",
                    message = "Su ticket ${assistanceTicket.id} ha sido actualizado a cerrado",
                    customerToken = customerToken.token,
                    data = mTicketDto.toJson(),
                    type = FcmMessage.FcmMessageType.ASSISTANCE_TICKET,
                    id = mTicketDto.id.toString()
                ).sendNotification(fcm)
            }
        }

        return ResponseEntity.ok(mTicketDto)
    }

    @PutMapping("/closeUnattendedTicket")
    fun closeUnattendedTicket(
        @RequestParam("ticketId") ticketId: Int,
        @RequestParam("userId") userId: Int,
        @RequestParam("status", required = false) status: AssistanceTicketStatus?,
    ): ResponseEntity<AssistanceTicketDto> {
        val assistanceTicket = repository.findById(ticketId).orElseThrow()
        val oldStatus = assistanceTicket.status.name
        val targetStatus = status ?: AssistanceTicketStatus.CLOSED

        assistanceTicket.apply {
            this.status = targetStatus
            closedAt = Date()
        }
        val mTicketDto: AssistanceTicketDto = repository.save(assistanceTicket).toDto()

        ticketNotificationService.notifyTicketClosed(
            ticketId = ticketId.toLong(),
            oldStatus = oldStatus,
            newStatus = assistanceTicket.status.name
        )
        customerNotifyService.notifyStatusChange(
            ticket = assistanceTicket,
            oldStatus = oldStatus,
            newStatus = assistanceTicket.status.name,
            assignedTo = mTicketDto.assignedTo
        )
        if (targetStatus == AssistanceTicketStatus.RESOLVED || targetStatus == AssistanceTicketStatus.CLOSED) {
            runCatching { csatSurveyService.scheduleOnTicketClose(assistanceTicket) }
        }

        FcmMessage(
            title = "Ticket ${assistanceTicket.status.status}",
            message = "El ticket ${assistanceTicket.id} de ${assistanceTicket.subscription?.getFullName()} se ha marcado como ${assistanceTicket.status.status.lowercase()}",
            topic = FcmConstants.ASSISTANCE_TICKET_ADMINS,
            data = mTicketDto.toJson(),
            type = FcmMessage.FcmMessageType.ASSISTANCE_TICKET,
            id = mTicketDto.id.toString()
        ).sendNotification(fcm)

        assistanceTicket.subscription?.id?.let {
            fcmTokenRepository.findById(it).ifPresent { customerToken ->
                FcmMessage(
                    title = "Ticket ${assistanceTicket.status.status}",
                    message = "Su ticket ${assistanceTicket.id} ha sido actualizado a ${assistanceTicket.status.status.lowercase()}",
                    customerToken = customerToken.token,
                    data = mTicketDto.toJson(),
                    type = FcmMessage.FcmMessageType.ASSISTANCE_TICKET,
                    id = mTicketDto.id.toString()
                ).sendNotification(fcm)
            }
        }

        return ResponseEntity.ok(mTicketDto)
    }

    @GetMapping("/findAll")
    fun findBy(@RequestParam("status") status: AssistanceTicketStatus): ResponseEntity<List<AssistanceTicketDto>> {
        val assistanceTickets = repository.findTop40ByStatusOrderByScheduledAt(status)
        return ResponseEntity.ok(assistanceTickets.map { it.toDto() })
    }

    @GetMapping("/find")
    fun getTicket(@RequestParam("ticketId") ticketId: Int): ResponseEntity<AssistanceTicketDto> {
        val assistanceTicket = repository.findById(ticketId).get()
        return ResponseEntity.ok(assistanceTicket.toDto())
    }

    @PostMapping
    fun registerAssistanceTicket(@RequestBody newAssistanceTicket: AssistanceTicketRequest): ResponseEntity<AssistanceTicketDto> {
        val subscription = newAssistanceTicket.subscriptionId?.let { subscriptionRepository.findById(it).get() }

        val ticket = newAssistanceTicket.toModel(subscription)

        val priority = when (newAssistanceTicket.category) {
            "Sin Conexión a Internet" -> 10
            "Cambio de Domicilio" -> 5
            "Otros" -> 3
            "Cambio de Contraseña" -> 2
            else -> 1
        }

        ticket.apply {
            this.priority = priority
            this.createdAt = Date()
            this.scheduledAt = this.createdAt
        }
        if (subscription == null) {
            ticket.isExternalCustomer = true
            ticket.externalCustomerName = newAssistanceTicket.customerName
        }

        val assistanceTicket = repository.save(ticket)
        val ticketDto = assistanceTicket.toDto()

        ticketNotificationService.notifyTicketCreated(ticketDto)

        val priorityLabel = getPriorityLabel(priority)

        FcmMessage(
            title = "Nuevo Ticket | Prioridad $priorityLabel",
            message = "${assistanceTicket.category} - ${assistanceTicket.description}",
            topic = FcmConstants.ASSISTANCE_TICKET,
            type = FcmMessage.FcmMessageType.ASSISTANCE_TICKET,
            data = ticketDto.toJson(),
            id = assistanceTicket.id.toString()
        ).sendNotification(fcm)

        return ResponseEntity.ok(ticketDto)
    }

    @DeleteMapping("/{id}")
    fun deletePendingTicket(@PathVariable id: Int): ResponseEntity<Void> {
        val ticket = repository.findById(id).orElseThrow()
        if (ticket.status.name != "PENDING") {
            return ResponseEntity.status(400).build()
        }
        repository.deleteById(id)
        return ResponseEntity.status(204).build()
    }

    @PutMapping("/{id}")
    fun updateTicket(
        @PathVariable id: Int,
        @RequestBody update: Map<String, String>
    ): ResponseEntity<AssistanceTicketDto> {
        val ticket = repository.findById(id).orElseThrow()
        val oldCategory = ticket.category
        val oldDescription = ticket.description
        update["description"]?.let { ticket.description = it }
        update["category"]?.let { ticket.category = it }
        val updatedTicket = repository.save(ticket)
        val ticketDto = updatedTicket.toDto()
        if (oldCategory != ticket.category || oldDescription != ticket.description) {
            ticketNotificationService.notifyTicketStatusChange(
                ticketId = ticket.id.toLong(),
                oldStatus = ticket.status.name,
                newStatus = ticket.status.name
            )
        }
        return ResponseEntity.ok(ticketDto)
    }

    data class RescheduleTicketRequest(
        val scheduledAt: Long = 0
    )

    @PutMapping("/{id}/reschedule")
    fun rescheduleTicket(
        @PathVariable id: Int,
        @RequestBody request: RescheduleTicketRequest
    ): ResponseEntity<AssistanceTicketDto> {
        val ticket = repository.findById(id).orElseThrow()
        if (request.scheduledAt <= 0) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ticket.toDto())
        }

        if (ticket.status in listOf(
                AssistanceTicketStatus.CLOSED,
                AssistanceTicketStatus.CANCELLED,
                AssistanceTicketStatus.RESOLVED
            )
        ) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ticket.toDto())
        }

        val oldStatus = ticket.status.name
        ticket.applyReschedule(request.scheduledAt)

        val updatedTicket = repository.save(ticket)
        val ticketDto = updatedTicket.toDto()

        ticketNotificationService.notifyTicketStatusChange(
            ticketId = ticket.id.toLong(),
            oldStatus = oldStatus,
            newStatus = ticket.status.name
        )

        return ResponseEntity.ok(ticketDto)
    }
}

private fun Any.toJson(): Any {
    return Gson().toJson(this)
}

fun getPriorityLabel(priority: Int): String {
    val priorityLabel = when {
        priority > 5 -> "Alta"
        priority > 2 -> "Media"
        else -> "Baja"
    }
    return priorityLabel
}

internal fun AssistanceTicket.toDto(): AssistanceTicketDto = AssistanceTicketDto(
    id = id,
    name = subscription?.getFullName() ?: externalCustomerName!!.uppercase(),
    phone = phone,
    ip = subscription?.ip?.takeIf { it.isNotBlank() },
    category = category,
    description = description,
    status = status,
    comments = comments,
    priority = getPriorityLabel(priority),
    createdAt = createdAt,
    scheduledAt = scheduledAt,
    assignedAt = assignedAt,
    resolvedAt = resolvedAt,
    closedAt = closedAt,
    assignedTo = if (responsible != null) "${responsible!!.name} ${responsible!!.lastName}" else "",
    place = subscription?.place?.name ?: placeName,
    address = subscription?.address,
    sheetImageUrl = sheetImageUrl,
    isExternalCustomer = isExternalCustomer
)

private fun AssistanceTicketRequest.toModel(subscription: Subscription?): AssistanceTicket {
    return AssistanceTicket(
        phone = phone,
        category = category,
        description = description,
        subscription = subscription,
        placeName = placeName
    )
}

fun FcmMessage.sendNotification(fcm: FirebaseMessaging) {
    val notificationAsJson = Gson().toJson(this)

    topic?.let {
        val msg: Message = Message.builder()
            .setTopic(topic)
            .putData("title", title)
            .putData("message", message)
            .putData("body", notificationAsJson)
            .build()
        fcm.send(msg)
    }
    customerToken?.let {
        val msg: Message = Message.builder()
            .setToken(customerToken)
            .putData("title", title)
            .putData("message", message)
            .putData("body", notificationAsJson)
            .build()
        fcm.send(msg)
    }
}
