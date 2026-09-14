package com.dscorp.wispadmin.wispadmin.requestbody

import com.dscorp.wispadmin.wispadmin.data.model.Mufa
import com.dscorp.wispadmin.wispadmin.data.model.NapBox
import com.dscorp.wispadmin.wispadmin.data.model.Place


data class NapBoxResquestBody(
    val code: String,
    val address: String,
    val mufaId: Int,
    val latitude: Float,
    val longitude: Float,
    val placeId: Int,
) {
    fun toDto() = NapBox(
        code = code,
        address = address,
        latitude = latitude,
        longitude = longitude,
        mufa = Mufa(id = mufaId),
        place = Place(id = placeId)
    )
}