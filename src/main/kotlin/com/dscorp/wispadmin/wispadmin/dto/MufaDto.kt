package com.dscorp.wispadmin.wispadmin.dto

import java.io.Serializable

data class MufaDto(
    var id: Int? = null,
    var latitude: Float? = null,
    var longitude: Float? = null,
    var reference: String? = null,
    var threads: Int? = null,
    var napBoxes:List<NapBoxDto>? = emptyList()
) : Serializable
