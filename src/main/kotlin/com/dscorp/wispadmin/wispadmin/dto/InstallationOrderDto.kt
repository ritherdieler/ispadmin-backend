package com.dscorp.wispadmin.wispadmin.dto

import com.dscorp.wispadmin.wispadmin.data.model.InstallationOrder
import com.dscorp.wispadmin.wispadmin.data.model.InstallationOrderStatus
import com.dscorp.wispadmin.wispadmin.data.model.Place
import com.dscorp.wispadmin.wispadmin.data.model.User
import com.dscorp.wispadmin.wispadmin.mapper.toDto
import java.time.LocalDateTime

/**
 * DTO para InstallationOrder, usado para notificaciones
 * Contiene solo los campos necesarios, evitando relaciones cíclicas
 */
data class InstallationOrderDto(
    val id: Int,
    val customerFirstName: String?,
    val customerLastName: String?,
    val customerAddress: String?,
    val customerPhone: String?,
    val customerDni: String? = null,
    val status: InstallationOrderStatus,
    val scheduledDate: LocalDateTime?,
    val seller: UserDto? = null,
    val assignedBy: UserDto? = null,
    val technician: UserDto? = null,
    val place: Place?,
    val createdAt: LocalDateTime? = null,
)

/**
 * Extensión para convertir un InstallationOrder a DTO
 */
fun InstallationOrder.toDto(): InstallationOrderDto {
    return InstallationOrderDto(
        id = this.id,
        customerFirstName = this.customerFirstName?.capitalize(),
        customerLastName = this.customerLastName?.capitalize(),
        customerAddress = this.customerAddress?.capitalize(),
        customerPhone = this.customerPhone,
        customerDni = this.customerDni,
        status = this.status,
        scheduledDate = this.scheduledDate,
        technician = technician?.toDto(),
        assignedBy =  this.assignedBy?.toDto(),
        seller = this.seller?.toDto(),
        place = Place(id = this.place?.id ?: 0, name = this.place?.name?.capitalize()),
        createdAt = createdAt
    )
} 