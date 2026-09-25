package com.dscorp.wispadmin.wispadmin.service.provisioningv2

import com.dscorp.wispadmin.routeros.port.MikrotikSession
import com.dscorp.wispadmin.wispadmin.data.model.AccessMode
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.dscorp.wispadmin.wispadmin.service.mikrotik.DeviceSessionRunner
import com.dscorp.wispadmin.wispadmin.service.mikrotik.PppoeProfileCatalog
import com.dscorp.wispadmin.wispadmin.service.whatsapp.CrmSecretCipher

/** Owns only the v2 PPPoE secret. Shared profiles are a precondition and are never mutated here. */
class MikrotikProvisioningStageHandler(
    private val subscriptions: SubscriptionRepository,
    private val cipher: CrmSecretCipher,
    private val sessionRunner: DeviceSessionRunner,
) : ProvisioningStageHandler {
    override val stage = ProvisioningStage.MIKROTIK

    override fun reconcile(context: ProvisioningStageContext): StageObservation {
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

    private fun expected(operation: ProvisioningOperation): ExpectedSecret {
        val subscription = subscriptions.lockIdentityOwner(operation.subscriptionId)
            ?: throw ProvisioningStepException(failure("SUBSCRIPTION_NOT_FOUND", retryable = false))
        if (!subscription.fiberOnuSn.equals(operation.serial, ignoreCase = true)) {
            throw ProvisioningStepException(failure("ONU_IDENTITY_MISMATCH", retryable = false))
        }
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
        code, "No se pudo confirmar la configuración PPPoE. Consulte el historial de la operación.", retryable)

    private data class ExpectedSecret(
        val subscription: Subscription,
        val username: String,
        val password: String,
        val profile: String,
        val owner: String,
    )

    private companion object {
        const val RESOURCE_KEY = "mikrotik"
        const val PATH_SECRET = "/ppp/secret"
        const val PATH_ACTIVE = "/ppp/active"
        const val SERVICE_PPPOE = "pppoe"
        val SECRET_PROPERTIES = listOf(".id", "name", "profile", "service", "disabled", "comment")
    }
}
