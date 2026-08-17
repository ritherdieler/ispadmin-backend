package com.dscorp.wispadmin.wispadmin.requestbody

import com.dscorp.wispadmin.wispadmin.data.model.*
import com.dscorp.wispadmin.wispadmin.dto.OnuDto
import com.dscorp.wispadmin.wispadmin.extensions.removeSpecialCharacters
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

data class SubscriptionRequest(
    var id: Int? = null,
    var firstName: String,
    var lastName: String,
    var dni: String,
    var password: String? = null,
    var address: String,
    var phone: String,
    var subscriptionDate: Long,
    var planId: Int,
    var additionalDeviceIds: List<Int>,
    var placeId: Int,
    var location: GeoLocation,
    var technicianId: Int,
    var napBoxId: Int? = null,
    var hostDeviceId: Int,
    var cpeDeviceId: Int? = null,
    var onu: OnuDto? = null,
    var installationType: InstallationType,
    var couponId: Int? = null,
    var price: Double? = null,
    var note: String? = null,
    var facadePhotoUrl: String? = null,
    var isMigration: Boolean = false,
    var installationOrderId: Int? = null,
    var clientRequestId: String? = null,
    var borneNumber: String? = null,
    var equipmentCondition: EquipmentCondition = EquipmentCondition.LOAN,
    var autoCut: Boolean = true,
    var clientIpAddress: String? = null,
    var vlan: String? = null,
) {
    fun toModel(): Subscription = Subscription(
        firstName = firstName.removeSpecialCharacters(),
        lastName = lastName.removeSpecialCharacters(),
        dni = dni,
        password = dni,
        address = address,
        phone = phone,
        subscriptionDatetime = subscriptionDateAsLocalDateTime(),
        plan = Plan(id = planId),
        additionalDevices = additionalDeviceIds.map { NetworkDevice(id = it) },
        place = Place(id = placeId),
        location = location,
        technician = User(id = technicianId),
        napBox = if (napBoxId == null) null else NapBox(id = napBoxId),
        hostDevice = NetworkDevice(id = hostDeviceId),
        cpe = cpeDeviceId?.let { NetworkDevice(id = it) },
        fiberOnu = onu?.toModel(),
        installationType = installationType,
        coupon = couponId?.let { Coupon(id = it) },
        price = price,
        note = note,
        facadePhotoUrl = facadePhotoUrl,
        isMigration = isMigration,
        borneNumber = borneNumber,
        equipmentCondition = equipmentCondition,
        autoCut = autoCut,
        vlan = vlan
    )

    private fun subscriptionDateAsLocalDateTime(): LocalDateTime {
        return Instant.ofEpochMilli(subscriptionDate)
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime()
    }

    fun getClientName(): String = "$firstName $lastName"
}