package com.dscorp.wispadmin.wispadmin.service.subscription

import com.dscorp.wispadmin.wispadmin.data.model.IpPool
import com.dscorp.wispadmin.wispadmin.extensions.getBaseIpFromRange
import com.dscorp.wispadmin.wispadmin.repository.IpPoolRepository
import org.springframework.stereotype.Service

@Service
class SubscriptionIpAllocationService(
    private val ipPoolRepository: IpPoolRepository
) {

    fun allocateFreeIp(hostDeviceId: Int): Pair<String, IpPool> {
        val ipPools = ipPoolRepository.findEligiblePoolsByHostDeviceId(hostDeviceId)
        if (ipPools.isEmpty()) {
            throw Exception("No hay pools de IP elegibles para el hostDevice id=$hostDeviceId")
        }

        val ipRange = 10..250
        for (ipPool in ipPools) {
            val usedLastOctets = ipPool.ips.mapNotNull { subscription ->
                subscription.ip?.split(".")?.lastOrNull()?.trim()?.toIntOrNull()
            }.toSet()
            val availableIps = ipRange - usedLastOctets
            if (availableIps.isNotEmpty()) {
                val availableIp = ipPool.ipSegment.getBaseIpFromRange() + availableIps.first()
                return Pair(availableIp, ipPool)
            }
        }

        throw Exception("No hay IPs disponibles para el hostDevice id=$hostDeviceId")
    }
}
