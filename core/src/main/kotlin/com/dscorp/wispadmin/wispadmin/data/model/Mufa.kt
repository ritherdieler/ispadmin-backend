package com.dscorp.wispadmin.wispadmin.data.model

import com.dscorp.wispadmin.wispadmin.dto.MufaDto
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Id
import javax.persistence.OneToMany

@Entity
data class Mufa(
    @Id
    @GeneratedValue
    var id: Int? = null,
    val latitude: Float? = null,
    val longitude: Float? = null,
    val reference: String? = null,
    val threads: Int? = null,
    @OneToMany(mappedBy = "mufa")
    val napBoxList: List<NapBox>? = null,
) {
    fun toDto() = MufaDto(
        id = id,
        latitude = latitude,
        longitude = longitude,
        reference = reference,
        threads = threads,
        napBoxes = napBoxList?.map { it.toDto() }
    )
}