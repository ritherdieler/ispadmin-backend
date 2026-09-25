package com.dscorp.wispadmin.wispadmin.service.cleanup

import com.dscorp.wispadmin.routeros.port.MikrotikException
import com.dscorp.wispadmin.wispadmin.acsclient.AcsHttpClient
import com.dscorp.wispadmin.wispadmin.extensions.executeCommand
import com.dscorp.wispadmin.wispadmin.oltclient.OltGatewayHttpClient
import com.dscorp.wispadmin.wispadmin.repository.NetworkDeviceRepository
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionAcsRepository
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.dscorp.wispadmin.wispadmin.service.FirebaseStorageService
import com.dscorp.wispadmin.wispadmin.service.mikrotik.DeviceSessionRunner
import com.dscorp.wispadmin.wispadmin.trafficclient.TrafficHttpClient
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.ResourceAccessException

@Service
class SubscriptionHardCleanupService(
    private val subscriptions: SubscriptionRepository,
    private val acsLinks: SubscriptionAcsRepository,
    private val devices: NetworkDeviceRepository,
    private val journal: CleanupJournal,
    private val eraser: SubscriptionTraceEraser,
    private val storage: FirebaseStorageService,
    private val acsHttp: ObjectProvider<AcsHttpClient>,
    private val gatewayHttp: ObjectProvider<OltGatewayHttpClient>,
    private val trafficHttp: ObjectProvider<TrafficHttpClient>,
    private val json: ObjectMapper,
    private val sessionRunner: DeviceSessionRunner = { device, block -> device.executeCommand(block) },
) {
    fun cleanup(subscriptionId: Int): CleanupReport {
        ensure(subscriptionId)
        return SubscriptionHardCleanupOrchestrator(
            journal = journal,
            steps = listOf(
                "mikrotik" to { snapshot -> mikrotik(snapshot) },
                "acs" to { snapshot -> acs(snapshot) },
                "olt" to { snapshot -> olt(snapshot) },
                "firebase" to { snapshot -> firebase(snapshot) },
                "traffic" to { snapshot -> traffic(snapshot) },
            ),
            eraser = eraser,
        ).execute(subscriptionId)
    }

    private fun ensure(subscriptionId: Int) {
        try {
            journal.load(subscriptionId)
        } catch (_: NoSuchElementException) {
            val subscription = subscriptions.findById(subscriptionId).orElseThrow {
                CleanupStepException(CleanupFailure(
                    code = "SUBSCRIPTION_NOT_FOUND",
                    message = "No existe la suscripción.",
                    detail = "id=$subscriptionId",
                    retryable = false,
                ))
            }
            val deviceId = acsLinks.findById(subscriptionId).map { it.genieacsDeviceId }.orElse(null)
            journal.save(CleanupRun(CleanupSnapshot(
                subscriptionId = subscriptionId,
                serial = subscription.fiberOnuSn,
                ip = subscription.ip,
                pppoeUsername = subscription.pppoeUsername,
                acsDeviceId = deviceId,
                facadePhotoUrl = subscription.facadePhotoUrl,
                hostDeviceId = subscription.hostDevice?.id,
            )))
        }
    }

    private fun mikrotik(snapshot: CleanupSnapshot) {
        val ip = snapshot.ip?.trim().orEmpty()
        val pppoe = snapshot.pppoeUsername?.trim().orEmpty()
        if (ip.isEmpty() && pppoe.isEmpty()) return
        val device = snapshot.hostDeviceId?.let { devices.findById(it).orElse(null) }
            ?: throw CleanupStepException(CleanupFailure(
                code = "MIKROTIK_UNAVAILABLE",
                message = "No hay router para borrar la cola o el secreto. La suscripción sigue en el listado.",
                detail = "hostDeviceId=${snapshot.hostDeviceId}",
                retryable = false,
            ))
        try {
            sessionRunner(device) { session ->
                if (ip.isNotEmpty()) {
                    removeMatches(session, "/queue/simple", listOf(".id", "target")) { row ->
                        targets(row["target"]).contains(ip)
                    }
                    removeMatches(session, "/ip/firewall/address-list", listOf(".id", "list", "address")) { row ->
                        row["list"] == "deudores" && targets(row["address"]).contains(ip)
                    }
                }
                if (pppoe.isNotEmpty()) {
                    listOf("/ppp/active", "/ppp/secret").forEach { path ->
                        removeMatches(session, path, listOf(".id", "name")) { row -> row["name"] == pppoe }
                    }
                }
            }
        } catch (ex: MikrotikException) {
            throw CleanupStepException(CleanupFailure(
                code = "MIKROTIK_UNAVAILABLE",
                message = "MikroTik no respondió. La suscripción sigue en el listado.",
                detail = ex.message ?: "mikrotik",
                retryable = true,
            ))
        }
    }

    private fun removeMatches(
        session: com.dscorp.wispadmin.routeros.port.MikrotikSession,
        path: String,
        props: List<String>,
        match: (Map<String, String>) -> Boolean,
    ) {
        session.print(path, emptyMap(), props).filter(match).forEach { row ->
            row[".id"]?.let { session.remove(path, it) }
        }
    }

    private fun acs(snapshot: CleanupSnapshot) {
        val sn = snapshot.serial?.trim().orEmpty()
        val device = snapshot.acsDeviceId?.trim().orEmpty()
        if (sn.isEmpty() && device.isEmpty()) return
        val http = acsHttp.ifAvailable ?: throw missing("ACS")
        post(http::postJson, "/api/acs/v1/subscription/purge", mapOf("sn" to sn, "deviceId" to device), "ACS")
    }

    private fun olt(snapshot: CleanupSnapshot) {
        val sn = snapshot.serial?.trim().orEmpty()
        if (sn.isEmpty()) return
        val http = gatewayHttp.ifAvailable ?: throw missing("OLT")
        post({ path, body -> http.postJsonBody(path, body) }, "/api/olt-gateway/subscription/purge", mapOf("sn" to sn), "OLT")
    }

    private fun firebase(snapshot: CleanupSnapshot) {
        val url = snapshot.facadePhotoUrl?.trim().orEmpty()
        if (url.isEmpty()) return
        try {
            storage.deleteByPublicUrl(url)
        } catch (ex: IllegalArgumentException) {
            throw CleanupStepException(CleanupFailure(
                code = "FIREBASE_URL",
                message = "No se pudo identificar la foto de fachada. La suscripción sigue en el listado.",
                detail = ex.message ?: url,
                retryable = false,
            ))
        } catch (ex: Exception) {
            throw CleanupStepException(CleanupFailure(
                code = "FIREBASE_UNAVAILABLE",
                message = "No se pudo borrar la foto de fachada. La suscripción sigue en el listado.",
                detail = ex.message ?: "firebase",
                retryable = true,
            ))
        }
    }

    private fun traffic(snapshot: CleanupSnapshot) {
        val http = trafficHttp.ifAvailable ?: throw missing("TRAFFIC")
        post(
            http::postJson,
            "/api/traffic/v1/subscription/purge",
            mapOf(
                "subscriptionId" to snapshot.subscriptionId,
                "ip" to snapshot.ip,
                "pppoeUsername" to snapshot.pppoeUsername,
            ),
            "TRAFFIC",
        )
    }

    private fun post(
        call: (String, String) -> Any,
        path: String,
        body: Map<String, Any?>,
        step: String,
    ) {
        try {
            call(path, json.writeValueAsString(body))
        } catch (ex: HttpStatusCodeException) {
            if (ex.statusCode.value() == 404) return
            val retryable = ex.statusCode.is5xxServerError
            throw CleanupStepException(CleanupFailure(
                code = if (retryable) "${step}_UNAVAILABLE" else "${step}_REJECTED",
                message = "No se pudo limpiar $step. La suscripción sigue en el listado.",
                detail = "HTTP ${ex.statusCode.value()} $path",
                retryable = retryable,
            ))
        } catch (ex: ResourceAccessException) {
            throw CleanupStepException(CleanupFailure(
                code = "${step}_UNAVAILABLE",
                message = "No se pudo limpiar $step. La suscripción sigue en el listado.",
                detail = ex.message ?: path,
                retryable = true,
            ))
        }
    }

    private fun missing(step: String) = CleanupStepException(CleanupFailure(
        code = "${step}_CLIENT_DISABLED",
        message = "$step no está disponible. La suscripción sigue en el listado.",
        detail = step,
        retryable = false,
    ))

    private fun targets(raw: String?): Set<String> =
        raw.orEmpty().split(",").map { it.trim().substringBefore("/") }.filter { it.isNotEmpty() }.toSet()
}
