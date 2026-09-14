package com.dscorp.wispadmin.wispadmin.data.model

import com.dscorp.wispadmin.wispadmin.dto.IpPoolDto
import com.dscorp.wispadmin.wispadmin.dto.NetworkDeviceDto
import com.dscorp.wispadmin.wispadmin.mapper.toDto
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Id
import javax.persistence.OneToMany
import javax.persistence.OneToOne

@Entity
data class IpPool(
    @Id
    @GeneratedValue
    var id: Int,
    @Column(unique = true)
    var ipSegment: String = "",
    var createdAt: Long = System.currentTimeMillis(),
    @OneToMany(mappedBy = "ipPool")
    val ips : List<Subscription> = emptyList(),
    var isEligible: Boolean = true,
    @OneToOne
    val hostDevice: NetworkDevice? = null
) {
    fun toDto(): IpPoolDto {
        return IpPoolDto(
            id = id,
            ipSegment = ipSegment,
            createdAt = createdAt,
            hostDevice = hostDevice?.toDto() ?: NetworkDeviceDto(),
            isEligible = isEligible
    )
    }
}