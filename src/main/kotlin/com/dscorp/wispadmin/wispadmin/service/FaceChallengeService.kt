package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.config.FaceChallengeProperties
import com.dscorp.wispadmin.wispadmin.response.ChallengeStartResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Service
class FaceChallengeService(
    private val properties: FaceChallengeProperties
) {
    private val logger = LoggerFactory.getLogger(FaceChallengeService::class.java)
    private val activeChallenges = ConcurrentHashMap<String, Long>()

    fun start(): ChallengeStartResponse {
        evictExpired()
        val token = UUID.randomUUID().toString()

        if (properties.enabled) {
            activeChallenges[token] = System.currentTimeMillis() + properties.ttlMs
        }

        return ChallengeStartResponse(
            challengeId = token,
            expiresInMs = properties.ttlMs,
            enabled = properties.enabled
        )
    }

    fun isValid(token: String?): Boolean {
        if (!properties.enabled) return true
        if (token.isNullOrBlank()) return false
        val expiresAt = activeChallenges[token] ?: return false
        if (System.currentTimeMillis() > expiresAt) {
            activeChallenges.remove(token)
            return false
        }
        return true
    }

    fun consume(token: String?): Boolean {
        if (!properties.enabled) return true
        if (token.isNullOrBlank()) return false
        val expiresAt = activeChallenges.remove(token) ?: return false
        val valid = System.currentTimeMillis() <= expiresAt
        if (!valid) {
            logger.info("Reto activo expirado al consumir token={}", token)
        }
        return valid
    }

    fun isEnabled(): Boolean = properties.enabled
    fun requireForVerify(): Boolean = properties.enabled && properties.requireForVerify
    fun requireForIdentify(): Boolean = properties.enabled && properties.requireForIdentify

    private fun evictExpired() {
        val now = System.currentTimeMillis()
        if (activeChallenges.size > properties.maxActive) {
            activeChallenges.entries.removeIf { it.value < now }
        }
    }

}
