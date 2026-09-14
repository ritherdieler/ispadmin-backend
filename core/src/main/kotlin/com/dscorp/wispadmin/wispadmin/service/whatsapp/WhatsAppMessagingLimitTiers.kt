package com.dscorp.wispadmin.wispadmin.service.whatsapp

object WhatsAppMessagingLimitTiers {

    private val LIMITS = mapOf(
        "TIER_50" to 50,
        "TIER_250" to 250,
        "TIER_1K" to 1000,
        "TIER_2K" to 2000,
        "TIER_10K" to 10000,
        "TIER_100K" to 100000
    )

    fun dailyLimitFor(tier: String?): Int? {
        if (tier == null) return null
        return LIMITS[tier.uppercase()]
    }

    fun resolveDailyLimit(tier: String?, override: Int?): Int? {
        val overrideLimit = override?.takeIf { it > 0 }
        if (overrideLimit != null) return overrideLimit
        return dailyLimitFor(tier)
    }
}
