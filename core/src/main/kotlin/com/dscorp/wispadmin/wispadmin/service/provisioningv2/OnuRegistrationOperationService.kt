package com.dscorp.wispadmin.wispadmin.service.provisioningv2

import com.dscorp.wispadmin.shared.config.GigafiberEnvironmentProperties
import com.dscorp.wispadmin.wispadmin.acsclient.AcsCpeCoreClient
import com.dscorp.wispadmin.wispadmin.acsclient.CoreOnboardingV2ContactRequest
import com.dscorp.wispadmin.wispadmin.acsclient.CoreOnboardingV2ContactState
import com.dscorp.wispadmin.wispadmin.oltclient.GatewayOnuActivationClient
import com.dscorp.wispadmin.wispadmin.oltclient.GatewayOnuV2AuthorizeRequest
import com.dscorp.wispadmin.wispadmin.oltclient.GatewayOnuV2AuthorizeResponse
import com.dscorp.wispadmin.wispadmin.service.FirebaseStorageService
import com.dscorp.wispadmin.wispadmin.service.cleanup.CleanupReport
import com.dscorp.wispadmin.wispadmin.service.cleanup.SubscriptionHardCleanupService
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

data class OnuRegistrationStartRequest(
    val requestKey: String,
    val serial: String,
    val target: ProvisioningOnuTarget,
)

