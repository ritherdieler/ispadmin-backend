package com.dscorp.wispadmin.wispadmin.service.subscription

import com.dscorp.wispadmin.wispadmin.config.PppoeProperties
import com.dscorp.wispadmin.wispadmin.data.model.AccessMigrationStage
import com.dscorp.wispadmin.wispadmin.data.model.AccessMode
import com.dscorp.wispadmin.wispadmin.data.model.PppoeProvisionStatus
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.data.model.SubscriptionAccessMigration
import com.dscorp.wispadmin.wispadmin.dto.AccessMigrationEligibleDto
import com.dscorp.wispadmin.wispadmin.dto.AccessMigrationProgressDto
import com.dscorp.wispadmin.wispadmin.extensions.executeCommand
import com.dscorp.wispadmin.wispadmin.oltclient.GatewayCpeAccessLayout
import com.dscorp.wispadmin.wispadmin.oltclient.GatewayCpeProvisionRequest
import com.dscorp.wispadmin.wispadmin.oltclient.GatewayOnuActivationClient
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionAccessMigrationRepository
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionAcsRepository
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.dscorp.wispadmin.wispadmin.service.mikrotik.DeviceSessionRunner
import com.dscorp.wispadmin.wispadmin.service.mikrotik.IMikroTikService
import com.dscorp.wispadmin.wispadmin.service.mikrotik.PppoeManagerService
import com.dscorp.wispadmin.wispadmin.service.mikrotik.SimpleQueueTarget
import com.dscorp.wispadmin.wispadmin.service.whatsapp.CrmSecretCipher
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.CompletableFuture

