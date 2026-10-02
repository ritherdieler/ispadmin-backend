package com.dscorp.wispadmin.wispadmin.service.provisioningv2

import com.dscorp.wispadmin.routeros.port.MikrotikSession
import com.dscorp.wispadmin.wispadmin.data.model.AccessMode
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.dscorp.wispadmin.wispadmin.service.mikrotik.DeviceSessionRunner
import com.dscorp.wispadmin.wispadmin.service.mikrotik.PppoeProfileCatalog
import com.dscorp.wispadmin.wispadmin.util.isValidIpAddress
import com.dscorp.wispadmin.wispadmin.service.whatsapp.CrmSecretCipher
import java.math.BigDecimal

/** Owns the v2 PPPoE secret or static IP queue. Shared profiles are never mutated here. */
class MikrotikProvisioningStageHandler(
    private val subscriptions: SubscriptionRepository,
    private val cipher: CrmSecretCipher,
    private val sessionRunner: DeviceSessionRunner,
) : ProvisioningStageHandler {
    override val stage = ProvisioningStage.MIKROTIK

    override fun reconcile(context: ProvisioningStageContext): StageObservation {
        val subscription = subscription(context.operation)
        if (subscription.accessMode == AccessMode.STATIC_IP) return reconcileQueue(context, subscription)
        val expected = expected(context.operation)
        var observation = StageObservation.NEEDS_APPLY
        sessionRunner(expected.subscription.hostDevice!!) { session ->
            val secret = findSecret(session, expected.username)
            observation = when {
                secret == null -> StageObservation.NEEDS_APPLY
                secret["comment"] != expected.owner -> ownershipConflict()
                matches(secret, expected) -> StageObservation.SATISFIED
                else -> configurationConflict()
            }
        }
        return observation
    }

    override fun apply(context: ProvisioningStageContext): StageObservation {
        val subscription = subscription(context.operation)
        if (subscription.accessMode == AccessMode.STATIC_IP) return applyQueue(context, subscription)
        val expected = expected(context.operation)
        sessionRunner(expected.subscription.hostDevice!!) { session ->
            val existing = findSecret(session, expected.username)
            when {
                existing != null && existing["comment"] != expected.owner -> ownershipConflict()
                existing != null && matches(existing, expected) -> return@sessionRunner
                existing != null -> configurationConflict()
            }
            context.assertLease()
            context.captureResource(RESOURCE_KEY, absentBaseline(expected))
            context.assertLease()
            session.add(PATH_SECRET, mapOf(
                "name" to expected.username,
                "profile" to expected.profile,
                "password" to expected.password,
                "service" to SERVICE_PPPOE,
                "disabled" to "false",
                "comment" to expected.owner,
            ))
            val confirmed = findSecret(session, expected.username)
            check(matches(confirmed, expected)) { "MIKROTIK_WRITE_UNCONFIRMED" }
        }
        return StageObservation.SATISFIED
    }

    override fun compensate(context: ProvisioningStageContext): StageObservation {
        val subscription = subscription(context.operation)
        if (subscription.accessMode == AccessMode.STATIC_IP) return compensateQueue(context, subscription)
        if (context.resourceSnapshot(RESOURCE_KEY) == null) return StageObservation.SATISFIED
        val expected = expected(context.operation)
        sessionRunner(expected.subscription.hostDevice!!) { session ->
            val secret = findSecret(session, expected.username) ?: return@sessionRunner
            if (secret["comment"] != expected.owner) ownershipConflict()
            session.print(PATH_ACTIVE, mapOf("name" to expected.username), listOf(".id")).forEach { active ->
                active[".id"]?.let { id -> context.assertLease(); session.remove(PATH_ACTIVE, id) }
            }
            val id = secret[".id"] ?: throw ProvisioningStepException(failure("MIKROTIK_SECRET_ID_MISSING"))
            context.assertLease()
            session.remove(PATH_SECRET, id)
            check(findSecret(session, expected.username) == null) { "MIKROTIK_DELETE_UNCONFIRMED" }
        }
        return StageObservation.SATISFIED
    }

    private fun subscription(operation: ProvisioningOperation): Subscription {
        val subscriptionId = operation.subscriptionId
            ?: throw ProvisioningStepException(failure("SUBSCRIPTION_ID_REQUIRED", retryable = false))
        val subscription = subscriptions.lockIdentityOwner(subscriptionId)
            ?: throw ProvisioningStepException(failure("SUBSCRIPTION_NOT_FOUND", retryable = false))
        if (!subscription.fiberOnuSn.equals(operation.serial, ignoreCase = true)) {
            throw ProvisioningStepException(failure("ONU_IDENTITY_MISMATCH", retryable = false))
        }
        return subscription
    }

    private fun expected(operation: ProvisioningOperation): ExpectedSecret {
        val subscription = subscription(operation)
        if (subscription.accessMode != AccessMode.PPPOE_DYNAMIC) {
            throw ProvisioningStepException(failure("PPPOE_DYNAMIC_REQUIRED", retryable = false))
        }
        val username = subscription.pppoeUsername?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw ProvisioningStepException(failure("PPPOE_USERNAME_REQUIRED", retryable = false))
        val password = subscription.pppoePasswordEnc?.takeIf(cipher::looksEncrypted)?.let(cipher::decrypt)
            ?: throw ProvisioningStepException(failure("PPPOE_PASSWORD_REQUIRED", retryable = false))
        val profile = PppoeProfileCatalog.profileName(subscription.plan)
            ?: throw ProvisioningStepException(failure("PPPOE_PROFILE_REQUIRED", retryable = false))
        if (subscription.hostDevice?.disabled != false) {
            throw ProvisioningStepException(failure("MIKROTIK_UNAVAILABLE", retryable = true))
        }
        return ExpectedSecret(subscription, username, password, profile, owner(operation))
    }

    private fun reconcileQueue(context: ProvisioningStageContext, subscription: Subscription): StageObservation {
        val expected = expectedQueue(context.operation, subscription)
        var observation = StageObservation.NEEDS_APPLY
        sessionRunner(expected.subscription.hostDevice!!) { session ->
            val rows = findQueues(session, expected.target)
            observation = when {
                rows.isEmpty() -> StageObservation.NEEDS_APPLY
                rows.size != 1 || rows.single()["comment"] != expected.owner -> queueOwnershipConflict()
                queueMatches(rows.single(), expected) -> StageObservation.SATISFIED
                else -> queueConfigurationConflict()
            }
        }
        return observation
    }

    private fun applyQueue(context: ProvisioningStageContext, subscription: Subscription): StageObservation {
        val expected = expectedQueue(context.operation, subscription)
        sessionRunner(expected.subscription.hostDevice!!) { session ->
            val rows = findQueues(session, expected.target)
            if (rows.isNotEmpty()) {
                if (rows.size != 1 || rows.single()["comment"] != expected.owner) queueOwnershipConflict()
                if (!queueMatches(rows.single(), expected)) queueConfigurationConflict()
                return@sessionRunner
            }
            context.assertLease()
            context.captureResource(RESOURCE_KEY, "{\"queue\":null,\"target\":\"${expected.target}\",\"owner\":\"${expected.owner}\"}")
            context.assertLease()
            session.add(PATH_QUEUE, mapOf(
                "name" to "[${context.operation.environment}] id:${expected.subscription.id} GFv2-${context.operation.id}",
                "target" to expected.target,
                "max-limit" to expected.maxLimit,
                "comment" to expected.owner,
            ))
            check(findQueues(session, expected.target).singleOrNull()?.let { queueMatches(it, expected) } == true) {
                "MIKROTIK_QUEUE_WRITE_UNCONFIRMED"
            }
        }
        return StageObservation.SATISFIED
    }

    private fun compensateQueue(context: ProvisioningStageContext, subscription: Subscription): StageObservation {
        if (context.resourceSnapshot(RESOURCE_KEY) == null) return StageObservation.SATISFIED
        val expected = expectedQueue(context.operation, subscription)
        sessionRunner(expected.subscription.hostDevice!!) { session ->
            val rows = findQueues(session, expected.target)
            if (rows.isEmpty()) return@sessionRunner
            if (rows.size != 1 || rows.single()["comment"] != expected.owner) queueOwnershipConflict()
            val id = rows.single()[".id"] ?: throw ProvisioningStepException(failure("MIKROTIK_QUEUE_ID_MISSING"))
            context.assertLease()
            session.remove(PATH_QUEUE, id)
            check(findQueues(session, expected.target).isEmpty()) { "MIKROTIK_QUEUE_DELETE_UNCONFIRMED" }
        }
        return StageObservation.SATISFIED
    }

    private fun expectedQueue(operation: ProvisioningOperation, subscription: Subscription): ExpectedQueue {
        val ip = subscription.ip?.takeIf { it.isValidIpAddress() }
            ?: throw ProvisioningStepException(failure("STATIC_IP_REQUIRED", retryable = false))
        val plan = subscription.plan ?: throw ProvisioningStepException(failure("PLAN_REQUIRED", retryable = false))
        if (subscription.hostDevice?.disabled != false) {
            throw ProvisioningStepException(failure("MIKROTIK_UNAVAILABLE"))
        }
        return ExpectedQueue(subscription, "$ip/32", "${plan.uploadSpeed}M/${plan.downloadSpeed}M", owner(operation))
    }

    private fun findQueues(session: MikrotikSession, target: String): List<Map<String, String>> =
        session.print(PATH_QUEUE, mapOf("target" to target), QUEUE_PROPERTIES)

    private fun queueMatches(row: Map<String, String>, expected: ExpectedQueue): Boolean =
        row["target"] == expected.target && sameRateLimit(row["max-limit"], expected.maxLimit) &&
            row["comment"] == expected.owner

    private fun sameRateLimit(actual: String?, expected: String): Boolean {
        fun parse(value: String): Pair<BigDecimal, BigDecimal>? {
            val parts = value.split('/')
            if (parts.size != 2) return null
            fun bitsPerSecond(part: String): BigDecimal? {
                val token = part.trim().uppercase()
                val suffix = token.lastOrNull()?.takeIf { it in "KMG" }
                val multiplier = when (suffix) {
                    'K' -> BigDecimal("1000")
                    'M' -> BigDecimal("1000000")
                    'G' -> BigDecimal("1000000000")
                    else -> BigDecimal.ONE
                }
                val number = if (suffix == null) token else token.dropLast(1)
                return number.toBigDecimalOrNull()?.multiply(multiplier)
            }
            val first = bitsPerSecond(parts[0]) ?: return null
            val second = bitsPerSecond(parts[1]) ?: return null
            return first to second
        }

        val actualRates = actual?.let(::parse) ?: return false
        val expectedRates = parse(expected) ?: return false
        return actualRates.first.compareTo(expectedRates.first) == 0 &&
            actualRates.second.compareTo(expectedRates.second) == 0
    }

    private fun queueOwnershipConflict(): Nothing = throw ProvisioningStepException(failure("MIKROTIK_QUEUE_OWNERSHIP_CONFLICT", false))
    private fun queueConfigurationConflict(): Nothing = throw ProvisioningStepException(failure("MIKROTIK_QUEUE_CONFIGURATION_CONFLICT", false))

    private fun findSecret(session: MikrotikSession, username: String): Map<String, String>? =
        session.print(PATH_SECRET, mapOf("name" to username), SECRET_PROPERTIES).firstOrNull()

    private fun matches(secret: Map<String, String>?, expected: ExpectedSecret): Boolean = secret != null &&
        secret["name"] == expected.username && secret["profile"] == expected.profile &&
        secret["service"] == SERVICE_PPPOE && secret["disabled"] != "true" && secret["comment"] == expected.owner

    private fun absentBaseline(expected: ExpectedSecret) =
        "{\"secret\":null,\"username\":\"${expected.username}\",\"owner\":\"${expected.owner}\"}"

    private fun owner(operation: ProvisioningOperation) = "GFv2-${operation.environment}-${operation.id}"

    private fun ownershipConflict(): Nothing = throw ProvisioningStepException(failure("MIKROTIK_SECRET_OWNERSHIP_CONFLICT", false))
    private fun configurationConflict(): Nothing = throw ProvisioningStepException(failure("MIKROTIK_SECRET_CONFIGURATION_CONFLICT", false))
    private fun failure(code: String, retryable: Boolean = true) = ProvisioningFailure(
        code, "No se pudo confirmar la configuración de MikroTik. Consulte el historial de la operación.", retryable)

    private data class ExpectedSecret(
        val subscription: Subscription,
        val username: String,
        val password: String,
        val profile: String,
        val owner: String,
    )

    private data class ExpectedQueue(
        val subscription: Subscription,
        val target: String,
        val maxLimit: String,
        val owner: String,
    )

    private companion object {
        const val RESOURCE_KEY = "mikrotik"
        const val PATH_SECRET = "/ppp/secret"
        const val PATH_ACTIVE = "/ppp/active"
        const val PATH_QUEUE = "/queue/simple"
        const val SERVICE_PPPOE = "pppoe"
        val SECRET_PROPERTIES = listOf(".id", "name", "profile", "service", "disabled", "comment")
        val QUEUE_PROPERTIES = listOf(".id", "name", "target", "max-limit", "comment")
    }
}