@Service
class OnuRegistrationOperationService(
    private val journal: ProvisioningJournal,
    private val resources: ProvisioningResourceStore,
    private val gateway: GatewayOnuActivationClient,
    private val acs: AcsCpeCoreClient,
    private val storage: FirebaseStorageService,
    private val json: com.fasterxml.jackson.databind.ObjectMapper,
    private val environmentProperties: GigafiberEnvironmentProperties,
    private val hardCleanup: SubscriptionHardCleanupService,
) {
    fun start(operatorId: Long, operatorUsername: String, request: OnuRegistrationStartRequest): ProvisioningOperation {
        require(operatorId > 0) { "AUTHENTICATED_OPERATOR_REQUIRED" }
        val environment = environment()
        val serial = request.serial.trim().uppercase()
        require(serial.matches(SERIAL)) { "INVALID_ONU_SERIAL" }
        require(request.requestKey.matches(REQUEST_KEY)) { "INVALID_REQUEST_KEY" }
        validateTarget(request.target)
        journal.preauthorizationForRequest(environment, operatorId, request.requestKey)?.let { return it }
        journal.activeForOperator(environment, operatorId)?.let { return reuseActive(it, serial, request.target) }

        val operation = ProvisioningOperation(
            id = UUID.randomUUID().toString(),
            environment = environment,
            subscriptionId = null,
            serial = serial,
            flowVersion = 3,
            managementMode = ManagementProvisioningMode.PRECONFIGURED,
            phase = ProvisioningPhase.OLT_AUTHORIZATION,
            operatorId = operatorId,
            operatorUsername = operatorUsername.trim().take(128),
            registrationRequestKey = request.requestKey,
            onuTarget = request.target,
        )
        try {
            journal.insertPreauthorization(operation)
        } catch (ex: DataIntegrityViolationException) {
            journal.preauthorizationForRequest(environment, operatorId, request.requestKey)?.let { return it }
            journal.activeForOperator(environment, operatorId)?.let { return reuseActive(it, serial, request.target) }
            throw IllegalStateException("OPERATOR_ALREADY_HAS_ACTIVE_REGISTRATION", ex)
        }

        return authorizeAndCheckAcs(operation)
    }

    private fun authorizeAndCheckAcs(operation: ProvisioningOperation): ProvisioningOperation {
        val preparing = journal.updatePreauthorization(environment(), operation.id, operation.revision, { current ->
            val checkpoint = current.checkpoints[ProvisioningStage.OLT.ordinal]
            current.copy(state = ProvisioningState.RUNNING, checkpoints = current.checkpoints.withCheckpoint(
                ProvisioningStage.OLT,
                checkpoint.copy(state = CheckpointState.RUNNING, attempts = checkpoint.attempts + 1, touched = true, failure = null),
            ))
        })
        val authorized = try {
            gateway.authorizeV2(preparing.toAuthorizeRequest())
        } catch (ex: Exception) {
            return fail(preparing, ProvisioningStage.OLT, "OLT_AUTHORIZATION_FAILED",
                "No se pudo autorizar la ONU en la OLT.", ex)
        }
        if (authorized.externalId.isBlank() || !authorized.managementVlanReady || authorized.ontId < 0) {
            return fail(preparing, ProvisioningStage.OLT, "OLT_AUTHORIZATION_UNCONFIRMED",
                "La OLT no confirmó la autorización ni el service-port de gestión.", null)
        }

        val waiting = journal.updatePreauthorization(environment(), preparing.id, preparing.revision, transform = { current ->
            current.copy(
                phase = ProvisioningPhase.WAITING_FOR_ACS,
                state = ProvisioningState.WAITING,
                oltEvidence = OltProvisioningResource(authorized.externalId, authorized.board, authorized.port, authorized.ontId),
                checkpoints = current.checkpoints.withCheckpoint(
                    ProvisioningStage.OLT,
                    current.checkpoints[ProvisioningStage.OLT.ordinal].copy(state = CheckpointState.SUCCEEDED, failure = null),
                ),
            )
        })
        return checkAcs(waiting)
    }

    fun active(operatorId: Long): ProvisioningOperation? = journal.activeForOperator(environment(), operatorId)

    fun get(operatorId: Long, operationId: String): ProvisioningOperation = owned(operatorId, operationId)

    fun history(operatorId: Long, operationId: String, after: Long): List<ProvisioningEvent> {
        owned(operatorId, operationId)
        return journal.history(environment(), operationId, after)
    }

    fun saveDraft(operatorId: Long, operationId: String, request: JsonNode): JsonNode {
        val operation = owned(operatorId, operationId)
        requireReadyForForm(operation)
        require(request.isObject) { "DRAFT_OBJECT_REQUIRED" }
        val safeDraft = request.deepCopy<ObjectNode>().apply {
            put("registrationOperationId", operation.id)
            remove("facadePhotoUrl")
            resources.preauthorizationResource(environment(), operation.id, operatorId, PHOTO_RESOURCE_KEY)
                ?.let { put("facadePhotoUrl", it) }
        }
        resources.savePreauthorizationResource(environment(), operation.id, operatorId, DRAFT_RESOURCE_KEY,
            json.writeValueAsString(safeDraft))
        return safeDraft
    }

    fun draft(operatorId: Long, operationId: String): JsonNode? {
        val operation = owned(operatorId, operationId)
        requireReadyForForm(operation)
        val snapshot = resources.preauthorizationResource(environment(), operation.id, operatorId, DRAFT_RESOURCE_KEY)
            ?: return null
        val restored = json.readTree(snapshot)
        require(restored.isObject) { "DRAFT_OBJECT_REQUIRED" }
        return (restored as ObjectNode).apply {
            put("registrationOperationId", operation.id)
            remove("facadePhotoUrl")
            resources.preauthorizationResource(environment(), operation.id, operatorId, PHOTO_RESOURCE_KEY)
                ?.let { put("facadePhotoUrl", it) }
        }
    }

    fun savePhoto(operatorId: Long, operationId: String, file: MultipartFile): String {
        val operation = owned(operatorId, operationId)
        requireReadyForForm(operation)
        require(!file.isEmpty && file.size <= MAX_PHOTO_BYTES && file.contentType?.startsWith("image/") == true) {
            "INVALID_FACADE_PHOTO"
        }
        val uploaded = storage.uploadFileToFolder(file, PREAUTHORIZATION_PHOTO_FOLDER)
        try {
            val old = resources.preauthorizationResource(environment(), operation.id, operatorId, PHOTO_RESOURCE_KEY)
            resources.savePreauthorizationResource(environment(), operation.id, operatorId, PHOTO_RESOURCE_KEY, uploaded)
            if (!old.isNullOrBlank() && old != uploaded) runCatching { storage.deleteByPublicUrl(old) }
        } catch (ex: Exception) {
            runCatching { storage.deleteByPublicUrl(uploaded) }
            throw ex
        }
        return uploaded
    }

    /** Performs exactly one synchronous ACS contact check; waiting operations are never put on the worker queue. */
    fun retryAcs(operatorId: Long, operationId: String, expectedRevision: Long): ProvisioningOperation {
        val operation = owned(operatorId, operationId)
        require(operation.revision == expectedRevision) { "STALE_REVISION" }
        if (operation.phase == ProvisioningPhase.OLT_AUTHORIZATION) {
            require(operation.state in setOf(ProvisioningState.PENDING, ProvisioningState.RUNNING,
                ProvisioningState.WAITING, ProvisioningState.FAILED)) { "OLT_RETRY_NOT_ALLOWED" }
            return authorizeAndCheckAcs(operation)
        }
        require(operation.phase == ProvisioningPhase.WAITING_FOR_ACS && operation.state in
            setOf(ProvisioningState.WAITING, ProvisioningState.FAILED)) { "ACS_RETRY_NOT_ALLOWED" }
        require(operation.checkpoints[ProvisioningStage.OLT.ordinal].state == CheckpointState.SUCCEEDED) { "OLT_AUTHORIZATION_REQUIRED" }
        return checkAcs(operation)
    }

    fun cancel(operatorId: Long, operationId: String, expectedRevision: Long): ProvisioningOperation {
        return journal.requestRegistrationCancel(environment(), operationId, operatorId, expectedRevision)
    }

    fun cleanupCancelled(operatorId: Long, operationId: String): CleanupReport {
        val operation = journal.get(environment(), operationId)
            ?.takeIf { it.operatorId == operatorId && it.flowVersion == 3 }
            ?: throw NoSuchElementException("OPERATION_NOT_FOUND")
        val subscriptionId = operation.subscriptionId ?: throw IllegalStateException("SUBSCRIPTION_NOT_LINKED")
        check(operation.state == ProvisioningState.CANCELLED) { "CANCELLATION_NOT_CONFIRMED" }
        return hardCleanup.cleanup(subscriptionId)
    }

    fun unlinkedPreauthorizations(): List<ProvisioningOperation> = journal.unlinkedPreauthorizations(environment())

    fun adminGet(operationId: String): ProvisioningOperation = journal.get(environment(), operationId)
        ?.takeIf { it.subscriptionId == null }
        ?: throw NoSuchElementException("OPERATION_NOT_FOUND")

    fun adminHistory(operationId: String, after: Long): List<ProvisioningEvent> {
        adminGet(operationId)
        return journal.history(environment(), operationId, after)
    }

    private fun checkAcs(operation: ProvisioningOperation): ProvisioningOperation {
        val running = journal.updatePreauthorization(environment(), operation.id, operation.revision, transform = { current ->
            current.copy(
                state = ProvisioningState.RUNNING,
                checkpoints = current.checkpoints.withCheckpoint(ProvisioningStage.ACS_CONTACT,
                    current.checkpoints[ProvisioningStage.ACS_CONTACT.ordinal].copy(
                        state = CheckpointState.RUNNING,
                        attempts = current.checkpoints[ProvisioningStage.ACS_CONTACT.ordinal].attempts + 1,
                        failure = null,
                    )),
            )
        })
        return try {
            val contact = acs.onboardingV2Contact(CoreOnboardingV2ContactRequest(running.id, running.serial))
            if (contact.state == CoreOnboardingV2ContactState.WAITING) {
                journal.updatePreauthorization(environment(), running.id, running.revision, transform = { current ->
                    current.copy(
                        state = ProvisioningState.WAITING,
                        checkpoints = current.checkpoints.withCheckpoint(ProvisioningStage.ACS_CONTACT,
                            current.checkpoints[ProvisioningStage.ACS_CONTACT.ordinal].copy(state = CheckpointState.WAITING)),
                    )
                })
            } else {
                val resource = AcsContactProvisioningResource(
                    contact.deviceId?.takeIf(String::isNotBlank) ?: error("ACS_DEVICE_ID_MISSING"),
                    contact.model?.takeIf(String::isNotBlank) ?: error("ACS_MODEL_MISSING"),
                    contact.firmware?.takeIf(String::isNotBlank) ?: error("ACS_FIRMWARE_MISSING"),
                )
                journal.updatePreauthorization(environment(), running.id, running.revision, transform = { current ->
                    current.copy(
                        phase = ProvisioningPhase.READY_FOR_FORM,
                        state = ProvisioningState.READY_FOR_FORM,
                        acsContactEvidence = resource,
                        checkpoints = current.checkpoints.withCheckpoint(ProvisioningStage.ACS_CONTACT,
                            current.checkpoints[ProvisioningStage.ACS_CONTACT.ordinal].copy(
                                state = CheckpointState.SUCCEEDED, failure = null,
                            )),
                    )
                })
            }
        } catch (ex: Exception) {
            fail(running, ProvisioningStage.ACS_CONTACT, "ACS_CONTACT_CHECK_FAILED",
                "ACS todavía no pudo confirmar el primer contacto TR-069 de la ONU.", ex)
        }
    }

    private fun fail(
        operation: ProvisioningOperation,
        stage: ProvisioningStage,
        code: String,
        summary: String,
        cause: Throwable?,
    ): ProvisioningOperation {
        val technical = cause?.let(::safeTechnicalCause)
        return journal.updatePreauthorization(environment(), operation.id, operation.revision, transform = { current ->
            val checkpoint = current.checkpoints[stage.ordinal]
            current.copy(
                state = ProvisioningState.FAILED,
                checkpoints = current.checkpoints.withCheckpoint(stage, checkpoint.copy(
                    state = CheckpointState.FAILED,
                failure = ProvisioningFailure(code, summary, retryable = stage in setOf(ProvisioningStage.OLT, ProvisioningStage.ACS_CONTACT), technicalDetails = technical),
                )),
            )
        })
    }

    private fun owned(operatorId: Long, operationId: String): ProvisioningOperation = journal.get(environment(), operationId)
        ?.takeIf { it.operatorId == operatorId && it.subscriptionId == null }
        ?: throw NoSuchElementException("OPERATION_NOT_FOUND")

    private fun reuseActive(
        active: ProvisioningOperation,
        serial: String,
        target: ProvisioningOnuTarget,
    ): ProvisioningOperation {
        require(active.serial == serial && active.onuTarget == target) { "OPERATOR_ALREADY_HAS_ACTIVE_REGISTRATION" }
        return active
    }

    private fun environment(): String = environmentProperties.normalizedTag().ifBlank { "prod" }

    private fun requireReadyForForm(operation: ProvisioningOperation) {
        require(operation.phase == ProvisioningPhase.READY_FOR_FORM && operation.state == ProvisioningState.READY_FOR_FORM) {
            "ACS_CONFIRMATION_REQUIRED"
        }
    }

    private fun validateTarget(target: ProvisioningOnuTarget) {
        require(target.oltId.isNotBlank() && target.ponType.isNotBlank() && target.board.isNotBlank() &&
            target.port.isNotBlank() && target.onuType.isNotBlank()) { "ONU_TARGET_INCOMPLETE" }
        require(target.vlan in 1..4094 && target.vlan != ProvisioningV2RegistrationService.MANAGEMENT_VLAN) {
            "INTERNET_VLAN_REQUIRED"
        }
    }

    private fun ProvisioningOperation.toAuthorizeRequest(): GatewayOnuV2AuthorizeRequest {
        val target = requireNotNull(onuTarget)
        return GatewayOnuV2AuthorizeRequest(
            operationId = id,
            sn = serial,
            oltId = target.oltId,
            ponType = target.ponType,
            board = target.board,
            port = target.port,
            vlan = target.vlan.toString(),
            onuType = target.onuType,
            subscriberName = serial,
            zone = target.zone,
            onuMode = target.onuMode,
            customProfile = target.customProfile,
            managementMode = ManagementProvisioningMode.PRECONFIGURED.name,
        )
    }

    private fun List<StageCheckpoint>.withCheckpoint(stage: ProvisioningStage, value: StageCheckpoint) =
        map { if (it.stage == stage) value else it }

    private fun safeTechnicalCause(cause: Throwable): String {
        val statusCause = generateSequence(cause) { it.cause }.filterIsInstance<HttpStatusCodeException>().firstOrNull()
        val raw = statusCause?.responseBodyAsString?.ifBlank { statusCause.statusText }
            ?: generateSequence(cause) { it.cause }.mapNotNull { it.message?.takeIf(String::isNotBlank) }.firstOrNull()
            ?: cause.javaClass.simpleName
        return raw.replace(SECRET, "$1=<redacted>")
            .replace(Regex("(?i)(bearer\\s+)[A-Za-z0-9._~+/-]+=*"), "$1<redacted>")
            .replace(Regex("\\s+"), " ").take(300)
    }

    private companion object {
        const val DRAFT_RESOURCE_KEY = "registration-draft"
        const val PHOTO_RESOURCE_KEY = "registration-photo"
        const val PREAUTHORIZATION_PHOTO_FOLDER = "onu-registration-preauthorization"
        const val MAX_PHOTO_BYTES = 8L * 1024 * 1024
        val SERIAL = Regex("[A-Z0-9]{12,16}")
        val REQUEST_KEY = Regex("[A-Za-z0-9_-]{8,64}")
        val SECRET = Regex("(?i)(password|passwd|passphrase|secret|authorization|access[_-]?token|refresh[_-]?token|api[_-]?key|token)(\\\"?\\s*[:=]\\s*\\\"?)[^\\\"\\s,}]+")
    }
}