@Service
class AccessMigrationService(
    private val subscriptionRepository: SubscriptionRepository,
    private val migrationRepository: SubscriptionAccessMigrationRepository,
    private val subscriptionAcsRepository: SubscriptionAcsRepository,
    private val gatewayClients: ObjectProvider<GatewayOnuActivationClient>,
    private val pppoeManager: PppoeManagerService,
    private val mikrotikService: IMikroTikService,
    private val secretCipher: CrmSecretCipher,
    private val pppoeProperties: PppoeProperties,
) {
    var clock: Clock = Clock.systemDefaultZone()
    var asyncRunner: (Runnable) -> Unit = { CompletableFuture.runAsync(it) }
    var sessionRunner: DeviceSessionRunner = { device, block -> device.executeCommand(block) }
    private val logger = LoggerFactory.getLogger(AccessMigrationService::class.java)

    fun progress(subscriptionId: Int): AccessMigrationProgressDto? =
        migrationRepository.findTopBySubscriptionIdOrderByAttemptDesc(subscriptionId)?.toDto()

    fun listEligible(): List<AccessMigrationEligibleDto> {
        return subscriptionRepository.findAccessMigrationCandidates().map { subscription ->
            val acs = subscriptionAcsRepository.findById(subscription.id!!).orElse(null)
            val layout = liveLayoutOrNull(subscription.fiberOnuSn)
            val result = AccessMigrationEligibility.evaluate(
                subscription = subscription,
                plan = subscription.plan,
                cpeModel = AccessMigrationCpeModel(
                    productClass = layout?.productClass ?: acs?.productClass,
                    hasPppPath = layout?.hasPppPath ?: knownPppModel(layout?.productClass ?: acs?.productClass),
                ),
                acs = AccessMigrationAcsState(
                    connectionRequestUrl = layout?.connectionRequestUrl ?: acs?.connectionRequestUrl,
                    lastInformAt = layout?.lastInformAt ?: acs?.lastInformAt?.toString(),
                    wanIpPath = layout?.wanIpPath,
                    wanPppPath = layout?.wanPppPath,
                ),
            )
            AccessMigrationEligibleDto(
                subscriptionId = subscription.id!!,
                name = subscription.getFullName(),
                ip = subscription.ip,
                onuSn = subscription.fiberOnuSn,
                productClass = layout?.productClass ?: acs?.productClass,
                planName = subscription.plan?.name,
                reason = (result as? AccessMigrationEligibilityResult.Ineligible)?.reason,
                eligible = result.eligible,
            )
        }.filter { it.eligible }
    }

    @Transactional
    fun start(subscriptionId: Int): AccessMigrationProgressDto {
        val subscription = subscriptionRepository.findById(subscriptionId)
            .orElseThrow { IllegalArgumentException("Suscripción no encontrada con ID: $subscriptionId") }
        val existing = migrationRepository.findTopBySubscriptionIdOrderByAttemptDesc(subscriptionId)
        if (existing != null && existing.stage.isActive() && existing.stage != AccessMigrationStage.ELIGIBLE) {
            asyncRunner { runCatching { advance(subscriptionId) } }
            return existing.toDto()
        }
        val layout = requireLayout(subscription)
        val eligibility = AccessMigrationEligibility.evaluate(
            subscription = subscription,
            plan = subscription.plan,
            cpeModel = AccessMigrationCpeModel(layout.productClass, layout.hasPppPath),
            acs = AccessMigrationAcsState(
                connectionRequestUrl = layout.connectionRequestUrl,
                lastInformAt = layout.lastInformAt,
                wanIpPath = layout.wanIpPath,
                wanPppPath = layout.wanPppPath,
            ),
        )
        if (!eligibility.eligible) {
            throw IllegalStateException((eligibility as AccessMigrationEligibilityResult.Ineligible).reason)
        }
        if (AccessMigrationWanGuard.wouldLeaveTwoActiveWans(layout.wanIpPath, layout.wanPppPath)) {
            throw IllegalStateException("WANIP y WANPPP no comparten slot; abortado para no dejar dos WAN activas")
        }
        val row = if (existing == null || existing.stage.isTerminal()) {
            SubscriptionAccessMigration(
                subscriptionId = subscriptionId,
                stage = AccessMigrationStage.ELIGIBLE,
                attempt = (existing?.attempt ?: 0) + 1,
                previousIp = subscription.ip,
                previousVlan = subscription.vlan ?: "1",
                previousQueueTarget = SimpleQueueTarget.of(subscription),
                createdAt = now(),
                updatedAt = now(),
            )
        } else {
            existing.apply {
                stage = AccessMigrationStage.ELIGIBLE
                failureReason = null
                updatedAt = now()
            }
        }
        val saved = migrationRepository.save(row)
        asyncRunner { runCatching { advance(subscriptionId) } }
        return saved.toDto()
    }

    @Transactional
    fun advance(subscriptionId: Int) {
        val subscription = subscriptionRepository.findById(subscriptionId).orElse(null) ?: return
        val row = migrationRepository.findTopBySubscriptionIdOrderByAttemptDesc(subscriptionId) ?: return
        try {
            when (row.stage) {
                AccessMigrationStage.ELIGIBLE -> mark(row, AccessMigrationStage.OLT_READY) { ensureOltReady(subscription) }
                AccessMigrationStage.OLT_READY -> mark(row, AccessMigrationStage.SECRET_READY) { ensureSecret(subscription, row) }
                AccessMigrationStage.SECRET_READY -> mark(row, AccessMigrationStage.CPE_APPLIED) { applyCpe(subscription, row) }
                AccessMigrationStage.CPE_APPLIED -> mark(row, AccessMigrationStage.VERIFIED) { verify(subscription, row) }
                AccessMigrationStage.VERIFIED -> mark(row, AccessMigrationStage.QUEUE_CLEARED) { clearQueue(subscription, row) }
                AccessMigrationStage.QUEUE_CLEARED -> enterQuarantine(subscription, row)
                AccessMigrationStage.QUARANTINE, AccessMigrationStage.DONE,
                AccessMigrationStage.FAILED_REVERTED, AccessMigrationStage.FAILED_STRANDED -> return
            }
            val latest = migrationRepository.findTopBySubscriptionIdOrderByAttemptDesc(subscriptionId) ?: return
            if (latest.stage.isActive() && latest.stage != AccessMigrationStage.QUARANTINE) {
                advance(subscriptionId)
            }
        } catch (ex: Exception) {
            logger.error("Migración PPPoE fallida para {}: {}", subscriptionId, ex.message)
            fail(subscription, row, ex.message ?: "error desconocido")
        }
    }

    @Transactional
    fun finishQuarantine(row: SubscriptionAccessMigration) {
        val subscription = subscriptionRepository.findById(row.subscriptionId).orElse(null) ?: return
        val sn = subscription.fiberOnuSn ?: throw IllegalStateException("Sin serial de ONU")
        val gateway = gateway()
        gateway.removeServicePort(sn, 1)
        val device = subscription.hostDevice
        if (device != null && !row.previousIp.isNullOrBlank()) {
            sessionRunner(device) { session ->
                mikrotikService.findAndRemoveQueueByIp(session, row.previousIp!!)
                mikrotikService.removeIpFromAllCutLists(session, row.previousIp!!)
            }
        }
        subscription.ip = null
        subscription.ipPool = null
        subscriptionRepository.save(subscription)
        row.stage = AccessMigrationStage.DONE
        row.updatedAt = now()
        migrationRepository.save(row)
    }

    private fun ensureOltReady(subscription: Subscription) {
        val sn = subscription.fiberOnuSn ?: throw IllegalStateException("Sin serial de ONU")
        val ports = gateway().servicePorts(sn)
        if (100 !in ports.vlans) {
            throw IllegalStateException("La ONU no tiene service-port VLAN 100")
        }
    }

    private fun ensureSecret(subscription: Subscription, row: SubscriptionAccessMigration) {
        val device = subscription.hostDevice ?: throw IllegalStateException("Sin equipo host MikroTik")
        if (subscription.pppoeUsername.isNullOrBlank()) {
            subscription.pppoeUsername = PppoeCredentialFactory.username(subscription.id)
                ?: throw IllegalStateException("No se pudo derivar username PPPoE")
        }
        if (subscription.pppoePasswordEnc.isNullOrBlank()) {
            subscription.pppoePasswordEnc = secretCipher.encrypt(PppoeCredentialFactory.password())
        }
        val password = secretCipher.decrypt(subscription.pppoePasswordEnc!!)
        var resultError: String? = null
        sessionRunner(device) { session ->
            val result = pppoeManager.ensureSecret(session, subscription, password)
            if (!result.successful) resultError = result.error
        }
        if (resultError != null) throw IllegalStateException(resultError)
        row.pppoeUsername = subscription.pppoeUsername
        subscription.pppoeProvisionStatus = PppoeProvisionStatus.SECRET_CREATED
        subscriptionRepository.save(subscription)
        migrationRepository.save(row)
    }

    private fun applyCpe(subscription: Subscription, row: SubscriptionAccessMigration) {
        val sn = subscription.fiberOnuSn ?: throw IllegalStateException("Sin serial de ONU")
        val layout = requireLayout(subscription)
        if (AccessMigrationWanGuard.wouldLeaveTwoActiveWans(layout.wanIpPath, layout.wanPppPath)) {
            throw IllegalStateException("WANIP y WANPPP no comparten slot; abortado para no dejar dos WAN activas")
        }
        val password = subscription.pppoePasswordEnc
            ?.takeIf { secretCipher.looksEncrypted(it) }
            ?.let { secretCipher.decrypt(it) }
            ?: throw IllegalStateException("Sin contraseña PPPoE")
        val response = gateway().provision(
            GatewayCpeProvisionRequest(
                sn = sn,
                uniqueExternalId = subscription.id?.toString(),
                onuType = layout.productClass,
                wanVlanId = 100,
                pppoeUsername = subscription.pppoeUsername,
                pppoePassword = password,
            )
        )
        if (response.status.equals("FAILED", ignoreCase = true)) {
            throw IllegalStateException(response.message ?: "ACS provision failed")
        }
        row.pppoeUsername = subscription.pppoeUsername
        row.updatedAt = now()
        migrationRepository.save(row)
    }

    private fun verify(subscription: Subscription, row: SubscriptionAccessMigration) {
        val username = subscription.pppoeUsername ?: throw IllegalStateException("Sin username PPPoE")
        val device = subscription.hostDevice ?: throw IllegalStateException("Sin equipo host")
        var sessionAddress: String? = null
        sessionRunner(device) { session ->
            sessionAddress = pppoeManager.sessionOf(session, username)?.address
        }
        val address = sessionAddress?.substringBefore('/')
        if (address.isNullOrBlank() || !address.startsWith("10.64.")) {
            throw IllegalStateException("No hay sesión PPPoE en el pool dinámico")
        }
        val layout = requireLayout(subscription)
        val inform = parseInstant(layout.lastInformAt)
        if (inform == null || inform.isBefore(row.createdAt.atZone(ZoneId.systemDefault()).toInstant().minusSeconds(30))) {
            throw IllegalStateException("No hay Inform posterior del CPE")
        }
        subscription.pppoeLastIp = address
        subscription.pppoeProvisionStatus = PppoeProvisionStatus.SESSION_UP
        subscriptionRepository.save(subscription)
    }

    private fun clearQueue(subscription: Subscription, row: SubscriptionAccessMigration) {
        val ip = row.previousIp ?: subscription.ip
        val device = subscription.hostDevice
        if (device != null && !ip.isNullOrBlank()) {
            sessionRunner(device) { session ->
                mikrotikService.findAndRemoveQueueByIp(session, ip)
            }
        }
    }

    private fun enterQuarantine(subscription: Subscription, row: SubscriptionAccessMigration) {
        subscription.accessMode = AccessMode.PPPOE_DYNAMIC
        subscription.pppoeUsername = row.pppoeUsername ?: subscription.pppoeUsername
        subscriptionRepository.save(subscription)
        row.stage = AccessMigrationStage.QUARANTINE
        row.quarantineUntil = now().plusDays(pppoeProperties.migration.quarantineDays.toLong())
        row.updatedAt = now()
        migrationRepository.save(row)
    }

    private fun fail(subscription: Subscription, row: SubscriptionAccessMigration, reason: String) {
        row.failureReason = reason.take(500)
        try {
            if (row.stage == AccessMigrationStage.CPE_APPLIED || row.stage == AccessMigrationStage.VERIFIED) {
                val layout = liveLayoutOrNull(subscription.fiberOnuSn)
                val inform = parseInstant(layout?.lastInformAt)
                val recent = inform != null && inform.isAfter(Instant.now(clock).minusSeconds(15 * 60))
                if (recent) {
                    revertCpe(subscription, row)
                    row.stage = AccessMigrationStage.FAILED_REVERTED
                } else {
                    row.stage = AccessMigrationStage.FAILED_STRANDED
                }
            } else {
                row.stage = AccessMigrationStage.FAILED_REVERTED
            }
        } catch (ex: Exception) {
            logger.error("Rollback de migración fallido para {}: {}", subscription.id, ex.message)
            row.stage = AccessMigrationStage.FAILED_STRANDED
            row.failureReason = "${row.failureReason}; rollback: ${ex.message}".take(500)
        }
        row.updatedAt = now()
        migrationRepository.save(row)
    }

    private fun revertCpe(subscription: Subscription, row: SubscriptionAccessMigration) {
        val sn = subscription.fiberOnuSn ?: return
        val vlan = row.previousVlan?.toIntOrNull() ?: 1
        gateway().provision(
            GatewayCpeProvisionRequest(
                sn = sn,
                uniqueExternalId = subscription.id?.toString(),
                ip = row.previousIp,
                wanVlanId = vlan,
            )
        )
    }

    private fun mark(row: SubscriptionAccessMigration, next: AccessMigrationStage, block: () -> Unit) {
        block()
        row.stage = next
        row.updatedAt = now()
        migrationRepository.save(row)
    }

    private fun requireLayout(subscription: Subscription): GatewayCpeAccessLayout {
        val sn = subscription.fiberOnuSn ?: throw IllegalStateException("Sin serial de ONU")
        return gateway().accessLayout(sn)
    }

    private fun liveLayoutOrNull(sn: String?): GatewayCpeAccessLayout? {
        if (sn.isNullOrBlank()) return null
        return runCatching { gatewayClients.ifAvailable?.accessLayout(sn) }.getOrNull()
    }

    private fun gateway(): GatewayOnuActivationClient =
        gatewayClients.ifAvailable ?: throw IllegalStateException("Cliente OLT Gateway no habilitado")

    private fun knownPppModel(productClass: String?): Boolean {
        val value = productClass?.uppercase() ?: return false
        return value.contains("V2804AX15T") || value.contains("F6600R") || value.contains("VSOLVA74")
    }

    private fun parseInstant(value: String?): Instant? {
        val raw = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return runCatching { Instant.parse(raw) }.getOrNull()
    }

    private fun now(): LocalDateTime = LocalDateTime.ofInstant(Instant.now(clock), ZoneId.systemDefault())

    private fun SubscriptionAccessMigration.toDto() = AccessMigrationProgressDto(
        subscriptionId = subscriptionId,
        stage = stage,
        attempt = attempt,
        pppoeUsername = pppoeUsername,
        failureReason = failureReason,
        quarantineUntil = quarantineUntil?.toString(),
    )
}
