package com.dscorp.wispadmin.wispadmin.dto

import com.dscorp.wispadmin.wispadmin.data.model.ServiceStatus
import javax.validation.constraints.NotEmpty
import javax.validation.constraints.NotNull

data class SmartMapClientSearchDto(
    val id: Int,
    val customerName: String,
    val abonadoCode: String,
    val status: ServiceStatus,
    val address: String?,
    val latitude: Double?,
    val longitude: Double?,
    val debtAmount: Double,
    val placeName: String?,
    val hasValidLocation: Boolean,
)

data class TicketRouteLocationDto(
    val ticketId: Int,
    val ticketNumber: String,
    val subject: String,
    val customerId: Int,
    val customerName: String,
    val latitude: Double,
    val longitude: Double,
    val placeName: String?,
    val priority: Int,
)

data class SmartMapCustomRouteRequestDto(
    @field:NotNull
    val collectorLatitude: Double,
    @field:NotNull
    val collectorLongitude: Double,
    val collectorAccuracyMeters: Double? = null,
    @field:NotEmpty
    val clientIds: List<Int>,
    val includeRoadGeometry: Boolean = true,
    val travelMode: String = "vehicle",
    /** Optional preserved stop order when the user manually reordered the cart. */
    val preserveManualOrder: Boolean = false,
)
