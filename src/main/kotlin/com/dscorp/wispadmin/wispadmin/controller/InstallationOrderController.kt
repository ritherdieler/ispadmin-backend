package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.data.model.InstallationOrder
import com.dscorp.wispadmin.wispadmin.data.model.InstallationOrderStatus
import com.dscorp.wispadmin.wispadmin.data.model.Modules
import com.dscorp.wispadmin.wispadmin.dto.InstallationOrderDto
import com.dscorp.wispadmin.wispadmin.dto.toDto
import com.dscorp.wispadmin.wispadmin.repository.ErrorLogRepository
import com.dscorp.wispadmin.wispadmin.service.InstallationOrderService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.Page
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime

@RestController
@RequestMapping("/installation-order")
class InstallationOrderController @Autowired constructor(
    private val installationOrderService: InstallationOrderService,
    private val errorLogRepository: ErrorLogRepository
) {


    @GetMapping("/{id}")
    fun getInstallationOrderById(@PathVariable id: Int): ResponseEntity<InstallationOrderDto> {
        return try {
            val order = installationOrderService.getInstallationOrderById(id)
            ResponseEntity.ok(order.toDto())
        } catch (e: ModuleException) {
            e.printStackTrace()
            errorLogRepository.save(e.toErrorLog(Modules.INSTALLATION_ORDER))
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(null)
        } catch (e: Exception) {
            e.printStackTrace()
            errorLogRepository.save(e.toErrorLog(Modules.INSTALLATION_ORDER))
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null)
        }
    }

    @PostMapping
    fun createInstallationOrder(@RequestBody installationOrder: InstallationOrder): ResponseEntity<InstallationOrderDto> {
        return try {
            val savedOrder = installationOrderService.createInstallationOrder(installationOrder)
            ResponseEntity.status(HttpStatus.CREATED).body(savedOrder.toDto())
        } catch (e: Exception) {
            e.printStackTrace()
            errorLogRepository.save(e.toErrorLog(Modules.INSTALLATION_ORDER))
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null)
        }
    }

    @PutMapping("/{id}/assign")
    fun assignTechnicianToOrder(
        @PathVariable id: Int,
        @RequestParam technicianId: Int,
        @RequestParam assignedById: Int,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) scheduledDateTime: LocalDateTime
    ): ResponseEntity<InstallationOrderDto> {
        return try {
            val updatedOrder =
                installationOrderService.assignTechnician(id, technicianId, assignedById, scheduledDateTime)
            ResponseEntity.ok(updatedOrder.toDto())
        } catch (e: ModuleException) {
            e.printStackTrace()
            errorLogRepository.save(e.toErrorLog(Modules.INSTALLATION_ORDER))
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(null)
        } catch (e: Exception) {
            e.printStackTrace()
            errorLogRepository.save(e.toErrorLog(Modules.INSTALLATION_ORDER))
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null)
        }
    }

    @PutMapping("/{id}/schedule")
    fun scheduleInstallation(
        @PathVariable id: Int,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) scheduledDate: LocalDateTime
    ): ResponseEntity<InstallationOrderDto> {
        return try {
            val updatedOrder = installationOrderService.scheduleInstallation(id, scheduledDate)
            ResponseEntity.ok(updatedOrder.toDto())
        } catch (e: ModuleException) {
            e.printStackTrace()
            errorLogRepository.save(e.toErrorLog(Modules.INSTALLATION_ORDER))
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(null)
        } catch (e: Exception) {
            e.printStackTrace()
            errorLogRepository.save(e.toErrorLog(Modules.INSTALLATION_ORDER))
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null)
        }
    }

    @PutMapping("/{id}/close")
    fun closeInstallationOrder(@PathVariable id: Int): ResponseEntity<InstallationOrderDto> {
        return try {
            val updatedOrder = installationOrderService.closeInstallationOrder(id)
            ResponseEntity.ok(updatedOrder.toDto())
        } catch (e: ModuleException) {
            e.printStackTrace()
            errorLogRepository.save(e.toErrorLog(Modules.INSTALLATION_ORDER))
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(null)
        } catch (e: Exception) {
            e.printStackTrace()
            errorLogRepository.save(e.toErrorLog(Modules.INSTALLATION_ORDER))
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null)
        }
    }

    @DeleteMapping("/{id}")
    fun deleteInstallationOrder(@PathVariable id: Int): ResponseEntity<Void> {
        return try {
            installationOrderService.deleteInstallationOrder(id)
            ResponseEntity.ok().build()
        } catch (e: ModuleException) {
            e.printStackTrace()
            errorLogRepository.save(e.toErrorLog(Modules.INSTALLATION_ORDER))
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(null)
        } catch (e: Exception) {
            e.printStackTrace()
            errorLogRepository.save(e.toErrorLog(Modules.INSTALLATION_ORDER))
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null)
        }
    }

    @PutMapping("/{id}/cancel")
    fun cancelInstallationOrder(
        @PathVariable id: Int,
        @RequestParam(required = false) cancellationReason: String?
    ): ResponseEntity<InstallationOrderDto> {
        return try {
            val updatedOrder = installationOrderService.cancelInstallationOrder(id, cancellationReason)
            ResponseEntity.ok(updatedOrder.toDto())
        } catch (e: ModuleException) {
            e.printStackTrace()
            errorLogRepository.save(e.toErrorLog(Modules.INSTALLATION_ORDER))
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(null)
        } catch (e: Exception) {
            e.printStackTrace()
            errorLogRepository.save(e.toErrorLog(Modules.INSTALLATION_ORDER))
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null)
        }
    }

    @GetMapping("/all-paginated")
    fun getAllInstallationOrdersPaginated(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int
    ): ResponseEntity<Any> {
        return try {
            val orders = installationOrderService.getAllInstallationOrdersPaginated(page, size)
            ResponseEntity.ok(orders.map { it.toDto() })
        } catch (e: Exception) {
            e.printStackTrace()
            errorLogRepository.save(e.toErrorLog(Modules.INSTALLATION_ORDER))
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf("error" to (e.message ?: "Unknown error")))
        }
    }

    /**
     * Obtiene las órdenes de instalación paginadas para un vendedor específico
     * @param sellerId ID del vendedor
     * @param page Número de página (0-based)
     * @param size Tamaño de la página
     * @return Página de órdenes de instalación como DTOs
     */
    @GetMapping("/seller/{sellerId}")
    fun getInstallationOrdersBySeller(
        @PathVariable sellerId: Int,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int
    ): ResponseEntity<Any> {
        return try {
            val orders = installationOrderService.getInstallationOrdersBySeller(sellerId, page, size)
            ResponseEntity.ok(orders.map { it.toDto() })
        } catch (e: Exception) {
            e.printStackTrace()
            errorLogRepository.save(e.toErrorLog(Modules.INSTALLATION_ORDER))
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf("error" to (e.message ?: "Unknown error")))

        }
    }

    /**
     * Obtiene las órdenes de instalación paginadas para un técnico específico
     * @param technicianId ID del técnico
     * @param page Número de página (0-based)
     * @param size Tamaño de la página
     * @return Página de órdenes de instalación como DTOs
     */
    @GetMapping("/technician/{technicianId}")
    fun getInstallationOrdersByTechnician(
        @PathVariable technicianId: Int,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int
    ): ResponseEntity<Any> {
        return try {
            val orders = installationOrderService.getInstallationOrdersByTechnician(technicianId, page, size)
            ResponseEntity.ok(orders.map { it.toDto() })
        } catch (e: Exception) {
            e.printStackTrace()
            errorLogRepository.save(e.toErrorLog(Modules.INSTALLATION_ORDER))
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf("error" to (e.message ?: "Unknown error")))
        }
    }

    @PutMapping("/{id}/transfer")
    fun transferInstallationOrder(
        @PathVariable id: Int,
        @RequestParam newTechnicianId: Int,
        @RequestParam transferredById: Int,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) scheduledDateTime: LocalDateTime
    ): ResponseEntity<InstallationOrderDto> {
        return try {
            val updatedOrder = installationOrderService.transferInstallationOrder(
                orderId = id,
                newTechnicianId = newTechnicianId,
                transferredById = transferredById,
                scheduledDateTime = scheduledDateTime
            )
            ResponseEntity.ok(updatedOrder.toDto())
        } catch (e: ModuleException) {
            e.printStackTrace()
            errorLogRepository.save(e.toErrorLog(Modules.INSTALLATION_ORDER))
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(null)
        } catch (e: Exception) {
            e.printStackTrace()
            errorLogRepository.save(e.toErrorLog(Modules.INSTALLATION_ORDER))
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null)
        }
    }
}
