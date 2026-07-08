package com.dscorp.wispadmin.wispadmin.dto

import com.dscorp.wispadmin.wispadmin.data.model.ServiceStatus
import java.time.LocalDate

data class SmartMapSummaryDto(
    val clients: List<SmartMapClientDto>,
    val zones: List<SmartMapZoneDto>,
    val kpis: SmartMapKpisDto,
    val coverageZones: List<CoverageZoneDto> = emptyList(),
    val salesLeads: List<SalesLeadMapDto> = emptyList(),
    val commercialOpportunities: List<CommercialOpportunityDto> = emptyList(),
    val alerts: List<SmartMapAlertDto> = emptyList(),
    val rankings: SmartMapRankingsDto? = null,
)

data class SmartMapClientDto(
    val id: Int,
    val firstName: String,
    val lastName: String,
    val plan: String?,
    val location: GeoLocationDto,
    val serviceStatus: ServiceStatus,
    val address: String?,
    val phone: String?,
    val dni: String?,
    val ip: String?,
    val subscriptionDate: Long?,
    val lastCutOffDate: LocalDate?,
    val pendingInvoiceQuantity: Int,
    val totalDebt: Double,
    val place: String?,
    val installationType: String?
)

data class SmartMapZoneDto(
    val zoneName: String,
    val displayName: String = zoneName,
    val clientCount: Int,
    val activeClients: Int,
    val totalDebt: Double,
    val openTickets: Int,
    val riskLevel: String,
    val debtLevel: String,
    val ticketLevel: String,
    val latitude: Double?,
    val longitude: Double?,
    val clientDensity: Int = 0,
    val revenue: Double = 0.0,
    val incidenceRate: Double = 0.0,
    val growthRate: Double = 0.0,
    val opportunityScore: Double = 0.0,
    val cancellationRisk: Double = 0.0,
)

data class SmartMapKpisDto(
    val totalClients: Int,
    val activeClients: Int,
    val geolocatedClients: Int,
    val totalDebt: Double,
    val totalZones: Int,
    val openTickets: Int
)
