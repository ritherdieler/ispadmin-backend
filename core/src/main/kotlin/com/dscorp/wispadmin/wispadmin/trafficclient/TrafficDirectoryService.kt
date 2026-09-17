package com.dscorp.wispadmin.wispadmin.trafficclient

import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.data.model.usesPppoe
import com.dscorp.wispadmin.wispadmin.data.model.usesSimpleQueue
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import org.springframework.stereotype.Service

@Service
class TrafficDirectoryService(
    private val subscriptionRepository: SubscriptionRepository,
) {
    fun list(): List<TrafficDirectoryEntryDto> {
        return subscriptionRepository.findForTrafficPolling().mapNotNull { it.toEntry() }
    }

    fun find(subscriptionId: Int): TrafficDirectoryEntryDto? {
        val subscription = subscriptionRepository.findById(subscriptionId).orElse(null) ?: return null
        return subscription.toEntry()
    }

    fun page(after: Int, size: Int): TrafficTargetPage {
        if (after < 0 || size !in 1..200) throw org.springframework.web.server.ResponseStatusException(
            org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid target page")
        val rows = subscriptionRepository.findTrafficTargetsAfter(after, org.springframework.data.domain.PageRequest.of(0, size + 1))
        val selected = rows.take(size)
        return TrafficTargetPage(selected.mapNotNull { it.toEntry() },
            if (rows.size > size) selected.last().id else null)
    }

    private fun Subscription.toEntry(): TrafficDirectoryEntryDto? {
        val id = id ?: return null
        val ipRaw = ip?.trim().orEmpty()
        val pppoeRaw = pppoeUsername?.trim()?.takeIf { it.isNotEmpty() }
        val ip = if (accessMode.usesSimpleQueue()) ipRaw else ""
        val pppoe = if (accessMode.usesPppoe() && !accessMode.usesSimpleQueue()) pppoeRaw else null
        if (ip.isEmpty() && pppoe == null) return null
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
            pppoeUsername = pppoe,
        )
    }
}

data class TrafficTargetPage(val items: List<TrafficDirectoryEntryDto>, val nextCursor: Int?)
