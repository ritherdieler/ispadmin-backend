package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.data.model.InstallationOrder
import com.dscorp.wispadmin.wispadmin.dto.InstallationOrderDto
import com.dscorp.wispadmin.wispadmin.dto.toDto
import com.dscorp.wispadmin.wispadmin.service.InstallationOrderService
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime

@RestController
@RequestMapping("/installation-order")
class InstallationOrderController(
    private val installationOrderService: InstallationOrderService
) {

    @GetMapping("/{id}")
    fun getInstallationOrderById(@PathVariable id: Int): ResponseEntity<InstallationOrderDto> {
        return try {
            ResponseEntity.ok(installationOrderService.getInstallationOrderById(id).toDto())
        } catch (e: ModuleException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(null)
        }
    }

    @PostMapping
    fun createInstallationOrder(@RequestBody installationOrder: InstallationOrder): ResponseEntity<InstallationOrderDto> {
        val savedOrder = installationOrderService.createInstallationOrder(installationOrder)
        return ResponseEntity.status(HttpStatus.CREATED).body(savedOrder.toDto())
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
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(null)
        }
    }

    @PutMapping("/{id}/schedule")
    fun scheduleInstallation(
        @PathVariable id: Int,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) scheduledDate: LocalDateTime
    ): ResponseEntity<InstallationOrderDto> {
        return try {
            ResponseEntity.ok(installationOrderService.scheduleInstallation(id, scheduledDate).toDto())
        } catch (e: ModuleException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(null)
        }
    }

    @PutMapping("/{id}/close")
    fun closeInstallationOrder(@PathVariable id: Int): ResponseEntity<InstallationOrderDto> {
        return try {
            ResponseEntity.ok(installationOrderService.closeInstallationOrder(id).toDto())
        } catch (e: ModuleException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(null)
        }
    }

    @DeleteMapping("/{id}")
    fun deleteInstallationOrder(@PathVariable id: Int): ResponseEntity<Void> {
        return try {
            installationOrderService.deleteInstallationOrder(id)
            ResponseEntity.ok().build()
        } catch (e: ModuleException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(null)
        }
    }

    @PutMapping("/{id}/cancel")
    fun cancelInstallationOrder(
        @PathVariable id: Int,
        @RequestParam(required = false) cancellationReason: String?
    ): ResponseEntity<InstallationOrderDto> {
        return try {
            ResponseEntity.ok(installationOrderService.cancelInstallationOrder(id, cancellationReason).toDto())
        } catch (e: ModuleException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(null)
        }
    }

    @GetMapping("/all-paginated")
    fun getAllInstallationOrdersPaginated(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int
    ): ResponseEntity<Any> {
        val orders = installationOrderService.getAllInstallationOrdersPaginated(page, size)
        return ResponseEntity.ok(orders.map { it.toDto() })
    }

    @GetMapping("/seller/{sellerId}")
    fun getInstallationOrdersBySeller(
        @PathVariable sellerId: Int,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int
    ): ResponseEntity<Any> {
        val orders = installationOrderService.getInstallationOrdersBySeller(sellerId, page, size)
        return ResponseEntity.ok(orders.map { it.toDto() })
    }

    @GetMapping("/technician/{technicianId}")
    fun getInstallationOrdersByTechnician(
        @PathVariable technicianId: Int,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int
    ): ResponseEntity<Any> {
        val orders = installationOrderService.getInstallationOrdersByTechnician(technicianId, page, size)
        return ResponseEntity.ok(orders.map { it.toDto() })
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
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(null)
        }
    }
}
