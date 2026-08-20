package com.dscorp.wispadmin.wispadmin.dto

import com.dscorp.wispadmin.wispadmin.data.model.EquipmentCondition
import com.dscorp.wispadmin.wispadmin.data.model.InstallationType
import com.dscorp.wispadmin.wispadmin.data.model.MikrotikProvisionStatus
import com.dscorp.wispadmin.wispadmin.data.model.OltProvisionStatus
import com.dscorp.wispadmin.wispadmin.data.model.ServiceStatus
import com.dscorp.wispadmin.wispadmin.data.model.Tr069ProvisionStatus
import java.io.Serializable
import java.time.LocalDate

/**
 * A DTO for the {@link com.dscorp.wispadmin.wispadmin.data.model.Subscription} entity
 */
data class SubscriptionDto(
    val id: Int? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val dni: String? = null,
    val password: String? = null,
    val address: String? = null,
    val phone: String? = null,
    val isNew: Boolean? = null,
    val plan: PlanDto? = null,
    val place: PlaceDto? = null,
    val additionalDevices: List<NetworkDeviceDto>? = null,
    val location: GeoLocationDto? = null,
    val technician: UserDto? = null,
    val napBox: NapBoxDto? = null,
    val hostDevice: NetworkDeviceDto? = null,
    val installationType: InstallationType? = null,
    val serviceStatus: ServiceStatus? = null,
    val ip: String? = null,
    val subscriptionDate: Long? = null,
    val isMigration: Boolean? = false,
    val price: Double? = null,
    val paymentCommitmentDate: Long? = null,
    val isPaymentCommitment: Boolean? = null,
    val isServiceCutOff: Boolean = false,
    val lastCutOffDate: LocalDate? = null,
    var isReactivation: Boolean = false,
    var reactivationDate: Long? = null,
    var cpeDeviceId: Int? = null,
    var note: String? = null,
    var email: String? = null,
    var facadePhotoUrl: String? = null,//URL publicada de la foto de fachada almacenada en firebase storage
    val pendingInvoiceQuantity: Int? = 0,
    val antiquityInMonths: Int? = 0,
    val qualification: Int? = 0,
    val ics: Int? = 0,
    val totalDebt: Double? = 0.0,
    val lastPaymentDate: String? = null,
    val borneNumber: String? = null,
    val equipmentCondition: EquipmentCondition? = null,
    val autoCut: Boolean = true,
    val hasFiberOnu: Boolean = false,
    val alreadyRegistered: Boolean = false,
    val mikrotikProvisionStatus: MikrotikProvisionStatus? = null,
    val oltProvisionStatus: OltProvisionStatus? = null,
    val provisioningPending: Boolean = false,
    val tr069ProvisionStatus: Tr069ProvisionStatus? = null,
    val tr069RequiresManualConfig: Boolean = false,
    val tr069Message: String? = null,
    val wifiSsid24: String? = null,
    val wifiSsid5: String? = null,
) : Serializable


data class SubscriptionUserDto(
    val id: Int? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val phone: String? = null,
    val dni: String? = null,
    val serviceStatus: ServiceStatus? = null,
    val address: String? = null,

    )

data class SubscriptionCutDto(
    val id: Int? = null,
    val hostDevice: NetworkDeviceDto? = null,
    val ip: String? = null,
    val name: String
)

