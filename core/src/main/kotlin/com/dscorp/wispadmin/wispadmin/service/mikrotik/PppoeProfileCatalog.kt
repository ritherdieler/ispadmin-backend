package com.dscorp.wispadmin.wispadmin.service.mikrotik

import com.dscorp.wispadmin.wispadmin.data.model.Plan

object PppoeProfileCatalog {

    const val CUT_PROFILE = "GF-CORTE"
    const val USERNAME_PREFIX = "gf"

    private const val PROFILE_PREFIX = "GF-"
    private val PLAN_PROFILE_PATTERN = Regex("^GF-\\d+-\\d+$")

    fun profileName(plan: Plan?): String? {
        val speeds = speedsOf(plan) ?: return null
        return "$PROFILE_PREFIX${speeds.first}-${speeds.second}"
    }

    fun rateLimit(plan: Plan?): String? {
        val speeds = speedsOf(plan) ?: return null
        return "${speeds.second}M/${speeds.first}M"
    }

    fun isCatalogProfile(profile: String?): Boolean {
        val name = profile?.trim() ?: return false
        return name == CUT_PROFILE || PLAN_PROFILE_PATTERN.matches(name)
    }

    fun username(subscriptionId: Int?): String? {
        val id = subscriptionId ?: return null
        if (id <= 0) return null
        return "$USERNAME_PREFIX$id"
    }

    fun profileComment(plan: Plan?): String? {
        val speeds = speedsOf(plan) ?: return null
        val name = plan?.name?.trim().orEmpty().ifBlank { "Plan" }
        val type = plan?.type?.name ?: "UNKNOWN"
        val price = formatPrice(plan?.price)
        return "$name | $type | ${speeds.first}/${speeds.second} Mbps | S/ $price"
    }

    private fun formatPrice(price: Double?): String {
        if (price == null) return "0"
        return if (price % 1.0 == 0.0) price.toInt().toString() else price.toString()
    }

    internal fun speedsOf(plan: Plan?): Pair<Int, Int>? {
        val download = plan?.downloadSpeed ?: return null
        val upload = plan.uploadSpeed ?: return null
        if (download <= 0 || upload <= 0) return null
        return download to upload
    }
}
