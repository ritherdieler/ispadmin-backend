package com.dscorp.wispadmin.wispadmin.dto

import com.dscorp.wispadmin.wispadmin.data.model.ServiceStatus
import java.io.Serializable
import java.time.LocalDate
import java.util.Date

/**
 * DTO que representa la información de ubicación de un cliente para el mapa de calor
 */
data class ClienteUbicacionDto(
    val id: Int,
    val firstName: String,
    val lastName: String,
    val plan: String?,
    val location: GeoLocationDto,
    val serviceStatus: ServiceStatus,
    // Datos adicionales relevantes de la suscripción
    val address: String?,
    val phone: String?,
    val dni: String?,
    val ip: String?,
    val subscriptionDate: Long?, // timestamp
    val lastCutOffDate: LocalDate?, // timestamp
    val pendingInvoiceQuantity: Int,
    val totalDebt: Double,
    val place: String?,
    val installationType: String?
) : Serializable 