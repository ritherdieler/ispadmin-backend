package com.dscorp.wispadmin.wispadmin.data.model

import com.dscorp.wispadmin.wispadmin.dto.NapBoxDto
import javax.persistence.*

@Entity
data class NapBox(
    @Id
    @GeneratedValue
    var id: Int? = null,
    val code: String = "",
    val address: String = "",
    val latitude: Float? = null,
    val longitude: Float? = null,
    val ports_number: Int? = null,
    @ManyToOne
    val mufa: Mufa? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    val place: Place? = null,

    val oltId: Int? = null,
    val oltPort: Int? = null,
    val oltBoard: Int? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    val hostDevice: NetworkDevice? = null
) {

    fun toNapBoxWitPlaceDto() = NapBoxDto(
        id = id,
        code = code,
        address = address,
        latitude = latitude,
        longitude = longitude,
        mufaId = mufa?.id,
        ports_number = ports_number,
        oltId = oltId,
        oltPort = oltPort,
        oltBoard = oltBoard,
        hostDeviceId = hostDevice?.id,
        placeName = place!!.name!!,
        placeId = place!!.id
    )

    fun toDto() = NapBoxDto(
        id = id,
        code = code,
        address = address,
        latitude = latitude,
        longitude = longitude,
        mufaId = mufa?.id,
        ports_number = ports_number,
        oltId = oltId,
        oltPort = oltPort,
        oltBoard = oltBoard,
        hostDeviceId = hostDevice?.id,
    )
}