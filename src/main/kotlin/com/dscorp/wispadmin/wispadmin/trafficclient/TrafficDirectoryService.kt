package com.dscorp.wispadmin.wispadmin.trafficclient

import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import org.springframework.stereotype.Service

@Service
class TrafficDirectoryService(
    private val subscriptionRepository: SubscriptionRepository,
) {
    fun list(): List<TrafficDirectoryEntryDto> {
        return subscriptionRepository.findForTrafficPolling().mapNotNull { it.toEntry() }
    }

    private fun Subscription.toEntry(): TrafficDirectoryEntryDto? {
        val id = id ?: return null
        val ip = ip?.trim().orEmpty()
        if (ip.isEmpty()) return null
        val person = listOf(firstName, lastName)
            .mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
            .joinToString(" ")
        val business = businessName?.trim().orEmpty()
        val display = when {
            person.isNotBlank() -> person
            business.isNotBlank() -> business
            else -> "Suscripción $id"
        }
        return TrafficDirectoryEntryDto(
            subscriptionId = id,
            ip = ip,
            routerHint = hostDevice?.id,
            planId = plan?.id,
            planName = plan?.name,
            planDownloadMbps = plan?.downloadSpeed,
            planUploadMbps = plan?.uploadSpeed,
            displayName = display,
        )
    }
}
