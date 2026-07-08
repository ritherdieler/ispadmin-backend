package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.data.model.AssistanceTicket
import com.dscorp.wispadmin.wispadmin.data.model.AssistanceTicketStatus
import com.dscorp.wispadmin.wispadmin.data.model.Modules
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.dto.AssistanceTicketDto
import com.dscorp.wispadmin.wispadmin.repository.*
import com.dscorp.wispadmin.wispadmin.requestbody.AssistanceTicketRequest
import com.dscorp.wispadmin.wispadmin.service.FirebaseStorageService
import com.dscorp.wispadmin.wispadmin.service.TicketNotificationService
import com.dscorp.wispadmin.wispadmin.util.fcm.FcmConstants
import com.dscorp.wispadmin.wispadmin.util.fcm.FcmMessage
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.Message
import com.google.gson.Gson
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.util.*
import org.springframework.http.HttpStatus
@RestController
@RequestMapping("/assistanceTicket")
class AssistanceTicketController @Autowired constructor(
    private val fcm: FirebaseMessaging,
    private val repository: AssistanceTicketRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val userRepository: UserRepository,
    private val fcmTokenRepository: FcmTokenRepository,
    private val errorLogRepository: ErrorLogRepository,
    private val storageService: FirebaseStorageService,
    private val ticketNotificationService: TicketNotificationService
) {

    val objectErrorResponse: ResponseEntity<AssistanceTicketDto> = ResponseEntity.status(500).body(null)
    val listObjectErrorResponse: ResponseEntity<List<AssistanceTicketDto>> = ResponseEntity.status(500).body(null)


    @GetMapping("/byDateRange")
    fun getTicketsByDateRange(
        @RequestParam("status") status: AssistanceTicketStatus,
        @RequestParam("startDate") startDate: Long,
        @RequestParam("endDate") endDate: Long
    ): ResponseEntity<List<AssistanceTicketDto>> {
        return try {

            val mStartDate = Date(startDate)
            val mEndDate = Date(endDate)

            val assistanceTickets = repository.findAllByStatusAndScheduledAtBetween(
                status = status,
                start = mStartDate,
                end = mEndDate
            )
            ResponseEntity.status(200).body(assistanceTickets.map { it.toDto() })
        } catch (e: Exception) {
            errorLogRepository.save(e.toErrorLog(Modules.ASSISTANCE_TICKET))
            e.printStackTrace()
            listObjectErrorResponse
        }
    }

    @PutMapping("/assignTicketToUser")
    fun updateTicketStatusToAssigned(
        @RequestParam("ticketId") ticketId: Int,
        @RequestParam("userId") userId: Int,
    ): ResponseEntity<AssistanceTicketDto> {
        try {
            val responsibleUser = userRepository.findById(userId).orElseThrow()
            val assistanceTicket = repository.findById(ticketId).orElseThrow()
            val oldStatus = assistanceTicket.status.name

            assistanceTicket.apply {
                status = AssistanceTicketStatus.ASSIGNED
                responsible = responsibleUser
                assignedAt = Date()
            }
            val mTicketDto: AssistanceTicketDto = repository.save(assistanceTicket).toDto()
            
            // Notificación WebSocket
            ticketNotificationService.notifyTicketAssigned(
                ticketId = ticketId.toLong(),
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
            // Notificación para el cliente
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

            return ResponseEntity.status(200).body(mTicketDto)
        } catch (e: Exception) {
            e.printStackTrace()
            errorLogRepository.save(e.toErrorLog(Modules.ASSISTANCE_TICKET))
            return objectErrorResponse
        }
    }


    @PutMapping("/closeAttendedTicket")
    fun closeAttendedTicket(
        @RequestParam("ticketId") ticketId: Int,
        @RequestParam("userId") userId: Int,
        @RequestParam("image") image: MultipartFile
    ): ResponseEntity<AssistanceTicketDto> {
        try {
            val assistanceTicket = repository.findById(ticketId).orElseThrow()
            val oldStatus = assistanceTicket.status.name
            
            val firebaseUrl = storageService.uploadFileToFolder(image, "tickets")
            assistanceTicket.apply {
                status = AssistanceTicketStatus.CLOSED
                closedAt = Date()
                sheetImageUrl = firebaseUrl
            }
            val mTicketDto: AssistanceTicketDto = repository.save(assistanceTicket).toDto()
            
            // Notificación WebSocket
            ticketNotificationService.notifyTicketClosed(
                ticketId = ticketId.toLong(),
                oldStatus = oldStatus,
                newStatus = assistanceTicket.status.name
            )
            
            FcmMessage(
                title = "Ticket ${assistanceTicket.status.status}",
                message = "El ticket ${assistanceTicket.id} de ${assistanceTicket.subscription?.getFullName()} se ha marcado como cerrado",
                topic = FcmConstants.ASSISTANCE_TICKET_ADMINS,
                data = mTicketDto.toJson(),
                type = FcmMessage.FcmMessageType.ASSISTANCE_TICKET,
                id = mTicketDto.id.toString()
            ).sendNotification(fcm)

            // Notificación para el cliente
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

            return ResponseEntity.status(200).body(mTicketDto)
        } catch (e: Exception) {
            e.printStackTrace()
            errorLogRepository.save(e.toErrorLog(Modules.ASSISTANCE_TICKET))
            return objectErrorResponse
        }

    }

    @PutMapping("/closeUnattendedTicket")
    fun closeUnattendedTicket(
        @RequestParam("ticketId") ticketId: Int,
        @RequestParam("userId") userId: Int,
    ): ResponseEntity<AssistanceTicketDto> {
        try {
            val assistanceTicket = repository.findById(ticketId).orElseThrow()
            val oldStatus = assistanceTicket.status.name
            
            assistanceTicket.apply {
                status = AssistanceTicketStatus.CLOSED
                closedAt = Date()
            }
//                        .also { ticket ->
//                        file?.let {
//                            val imageUrl = storageService.uploadFile(it)
////                            ticket.sheetImageUrl = imageUrl
//                        }
//                    }
            val mTicketDto: AssistanceTicketDto = repository.save(assistanceTicket).toDto()
            
            // Notificación WebSocket
            ticketNotificationService.notifyTicketClosed(
                ticketId = ticketId.toLong(),
                oldStatus = oldStatus,
                newStatus = assistanceTicket.status.name
            )
            
            FcmMessage(
                title = "Ticket ${assistanceTicket.status.status}",
                message = "El ticket ${assistanceTicket.id} de ${assistanceTicket.subscription?.getFullName()} se ha marcado como cerrado",
                topic = FcmConstants.ASSISTANCE_TICKET_ADMINS,
                data = mTicketDto.toJson(),
                type = FcmMessage.FcmMessageType.ASSISTANCE_TICKET,
                id = mTicketDto.id.toString()
            ).sendNotification(fcm)

            // Notificación para el cliente
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

            return ResponseEntity.status(200).body(mTicketDto)
        } catch (e: Exception) {
            e.printStackTrace()
            errorLogRepository.save(e.toErrorLog(Modules.ASSISTANCE_TICKET))
            return objectErrorResponse
        }

    }


    @GetMapping("/findAll")
    fun findBy(@RequestParam("status") status: AssistanceTicketStatus): ResponseEntity<List<AssistanceTicketDto>> {
        return try {
            val assistanceTickets = repository.findTop40ByStatusOrderByScheduledAt(status)
            ResponseEntity.status(200).body(assistanceTickets.map { it.toDto() })
        } catch (e: Exception) {
            errorLogRepository.save(e.toErrorLog(Modules.ASSISTANCE_TICKET))

            e.printStackTrace()
            listObjectErrorResponse
        }
    }


    @GetMapping("/find")
    fun getTicket(@RequestParam("ticketId") ticketId: Int): ResponseEntity<AssistanceTicketDto> {
        return try {
            val assistanceTicket = repository.findById(ticketId).get()
            ResponseEntity.status(200).body(assistanceTicket.toDto())
        } catch (e: Exception) {
            errorLogRepository.save(e.toErrorLog(Modules.ASSISTANCE_TICKET))

            e.printStackTrace()
            objectErrorResponse
        }
    }

    @PostMapping
    fun registerAssistanceTicket(@RequestBody newAssistanceTicket: AssistanceTicketRequest): ResponseEntity<AssistanceTicketDto> {
        return try {

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
            //si el ticket es de un cliente externo
            if (subscription == null) {
                ticket.isExternalCustomer = true
                ticket.externalCustomerName = newAssistanceTicket.customerName
            }

            val assistanceTicket = repository.save(ticket)
            val ticketDto = assistanceTicket.toDto()

            // Notificación WebSocket
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

            ResponseEntity.status(200).body(ticketDto)

        } catch (e: Exception) {
            errorLogRepository.save(e.toErrorLog(Modules.ASSISTANCE_TICKET))
            e.printStackTrace()
            objectErrorResponse


        }
    }

    @DeleteMapping("/{id}")
    fun deletePendingTicket(@PathVariable id: Int): ResponseEntity<Void> {
        return try {
            val ticket = repository.findById(id).orElseThrow()
            if (ticket.status.name != "PENDING") {
                return ResponseEntity.status(400).build()
            }
            repository.deleteById(id)
            ResponseEntity.status(204).build()
        } catch (e: Exception) {
            errorLogRepository.save(e.toErrorLog(Modules.ASSISTANCE_TICKET))
            ResponseEntity.status(500).build()
        }
    }

    @PutMapping("/{id}")
    fun updateTicket(
        @PathVariable id: Int,
        @RequestBody update: Map<String, String>
    ): ResponseEntity<AssistanceTicketDto> {
        return try {
            val ticket = repository.findById(id).orElseThrow()
            val oldCategory = ticket.category
            val oldDescription = ticket.description
            update["description"]?.let { ticket.description = it }
            update["category"]?.let { ticket.category = it }
            val updatedTicket = repository.save(ticket)
            val ticketDto = updatedTicket.toDto()
            // Notificar por WebSocket si cambia la descripción o categoría
            if (oldCategory != ticket.category || oldDescription != ticket.description) {
                ticketNotificationService.notifyTicketStatusChange(
                    ticketId = ticket.id.toLong(),
                    oldStatus = ticket.status.name,
                    newStatus = ticket.status.name
                )
            }
            ResponseEntity.ok(ticketDto)
        } catch (e: Exception) {
            errorLogRepository.save(e.toErrorLog(Modules.ASSISTANCE_TICKET))
            e.printStackTrace()
            objectErrorResponse
        }
    }
    data class RescheduleTicketRequest(
        val scheduledAt: Long = 0
    )
    @PutMapping("/{id}/reschedule")
    fun rescheduleTicket(
        @PathVariable id: Int,
        @RequestBody request: RescheduleTicketRequest
    ): ResponseEntity<AssistanceTicketDto> {
        return try {
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

            ticket.scheduledAt = Date(request.scheduledAt)

            val updatedTicket = repository.save(ticket)
            val ticketDto = updatedTicket.toDto()

            ticketNotificationService.notifyTicketStatusChange(
                ticketId = ticket.id.toLong(),
                oldStatus = ticket.status.name,
                newStatus = ticket.status.name
            )

            ResponseEntity.ok(ticketDto)
        } catch (e: Exception) {
            errorLogRepository.save(e.toErrorLog(Modules.ASSISTANCE_TICKET))
            e.printStackTrace()
            objectErrorResponse
        }
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

private fun AssistanceTicket.toDto(): AssistanceTicketDto = AssistanceTicketDto(
    id = id,
    name = subscription?.getFullName() ?: externalCustomerName!!.uppercase(),
    phone = phone,
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
