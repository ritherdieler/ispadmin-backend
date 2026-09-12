package com.dscorp.wispadmin.wispadmin.service.mikrotik

import com.dscorp.wispadmin.routeros.port.MikrotikSession
import com.dscorp.wispadmin.wispadmin.config.PppoeProperties
import com.dscorp.wispadmin.wispadmin.data.model.Plan
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

data class PppoeSecretResult(
    val created: Boolean = false,
    val updated: Boolean = false,
    val profile: String? = null,
    val error: String? = null
) {
    val successful: Boolean get() = error == null
}

data class PppoeSession(
    val username: String,
    val address: String?,
    val uptime: String?,
    val callerId: String?,
    val service: String?
)

@Service
class PppoeManagerService(
    private val pppoeProperties: PppoeProperties = PppoeProperties(),
) {

    private val logger = LoggerFactory.getLogger(PppoeManagerService::class.java)

    fun ensureSecret(
        session: MikrotikSession,
        subscription: Subscription,
        password: String?
    ): PppoeSecretResult {
        val username = subscription.pppoeUsername?.trim()?.takeIf { it.isNotEmpty() }
            ?: return PppoeSecretResult(error = ERROR_NO_USERNAME)

        val profile = ensureProfile(session, subscription.plan)
            ?: return PppoeSecretResult(error = ERROR_NO_PROFILE)

        val cleanPassword = password?.trim()?.takeIf { it.isNotEmpty() }
        val existing = findSecret(session, username)

        if (existing == null) {
            val args = mutableMapOf(
                "name" to username,
                "profile" to profile,
                "service" to SERVICE_PPPOE
            )
            cleanPassword?.let { args["password"] = it }
            session.add(PATH_SECRET, args)
            logger.info("Secret PPPoE creado para {} con perfil {}", username, profile)
            return PppoeSecretResult(created = true, profile = profile)
        }

        val id = existing[".id"] ?: return PppoeSecretResult(error = ERROR_NO_ID)
        val changes = mutableMapOf<String, String>()
        if (existing["profile"] != profile) changes["profile"] = profile
        if (existing["service"] != SERVICE_PPPOE) changes["service"] = SERVICE_PPPOE
        if (existing["disabled"] == "true") changes["disabled"] = "false"
        cleanPassword?.let { changes["password"] = it }

        if (changes.isEmpty()) return PppoeSecretResult(profile = profile)

        session.set(PATH_SECRET, id, changes)
        logger.info("Secret PPPoE actualizado para {} con perfil {}", username, profile)
        return PppoeSecretResult(updated = true, profile = profile)
    }

    fun applyProfile(session: MikrotikSession, username: String, profile: String): Boolean {
        val id = findSecret(session, username)?.get(".id") ?: return false
        session.set(PATH_SECRET, id, mapOf("profile" to profile))
        return true
    }

    fun disableSecret(session: MikrotikSession, username: String): Boolean {
        val id = findSecret(session, username)?.get(".id") ?: return false
        session.set(PATH_SECRET, id, mapOf("disabled" to "true"))
        return true
    }

    fun enableSecret(session: MikrotikSession, username: String): Boolean {
        val id = findSecret(session, username)?.get(".id") ?: return false
        session.set(PATH_SECRET, id, mapOf("disabled" to "false"))
        return true
    }

    fun removeSecret(session: MikrotikSession, username: String): Boolean {
        val id = findSecret(session, username)?.get(".id") ?: return false
        session.remove(PATH_SECRET, id)
        return true
    }

    fun kickSession(session: MikrotikSession, username: String): Boolean {
        val id = session.print(PATH_ACTIVE, mapOf("name" to username))
            .firstOrNull()
            ?.get(".id")
            ?: return false
        session.remove(PATH_ACTIVE, id)
        return true
    }

    fun activeSessions(session: MikrotikSession): List<PppoeSession> =
        session.print(PATH_ACTIVE, emptyMap(), ACTIVE_PROPLIST).mapNotNull(::toSession)

    fun sessionOf(session: MikrotikSession, username: String): PppoeSession? =
        session.print(PATH_ACTIVE, mapOf("name" to username), ACTIVE_PROPLIST)
            .firstNotNullOfOrNull(::toSession)

    fun ensureProfile(session: MikrotikSession, plan: Plan?): String? {
        val name = PppoeProfileCatalog.profileName(plan) ?: return null
        val rate = PppoeProfileCatalog.rateLimit(plan) ?: return name
        val comment = PppoeProfileCatalog.profileComment(plan)
        val existing = findProfile(session, name)
        if (existing == null) {
            val args = mutableMapOf(
                "name" to name,
                "rate-limit" to rate,
                "remote-address" to pppoeProperties.profile.remotePool,
                "local-address" to pppoeProperties.profile.localAddress,
            )
            comment?.let { args["comment"] = it }
            session.add(PATH_PROFILE, args)
            logger.info("Perfil PPP {} creado", name)
            return name
        }
        val id = existing[".id"] ?: return name
        val changes = mutableMapOf<String, String>()
        if (comment != null && existing["comment"] != comment) changes["comment"] = comment
        if (existing["rate-limit"] != rate) changes["rate-limit"] = rate
        if (changes.isNotEmpty()) {
            session.set(PATH_PROFILE, id, changes)
        }
        return name
    }

    private fun findProfile(session: MikrotikSession, name: String): Map<String, String>? =
        session.print(PATH_PROFILE, mapOf("name" to name), PROFILE_PROPLIST).firstOrNull()

    private fun findSecret(session: MikrotikSession, username: String): Map<String, String>? =
        session.print(PATH_SECRET, mapOf("name" to username), SECRET_PROPLIST).firstOrNull()

    private fun toSession(row: Map<String, String>): PppoeSession? {
        val username = row["name"]?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return PppoeSession(
            username = username,
            address = row["address"],
            uptime = row["uptime"],
            callerId = row["caller-id"],
            service = row["service"]
        )
    }

    companion object {
        const val PATH_SECRET = "/ppp/secret"
        const val PATH_ACTIVE = "/ppp/active"
        const val PATH_PROFILE = "/ppp/profile"
        const val SERVICE_PPPOE = "pppoe"
        const val ERROR_NO_USERNAME = "La suscripción no tiene username PPPoE"
        const val ERROR_NO_PROFILE = "El plan no tiene velocidades válidas para derivar el perfil PPPoE"
        const val ERROR_NO_ID = "El secret PPPoE existente no tiene identificador"

        private val SECRET_PROPLIST = listOf(".id", "name", "profile", "service", "disabled", "remote-address")
        private val ACTIVE_PROPLIST = listOf(".id", "name", "address", "uptime", "caller-id", "service")
        private val PROFILE_PROPLIST = listOf(".id", "name", "rate-limit", "remote-address", "local-address", "comment")
    }
}
