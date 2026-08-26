package com.dscorp.wispadmin.wispadmin.service.genieacs

import com.dscorp.wispadmin.wispadmin.data.model.InstallationType
import java.text.Normalizer

enum class GenieAcsServiceKind {
    INTERNET,
    DUO,
    TV,
}

object GenieAcsSubscriptionTags {
    const val WAN_NAME_MAX_LENGTH = 64

    private val comboTokens = listOf(
        "combo",
        "duo",
        "internet + tv",
        "internet tv",
        "cable + internet",
    )

    fun serviceKind(
        installationType: InstallationType?,
        planType: InstallationType?,
        planName: String?,
    ): GenieAcsServiceKind {
        if (installationType == InstallationType.ONLY_TV_FIBER ||
            planType == InstallationType.ONLY_TV_FIBER
        ) {
            return GenieAcsServiceKind.TV
        }
        if (isDuoPlanName(planName)) {
            return GenieAcsServiceKind.DUO
        }
        return GenieAcsServiceKind.INTERNET
    }

    fun isDuoPlanName(planName: String?): Boolean {
        val name = planName.orEmpty().lowercase()
        return comboTokens.any { name.contains(it) }
    }

    fun wanConnectionName(
        subscriptionId: Int,
        kind: GenieAcsServiceKind,
        fullName: String?,
    ): String {
        val prefix = "$subscriptionId ${kind.name} "
        val budget = (WAN_NAME_MAX_LENGTH - prefix.length).coerceAtLeast(0)
        val sanitized = sanitizeDisplay(fullName).take(budget).trimEnd()
        return (prefix + sanitized).trim().take(WAN_NAME_MAX_LENGTH)
    }

    fun managedTags(
        subscriptionId: Int,
        kind: GenieAcsServiceKind,
        fullName: String?,
    ): List<String> {
        val customer = sanitizeDisplay(fullName)
        return buildList {
            add("sub-$subscriptionId")
            add("t:${kind.name}")
            if (customer.isNotBlank()) add("c:$customer")
        }
    }

    fun isManagedTag(tag: String): Boolean {
        return tag.startsWith("sub-") || tag.startsWith("t:") || tag.startsWith("c:")
    }

    fun tagsToRemove(existing: List<String>, desired: List<String>): List<String> {
        val desiredSet = desired.toSet()
        return existing.filter { isManagedTag(it) && it !in desiredSet }
    }

    fun sanitizeDisplay(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val withoutMarks = Normalizer.normalize(raw, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
        return withoutMarks
            .uppercase()
            .replace(Regex("[/?#&%]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
