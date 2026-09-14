package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.controller.ModuleException
import com.dscorp.wispadmin.wispadmin.data.model.InstallationOrder
import com.dscorp.wispadmin.wispadmin.data.model.InstallationOrderStatus
import com.dscorp.wispadmin.wispadmin.data.model.User
import com.dscorp.wispadmin.wispadmin.dto.toDto
import com.dscorp.wispadmin.wispadmin.repository.InstallationOrderRepository
import com.dscorp.wispadmin.wispadmin.repository.UserRepository
import com.dscorp.wispadmin.wispadmin.util.fcm.FcmMessage.FcmMessageType
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import javax.transaction.Transactional

@Service
class InstallationOrderService @Autowired constructor(
    private val installationOrderRepository: InstallationOrderRepository,
    private val userRepository: UserRepository,
    private val notificationService: NotificationService
) {

    companion object {
        private const val TOPIC_INSTALLATION_ORDER = "installationOrder"
    }


    fun getInstallationOrderById(id: Int): InstallationOrder {
        return installationOrderRepository.findById(id)
            .orElseThrow { ModuleException("Orden de instalación no encontrada con ID: $id", "INSTALLATION_ORDER") }
    }



    @Transactional
    fun createInstallationOrder(installationOrder: InstallationOrder): InstallationOrder {
        installationOrder.status = InstallationOrderStatus.SOLICITADO
        installationOrder.createdAt = LocalDateTime.now()
        installationOrder.updatedAt = LocalDateTime.now()
        val savedOrder = installationOrderRepository.save(installationOrder)
        
        notificationService.sendTopicNotification(
            topic = TOPIC_INSTALLATION_ORDER,
            title = "Nueva orden de instalación",
            message = "Se ha creado la orden de instalación para ${savedOrder.customerFirstName} ${savedOrder.customerLastName}",
            data = savedOrder.toDto(),
            type = FcmMessageType.INSTALLATION_ORDER,
            id = savedOrder.id.toString()
        )
        
        return savedOrder
    }

    @Transactional
    fun assignTechnician(orderId: Int, technicianId: Int, assignedById: Int, scheduledDateTime: LocalDateTime): InstallationOrder {
        val order = getInstallationOrderById(orderId)
        val technician = userRepository.findById(technicianId)
            .orElseThrow { ModuleException("Usuario técnico no encontrado con ID: $technicianId", "INSTALLATION_ORDER") }
        val assignedBy = userRepository.findById(assignedById)
            .orElseThrow { ModuleException("Usuario asignador no encontrado con ID: $assignedById", "INSTALLATION_ORDER") }
        
        order.technician = technician
        order.assignedBy = assignedBy
        order.status = InstallationOrderStatus.EN_CURSO
        order.scheduledDate = scheduledDateTime
        
        val updatedOrder = installationOrderRepository.save(order)

        notificationService.sendUserNotification(
            userId = technicianId,
            title = "Nueva orden asignada",
            message = "Se te ha asignado la orden de instalación #${updatedOrder.id} para ${updatedOrder.customerFirstName} ${updatedOrder.customerLastName}. Programada para el ${scheduledDateTime.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy hh:mm a"))}",
            data = updatedOrder.toDto(),
            type = FcmMessageType.TECHNICIAN_ASSIGNED_INSTALLATION_ORDER,
            id = updatedOrder.id.toString()
        )
        
        order.seller?.let { seller ->
            notificationService.sendUserNotification(
                userId = seller.id,
                title = "Técnico asignado a tu orden",
                message = "Se ha asignado un técnico a la orden para ${updatedOrder.customerFirstName} ${updatedOrder.customerLastName}. Programada para el ${scheduledDateTime.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy hh:mm a"))}",
                data = updatedOrder.toDto(),
                type = FcmMessageType.SALES_ASSIGNED_INSTALLATION_ORDER,
                id = updatedOrder.id.toString()
            )
        }
        return updatedOrder
    }

    @Transactional
    fun scheduleInstallation(orderId: Int, scheduledDate: LocalDateTime): InstallationOrder {
        val order = getInstallationOrderById(orderId)
        
        order.scheduledDate = scheduledDate
        
        val updatedOrder = installationOrderRepository.save(order)
        
        notificationService.sendTopicNotification(
            topic = TOPIC_INSTALLATION_ORDER,
            title = "Instalación programada",
            message = "La instalación de la orden #${updatedOrder.id} ha sido programada para ${scheduledDate}",
            data = updatedOrder.toDto(),
            type = FcmMessageType.INSTALLATION_ORDER,
            id = updatedOrder.id.toString()
        )
        
        return updatedOrder
    }

    @Transactional
    fun closeInstallationOrder(orderId: Int): InstallationOrder {
        val order = getInstallationOrderById(orderId)

        if (order.status == InstallationOrderStatus.CERRADO) {
            return order
        }

        order.status = InstallationOrderStatus.CERRADO

        val updatedOrder = installationOrderRepository.save(order)

        notificationService.sendTopicNotification(
            topic = TOPIC_INSTALLATION_ORDER,
            title = "Instalación completada",
            message = "La instalación de ${updatedOrder.customerFirstName} ${updatedOrder.customerLastName} ha sido realizada con éxito. Orden ${updatedOrder.id} cerrada.",
            data = updatedOrder.toDto(),
            type = FcmMessageType.SALES_CLOSED_INSTALLATION_ORDER,
            id = updatedOrder.id.toString()
        )

        return updatedOrder
    }

    @Transactional
    fun cancelInstallationOrder(orderId: Int, cancellationReason: String?): InstallationOrder {
        val order = getInstallationOrderById(orderId)
        
        if (order.status == InstallationOrderStatus.CERRADO) {
            throw ModuleException("No se puede cancelar una orden que ya ha sido cerrada", "INSTALLATION_ORDER")
        }
        
        order.status = InstallationOrderStatus.CANCELADO
        
        val updatedOrder = installationOrderRepository.save(order)
        
        notificationService.sendTopicNotification(
            topic = TOPIC_INSTALLATION_ORDER,
            title = "Orden de instalación cancelada",
            message = "La orden de instalación #${updatedOrder.id} ha sido cancelada" + 
                      (cancellationReason?.let { ". Motivo: $it" } ?: ""),
            data = updatedOrder.toDto(),
            type = FcmMessageType.INSTALLATION_ORDER,
            id = updatedOrder.id.toString()
        )
        
        return updatedOrder
    }

    @Transactional
    fun deleteInstallationOrder(id: Int) {
        val order = getInstallationOrderById(id)
        installationOrderRepository.delete(order)
    }


    /**
     * Obtiene todas las órdenes de instalación paginadas
     * @param page Número de página (0-based)
     * @param size Tamaño de la página
     * @return Página de órdenes de instalación
     */
    fun getAllInstallationOrdersPaginated(page: Int, size: Int): Page<InstallationOrder> {
        val pageable = PageRequest.of(page, size, Sort.by("createdAt").descending())
        return installationOrderRepository.findAllPaginated(pageable)
    }

    /**
     * Obtiene las órdenes de instalación paginadas para un vendedor específico
     * @param sellerId ID del vendedor
     * @param page Número de página (0-based)
     * @param size Tamaño de la página
     * @return Página de órdenes de instalación
     */
    fun getInstallationOrdersBySeller(sellerId: Int, page: Int, size: Int): Page<InstallationOrder> {
        val pageable = PageRequest.of(page, size, Sort.by("createdAt").descending())
        return installationOrderRepository.findBySellerIdPaginated(sellerId, pageable)
    }

    /**
     * Obtiene las órdenes de instalación paginadas para un técnico específico
     * @param technicianId ID del técnico
     * @param page Número de página (0-based)
     * @param size Tamaño de la página
     * @return Página de órdenes de instalación
     */
    fun getInstallationOrdersByTechnician(technicianId: Int, page: Int, size: Int): Page<InstallationOrder> {
        val pageable = PageRequest.of(page, size, Sort.by("createdAt").descending())
        return installationOrderRepository.findByTechnicianIdPaginated(technicianId, pageable)
    }

    @Transactional
    fun transferInstallationOrder(
        orderId: Int,
        newTechnicianId: Int,
        transferredById: Int,
        scheduledDateTime: LocalDateTime
    ): InstallationOrder {
        val order = getInstallationOrderById(orderId)
        val newTechnician = userRepository.findById(newTechnicianId)
            .orElseThrow { ModuleException("Técnico no encontrado con ID: $newTechnicianId", "INSTALLATION_ORDER") }
        val transferredBy = userRepository.findById(transferredById)
            .orElseThrow { ModuleException("Usuario no encontrado con ID: $transferredById", "INSTALLATION_ORDER") }

        if (newTechnician.type != User.UserType.TECHNICIAN) {
            throw ModuleException("El usuario seleccionado no es un técnico", "INSTALLATION_ORDER")
        }

        order.technician = newTechnician
        order.assignedBy = transferredBy
        order.scheduledDate = scheduledDateTime
        order.updatedAt = LocalDateTime.now()

        val updatedOrder = installationOrderRepository.save(order)

        notificationService.sendTopicNotification(
            topic = TOPIC_INSTALLATION_ORDER,
            title = "Orden de instalación transferida",
            message = "La orden de instalación #${updatedOrder.id} ha sido transferida al técnico ${newTechnician.name} ${newTechnician.lastName}",
            data = updatedOrder.toDto(),
            type = FcmMessageType.INSTALLATION_ORDER,
            id = updatedOrder.id.toString()
        )

        return updatedOrder
    }
} 
