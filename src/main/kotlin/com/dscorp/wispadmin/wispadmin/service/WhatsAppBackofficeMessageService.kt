package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.config.WhatsAppProperties
import com.dscorp.wispadmin.wispadmin.data.model.Payment
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.data.model.EquipmentCondition
import com.dscorp.wispadmin.wispadmin.data.model.ServiceStatus
import com.dscorp.wispadmin.wispadmin.dto.WhatsAppInvalidPhoneCandidateDto
import com.dscorp.wispadmin.wispadmin.dto.WhatsAppMessageBatchResultDto
import com.dscorp.wispadmin.wispadmin.dto.WhatsAppMessageCandidateDto
import com.dscorp.wispadmin.wispadmin.dto.WhatsAppMessageCandidatesResponseDto
import com.dscorp.wispadmin.wispadmin.dto.WhatsAppMessageCandidatesTotalsDto
import com.dscorp.wispadmin.wispadmin.dto.WhatsAppMessageResultDto
import com.dscorp.wispadmin.wispadmin.dto.WhatsAppReminderBatchResultDto
import com.dscorp.wispadmin.wispadmin.dto.WhatsAppReminderCandidateDto
import com.dscorp.wispadmin.wispadmin.dto.WhatsAppReminderResultDto
import com.dscorp.wispadmin.wispadmin.dto.WhatsAppTemplateOptionDto
import com.dscorp.wispadmin.wispadmin.repository.PaymentRepository
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppMessageLogRepository
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppSyncedTemplateRepository
import com.dscorp.wispadmin.wispadmin.service.whatsapp.PeruvianPhoneValidator
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppTargetType
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppTemplateCatalog
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppTemplateCode
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppTemplateDefinition
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppTemplateDeliveryService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.sql.Timestamp

/**
 * Envio de mensajes WhatsApp por plantilla desde el backoffice.
 * No hay envio automatico programado: todo pasa por seleccion explicita del operador.
 */
@Service
class WhatsAppBackofficeMessageService(
    private val paymentRepository: PaymentRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val whatsAppMessageLogRepository: WhatsAppMessageLogRepository,
    private val syncedTemplateRepository: WhatsAppSyncedTemplateRepository,
    private val whatsAppProperties: WhatsAppProperties,
    private val templateDeliveryService: WhatsAppTemplateDeliveryService
) {

    fun listTemplates(): List<WhatsAppTemplateOptionDto> {
        val definitions = WhatsAppTemplateCatalog.all()
        val syncedByName = syncedTemplateRepository
            .findByNameIn(definitions.map { it.metaName })
            .associateBy { it.name }
        return definitions.map { toTemplateOptionDto(it, syncedByName[it.metaName]) }
    }

    fun listCandidates(templateCode: String): WhatsAppMessageCandidatesResponseDto {
        val definition = WhatsAppTemplateCatalog.getByCodeString(templateCode)
        if (definition.code == WhatsAppTemplateCode.SERVICE_CUT_NOTICE ||
            definition.code == WhatsAppTemplateCode.WELCOME_CUSTOMER
        ) {
            throw IllegalArgumentException(
                "La plantilla ${definition.messageType} no esta disponible para envio manual."
            )
        }

        val todayStart = LocalDate.now().atStartOfDay()
        val tomorrowStart = todayStart.plusDays(1)

        val partition = when (definition.code) {
            WhatsAppTemplateCode.PAYMENT_REMINDER -> {
                val limit = whatsAppProperties.messagingDailyLimitOverride.takeIf { it > 0 }
                    ?: DEFAULT_REMINDER_CANDIDATE_LIMIT
                partitionPaymentRows(
                    definition = definition,
                    rows = paymentRepository.findReminderCandidatePaymentRows(limit),
                    todayStart = todayStart,
                    tomorrowStart = tomorrowStart
                )
            }

            WhatsAppTemplateCode.PAYMENT_VALIDATION -> {
                val since = LocalDate.now()
                    .minusDays(whatsAppProperties.backoffice.validationPaidDays.toLong())
                    .atStartOfDay()
                partitionPaymentRows(
                    definition = definition,
                    rows = paymentRepository.findAllValidationCandidatePaymentRows(since),
                    todayStart = todayStart,
                    tomorrowStart = tomorrowStart
                )
            }

            else -> throw IllegalArgumentException("Plantilla no soportada para candidatos: ${definition.messageType}")
        }

        return WhatsAppMessageCandidatesResponseDto(
            candidates = partition.first,
            invalidPhones = partition.second,
            totals = WhatsAppMessageCandidatesTotalsDto(
                valid = partition.first.size,
                invalid = partition.second.size
            )
        )
    }

    fun sendSelected(templateCode: String, targetIds: List<Int>, operatorUsername: String? = null): WhatsAppMessageBatchResultDto {
        val definition = WhatsAppTemplateCatalog.getByCodeString(templateCode)
        val uniqueTargetIds = targetIds.distinct()
        val campaignId = if (uniqueTargetIds.size > 1) java.util.UUID.randomUUID().toString() else null

        if (uniqueTargetIds.isEmpty()) {
            return emptyBatchResult(definition.messageType)
        }

        return executeSendSelected(
            templateCode = templateCode,
            targetIds = uniqueTargetIds,
            campaignId = campaignId,
            operatorUsername = operatorUsername,
            onEachResult = null
        )
    }

    fun executeSendSelected(
        templateCode: String,
        targetIds: List<Int>,
        campaignId: String?,
        operatorUsername: String?,
        onEachResult: ((WhatsAppMessageResultDto) -> Unit)?
    ): WhatsAppMessageBatchResultDto {
        val definition = WhatsAppTemplateCatalog.getByCodeString(templateCode)
        val uniqueTargetIds = targetIds.distinct()
        if (uniqueTargetIds.isEmpty()) {
            return emptyBatchResult(definition.messageType)
        }

        val paymentRowsById = if (definition.targetType == com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppTargetType.PAYMENT) {
            paymentRepository.findWhatsAppPaymentRowsByIds(uniqueTargetIds)
                .associateBy { row -> row.intAt(0) }
        } else {
            emptyMap()
        }

        val concurrency = whatsAppProperties.backoffice.batchConcurrency.coerceAtLeast(1)
        val details = runBlocking {
            val semaphore = Semaphore(concurrency)
            uniqueTargetIds.map { targetId ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        val result = try {
                            attemptSend(
                                definition = definition,
                                targetId = targetId,
                                campaignId = campaignId,
                                operatorUsername = operatorUsername,
                                prefetchedPaymentRow = paymentRowsById[targetId]
                            )
                        } catch (e: Exception) {
                            WhatsAppMessageResultDto(
                                targetId = targetId,
                                targetType = definition.targetType.name,
                                paymentId = if (definition.targetType == com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppTargetType.PAYMENT) targetId else null,
                                subscriptionId = if (definition.targetType == com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppTargetType.SUBSCRIPTION) {
                                    targetId
                                } else {
                                    null
                                },
                                phone = null,
                                clientName = null,
                                status = WhatsAppTemplateDeliveryService.STATUS_FAILED,
                                reason = e.message ?: "No se pudo enviar el mensaje."
                            )
                        }
                        onEachResult?.invoke(result)
                        result
                    }
                }
            }.awaitAll()
        }

        return WhatsAppMessageBatchResultDto(
            templateCode = definition.messageType,
            requestedLimit = uniqueTargetIds.size,
            candidates = uniqueTargetIds.size,
            sent = details.count { it.status == WhatsAppTemplateDeliveryService.STATUS_SENT },
            skipped = details.count { it.status == WhatsAppTemplateDeliveryService.STATUS_SKIPPED },
            failed = details.count { it.status == WhatsAppTemplateDeliveryService.STATUS_FAILED },
            details = details
        )
    }

    fun listReminderCandidates(limit: Int = DEFAULT_CANDIDATE_LIMIT): List<WhatsAppReminderCandidateDto> {
        return listCandidates(WhatsAppTemplateCode.PAYMENT_REMINDER.name).candidates.map { candidate ->
            WhatsAppReminderCandidateDto(
                paymentId = candidate.paymentId ?: candidate.targetId,
                subscriptionId = candidate.subscriptionId,
                clientName = candidate.clientName,
                phone = candidate.phone,
                amount = candidate.amount ?: 0.0,
                billingDate = candidate.billingDate ?: "",
                alreadySentToday = candidate.alreadySentToday
            )
        }
    }

    fun sendSelectedReminders(paymentIds: List<Int>, operatorUsername: String? = null): WhatsAppReminderBatchResultDto {
        val batch = sendSelected(WhatsAppTemplateCode.PAYMENT_REMINDER.name, paymentIds, operatorUsername)
        return WhatsAppReminderBatchResultDto(
            requestedLimit = batch.requestedLimit,
            candidates = batch.candidates,
            sent = batch.sent,
            skipped = batch.skipped,
            failed = batch.failed,
            details = batch.details.map { detail ->
                WhatsAppReminderResultDto(
                    paymentId = detail.paymentId ?: detail.targetId,
                    subscriptionId = detail.subscriptionId,
                    phone = detail.phone,
                    status = detail.status,
                    reason = detail.reason
                )
            }
        )
    }

    private fun attemptSend(
        definition: WhatsAppTemplateDefinition,
        targetId: Int,
        campaignId: String? = null,
        operatorUsername: String? = null,
        prefetchedPaymentRow: Array<Any>? = null
    ): WhatsAppMessageResultDto {
        return when (definition.targetType) {
            WhatsAppTargetType.PAYMENT -> attemptSendPayment(
                definition,
                targetId,
                campaignId,
                operatorUsername,
                prefetchedPaymentRow
            )
            WhatsAppTargetType.SUBSCRIPTION -> attemptSendSubscription(definition, targetId, campaignId, operatorUsername)
        }
    }

    private fun attemptSendPayment(
        definition: WhatsAppTemplateDefinition,
        paymentId: Int,
        campaignId: String? = null,
        operatorUsername: String? = null,
        prefetchedPaymentRow: Array<Any>? = null
    ): WhatsAppMessageResultDto {
        val row = prefetchedPaymentRow
            ?: paymentRepository.findWhatsAppPaymentRowById(paymentId).firstOrNull()
            ?: return skippedPaymentResult(paymentId, null, null, null, "Factura no encontrada.")

        val context = paymentContextFromRow(row)
        val payment = context.payment
        val subscription = context.subscription
        val subscriptionId = subscription.id
        val phone = subscription.phone
        val clientName = subscription.getFullName()

        val validationError = validatePhone(subscription, phone)
        if (validationError != null) {
            return skippedPaymentResult(paymentId, subscriptionId, phone, clientName, validationError)
        }

        if (definition.code == WhatsAppTemplateCode.PAYMENT_REMINDER && payment.paid) {
            return skippedPaymentResult(
                paymentId,
                subscriptionId,
                phone,
                clientName,
                "La factura ya se encuentra pagada."
            )
        }
        if (definition.code == WhatsAppTemplateCode.PAYMENT_VALIDATION && !payment.paid) {
            return skippedPaymentResult(
                paymentId,
                subscriptionId,
                phone,
                clientName,
                "La factura no esta marcada como pagada."
            )
        }
        if (definition.code == WhatsAppTemplateCode.PAYMENT_VALIDATION && payment.paymentDateDatetime == null) {
            return skippedPaymentResult(
                paymentId,
                subscriptionId,
                phone,
                clientName,
                "La factura no tiene fecha de pago registrada."
            )
        }

        return try {
            deliverMessage(
                definition = definition,
                subscription = subscription,
                payment = payment,
                oldestUnpaidPayment = null,
                paymentId = payment.id,
                subscriptionId = subscription.id,
                phone = phone!!,
                targetId = paymentId,
                campaignId = campaignId,
                operatorUsername = operatorUsername
            )
            sentPaymentResult(paymentId, subscription.id, phone, clientName)
        } catch (e: IllegalStateException) {
            skippedPaymentResult(
                paymentId,
                subscriptionId,
                phone,
                clientName,
                e.message ?: "Ya se envio un mensaje de este tipo hoy."
            )
        } catch (e: Exception) {
            failedPaymentResult(
                paymentId,
                subscriptionId,
                phone,
                clientName,
                e.message ?: "No se pudo enviar el mensaje."
            )
        }
    }

    private fun attemptSendSubscription(
        definition: WhatsAppTemplateDefinition,
        subscriptionId: Int,
        campaignId: String? = null,
        operatorUsername: String? = null
    ): WhatsAppMessageResultDto {
        val row = subscriptionRepository.findWhatsAppSubscriptionRowById(subscriptionId).firstOrNull()
            ?: return skippedSubscriptionResult(subscriptionId, null, null, "Cliente no encontrado.")

        val subscription = subscriptionFromRow(row)
        val phone = subscription.phone
        val clientName = subscription.getFullName()
        val validationError = validatePhone(subscription, phone)
        if (validationError != null) {
            return skippedSubscriptionResult(subscriptionId, phone, clientName, validationError)
        }

        if (definition.code == WhatsAppTemplateCode.SERVICE_CUT_NOTICE &&
            subscription.serviceStatus != ServiceStatus.CUT_OFF &&
            subscription.isServiceCutOff != true
        ) {
            return skippedSubscriptionResult(
                subscriptionId,
                phone,
                clientName,
                "El cliente no tiene servicio cortado."
            )
        }

        val oldestUnpaid = if (definition.code == WhatsAppTemplateCode.SERVICE_CUT_NOTICE) {
            paymentFromOldestUnpaidRow(subscriptionId)
                ?: return skippedSubscriptionResult(
                    subscriptionId,
                    phone,
                    clientName,
                    "El cliente no tiene facturas pendientes."
                )
        } else {
            null
        }

        return try {
            deliverMessage(
                definition = definition,
                subscription = subscription,
                payment = null,
                oldestUnpaidPayment = oldestUnpaid,
                paymentId = oldestUnpaid?.id,
                subscriptionId = subscription.id,
                phone = phone!!,
                targetId = subscriptionId,
                campaignId = campaignId,
                operatorUsername = operatorUsername
            )
            sentSubscriptionResult(subscriptionId, phone, clientName)
        } catch (e: IllegalStateException) {
            skippedSubscriptionResult(
                subscriptionId,
                phone,
                clientName,
                e.message ?: "Ya se envio un mensaje de este tipo hoy."
            )
        } catch (e: Exception) {
            failedSubscriptionResult(
                subscriptionId,
                phone,
                clientName,
                e.message ?: "No se pudo enviar el mensaje."
            )
        }
    }

    private fun deliverMessage(
        definition: WhatsAppTemplateDefinition,
        subscription: Subscription,
        payment: Payment?,
        oldestUnpaidPayment: Payment?,
        paymentId: Int?,
        subscriptionId: Int?,
        phone: String,
        targetId: Int,
        campaignId: String? = null,
        operatorUsername: String? = null
    ) {
        val todayStart = LocalDate.now().atStartOfDay()
        val tomorrowStart = todayStart.plusDays(1)

        if (hasSentToday(definition, targetId, todayStart, tomorrowStart)) {
            throw IllegalStateException("Ya se envio un mensaje de este tipo por WhatsApp hoy.")
        }

        templateDeliveryService.deliverTemplate(
            definition = definition,
            subscription = subscription,
            payment = payment,
            oldestUnpaidPayment = oldestUnpaidPayment,
            paymentId = paymentId,
            subscriptionId = subscriptionId,
            phone = phone,
            campaignId = campaignId,
            operatorUsername = operatorUsername
        )
    }

    private fun partitionPaymentRows(
        definition: WhatsAppTemplateDefinition,
        rows: List<Array<Any>>,
        todayStart: LocalDateTime,
        tomorrowStart: LocalDateTime
    ): Pair<List<WhatsAppMessageCandidateDto>, List<WhatsAppInvalidPhoneCandidateDto>> {
        val candidates = mutableListOf<WhatsAppMessageCandidateDto>()
        val invalidPhones = mutableListOf<WhatsAppInvalidPhoneCandidateDto>()

        val validPaymentIds = rows.mapNotNull { row ->
            row.intAt(0).takeIf { phoneInvalidReason(row.stringAt(4)) == null }
        }
        val sentTodayPaymentIds = paymentIdsSentToday(definition, validPaymentIds, todayStart, tomorrowStart)

        rows.forEach { row ->
            val paymentId = row.intAt(0)
            val subscriptionId = row.intAt(1)
            val clientName = buildClientName(row.stringAt(2), row.stringAt(3))
            val phone = row.stringAt(4)
            val phoneReason = phoneInvalidReason(phone)

            if (phoneReason != null) {
                invalidPhones += WhatsAppInvalidPhoneCandidateDto(
                    subscriptionId = subscriptionId,
                    targetId = paymentId,
                    targetType = definition.targetType.name,
                    clientName = clientName,
                    phone = phone?.takeIf { it.isNotBlank() },
                    reason = phoneReason
                )
                return@forEach
            }

            val amountToPay = row.doubleAt(5)
            val amountPaid = row.doubleAtOrNull(6)
            val billingDate = row.localDateTimeAt(7)?.format(DATE_FORMAT)
            val paymentDate = row.localDateTimeAt(8)?.format(DATE_FORMAT)

            candidates += WhatsAppMessageCandidateDto(
                targetType = definition.targetType.name,
                targetId = paymentId,
                paymentId = paymentId,
                subscriptionId = subscriptionId,
                clientName = clientName,
                phone = phone!!,
                amount = amountPaid ?: amountToPay,
                billingDate = billingDate,
                paymentDate = paymentDate,
                installationDate = null,
                alreadySentToday = sentTodayPaymentIds.contains(paymentId)
            )
        }

        return candidates to invalidPhones
    }

    private fun paymentIdsSentToday(
        definition: WhatsAppTemplateDefinition,
        paymentIds: Collection<Int>,
        todayStart: LocalDateTime,
        tomorrowStart: LocalDateTime
    ): Set<Int> {
        if (paymentIds.isEmpty()) return emptySet()
        return whatsAppMessageLogRepository.findPaymentIdsSentToday(
            paymentIds,
            definition.messageType,
            WhatsAppTemplateDeliveryService.STATUS_SENT,
            todayStart,
            tomorrowStart
        )
    }

    private fun phoneInvalidReason(phone: String?): String? {
        if (phone.isNullOrBlank()) return "Teléfono inválido o vacío"
        if (!PeruvianPhoneValidator.isValid(phone)) {
            return "El telefono debe ser un celular peruano valido."
        }
        return null
    }

    private fun toPaymentCandidate(
        definition: WhatsAppTemplateDefinition,
        payment: Payment,
        todayStart: LocalDateTime,
        tomorrowStart: LocalDateTime
    ): WhatsAppMessageCandidateDto? {
        val paymentId = payment.id ?: return null
        val subscription = payment.subscription ?: return null
        val subscriptionId = subscription.id ?: return null
        val phone = subscription.phone?.takeIf { it.isNotBlank() } ?: return null
        if (!PeruvianPhoneValidator.isValid(phone)) return null

        return WhatsAppMessageCandidateDto(
            targetType = definition.targetType.name,
            targetId = paymentId,
            paymentId = paymentId,
            subscriptionId = subscriptionId,
            clientName = subscription.getFullName(),
            phone = phone,
            amount = when (definition.code) {
                WhatsAppTemplateCode.PAYMENT_VALIDATION -> payment.amountPaid ?: payment.amountToPay
                else -> payment.amountToPay
            },
            billingDate = payment.billingDateDatetime.format(DATE_FORMAT),
            paymentDate = payment.paymentDateDatetime?.format(DATE_FORMAT),
            installationDate = null,
            alreadySentToday = hasSentToday(definition, paymentId, todayStart, tomorrowStart)
        )
    }

    private fun toSubscriptionCandidate(
        definition: WhatsAppTemplateDefinition,
        subscription: Subscription,
        todayStart: LocalDateTime,
        tomorrowStart: LocalDateTime
    ): WhatsAppMessageCandidateDto? {
        val subscriptionId = subscription.id ?: return null
        val phone = subscription.phone?.takeIf { it.isNotBlank() } ?: return null
        if (!PeruvianPhoneValidator.isValid(phone)) return null

        val oldestUnpaid = if (definition.code == WhatsAppTemplateCode.SERVICE_CUT_NOTICE) {
            paymentFromOldestUnpaidRow(subscriptionId)
        } else {
            null
        }

        return WhatsAppMessageCandidateDto(
            targetType = definition.targetType.name,
            targetId = subscriptionId,
            paymentId = oldestUnpaid?.id,
            subscriptionId = subscriptionId,
            clientName = subscription.getFullName(),
            phone = phone,
            amount = oldestUnpaid?.amountToPay,
            billingDate = oldestUnpaid?.billingDateDatetime?.format(DATE_FORMAT),
            paymentDate = null,
            installationDate = subscription.subscriptionDatetime?.format(DATE_FORMAT),
            alreadySentToday = hasSentToday(definition, subscriptionId, todayStart, tomorrowStart)
        )
    }

    private fun toPaymentCandidateFromRow(
        definition: WhatsAppTemplateDefinition,
        row: Array<Any>,
        todayStart: LocalDateTime,
        tomorrowStart: LocalDateTime
    ): WhatsAppMessageCandidateDto? {
        val paymentId = row.intAt(0)
        val subscriptionId = row.intAt(1)
        val clientName = buildClientName(row.stringAt(2), row.stringAt(3))
        val phone = row.stringAt(4) ?: return null
        if (!PeruvianPhoneValidator.isValid(phone)) return null

        val amountToPay = row.doubleAt(5)
        val amountPaid = row.doubleAtOrNull(6)
        val billingDate = row.localDateTimeAt(7)?.format(DATE_FORMAT)
        val paymentDate = row.localDateTimeAt(8)?.format(DATE_FORMAT)

        return WhatsAppMessageCandidateDto(
            targetType = definition.targetType.name,
            targetId = paymentId,
            paymentId = paymentId,
            subscriptionId = subscriptionId,
            clientName = clientName,
            phone = phone,
            amount = amountPaid ?: amountToPay,
            billingDate = billingDate,
            paymentDate = paymentDate,
            installationDate = null,
            alreadySentToday = hasSentToday(definition, paymentId, todayStart, tomorrowStart)
        )
    }

    private fun toSubscriptionCandidateFromRow(
        definition: WhatsAppTemplateDefinition,
        row: Array<Any>,
        todayStart: LocalDateTime,
        tomorrowStart: LocalDateTime
    ): WhatsAppMessageCandidateDto? {
        val subscriptionId = row.intAt(0)
        val clientName = buildClientName(row.stringAt(1), row.stringAt(2))
        val phone = row.stringAt(3) ?: return null
        if (!PeruvianPhoneValidator.isValid(phone)) return null

        val installationDate = row.localDateTimeAt(4)?.format(DATE_FORMAT)

        return WhatsAppMessageCandidateDto(
            targetType = definition.targetType.name,
            targetId = subscriptionId,
            paymentId = null,
            subscriptionId = subscriptionId,
            clientName = clientName,
            phone = phone,
            amount = null,
            billingDate = null,
            paymentDate = null,
            installationDate = installationDate,
            alreadySentToday = hasSentToday(definition, subscriptionId, todayStart, tomorrowStart)
        )
    }

    private fun subscriptionFromRow(row: Array<Any>): Subscription {
        return Subscription(
            firstName = row.stringAt(1),
            lastName = row.stringAt(2),
            phone = row.stringAt(3),
            serviceStatus = ServiceStatus.valueOf(row.stringAt(4) ?: ServiceStatus.ACTIVE.name),
            equipmentCondition = EquipmentCondition.LOAN
        ).apply {
            id = row.intAt(0)
        }
    }

    private fun paymentContextFromRow(row: Array<Any>): PaymentContext {
        val subscription = Subscription(
            firstName = row.stringAt(2),
            lastName = row.stringAt(3),
            phone = row.stringAt(4),
            equipmentCondition = EquipmentCondition.LOAN
        ).apply {
            id = row.intAt(1)
        }

        val payment = Payment(
            discountAmount = 0.0,
            paid = row.booleanAt(9),
            amountToPay = row.doubleAt(5),
            amountPaid = row.doubleAtOrNull(6),
            billingDateDatetime = row.localDateTimeAt(7) ?: LocalDateTime.now(),
            paymentDateDatetime = row.localDateTimeAt(8),
            subscription = subscription
        ).apply {
            id = row.intAt(0)
        }

        return PaymentContext(payment = payment, subscription = subscription)
    }

    private data class PaymentContext(
        val payment: Payment,
        val subscription: Subscription
    )

    private fun paymentFromOldestUnpaidRow(subscriptionId: Int): Payment? {
        val row = paymentRepository.findOldestUnpaidPaymentRow(subscriptionId).firstOrNull()
            ?: return null

        return Payment(
            discountAmount = 0.0,
            paid = false,
            amountToPay = row.doubleAt(1),
            billingDateDatetime = row.localDateTimeAt(2) ?: LocalDateTime.now()
        ).apply {
            id = row.intAt(0)
        }
    }

    private fun buildClientName(firstName: String?, lastName: String?): String {
        return listOfNotNull(firstName?.trim()?.takeIf { it.isNotEmpty() }, lastName?.trim()?.takeIf { it.isNotEmpty() })
            .joinToString(" ")
            .ifBlank { "Cliente" }
    }

    private fun Array<Any>.intAt(index: Int): Int = (this[index] as Number).toInt()

    private fun Array<Any>.doubleAt(index: Int): Double = (this[index] as Number).toDouble()

    private fun Array<Any>.doubleAtOrNull(index: Int): Double? {
        return this.getOrNull(index)?.let { (it as Number).toDouble() }
    }

    private fun Array<Any>.booleanAt(index: Int): Boolean {
        return when (val value = this[index]) {
            is Boolean -> value
            is Number -> value.toInt() != 0
            else -> value.toString().toBoolean()
        }
    }

    private fun Array<Any>.stringAt(index: Int): String? {
        return this.getOrNull(index)?.toString()?.takeIf { it.isNotBlank() }
    }

    private fun Array<Any>.localDateTimeAt(index: Int): LocalDateTime? {
        return when (val value = this.getOrNull(index)) {
            null -> null
            is LocalDateTime -> value
            is Timestamp -> value.toLocalDateTime()
            is java.util.Date -> value.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
            else -> null
        }
    }

    private fun hasSentToday(
        definition: WhatsAppTemplateDefinition,
        targetId: Int,
        todayStart: LocalDateTime,
        tomorrowStart: LocalDateTime
    ): Boolean {
        return when (definition.targetType) {
            WhatsAppTargetType.PAYMENT ->
                whatsAppMessageLogRepository.existsByPaymentIdAndMessageTypeAndStatusAndCreatedAtBetween(
                    paymentId = targetId,
                    messageType = definition.messageType,
                    status = WhatsAppTemplateDeliveryService.STATUS_SENT,
                    startDate = todayStart,
                    endDate = tomorrowStart
                )

            WhatsAppTargetType.SUBSCRIPTION ->
                whatsAppMessageLogRepository.existsBySubscriptionIdAndMessageTypeAndStatusAndCreatedAtBetween(
                    subscriptionId = targetId,
                    messageType = definition.messageType,
                    status = WhatsAppTemplateDeliveryService.STATUS_SENT,
                    startDate = todayStart,
                    endDate = tomorrowStart
                )
        }
    }

    private fun validatePhone(subscription: Subscription?, phone: String?): String? {
        if (subscription == null) return "El registro no tiene cliente asociado."
        if (phone.isNullOrBlank()) return "El cliente no tiene telefono registrado."
        if (!PeruvianPhoneValidator.isValid(phone)) {
            return "El telefono debe ser un celular peruano valido."
        }
        return null
    }

    private fun toTemplateOptionDto(
        definition: WhatsAppTemplateDefinition,
        synced: com.dscorp.wispadmin.wispadmin.data.model.WhatsAppSyncedTemplate?
    ): WhatsAppTemplateOptionDto {
        return WhatsAppTemplateOptionDto(
            code = definition.messageType,
            metaName = definition.metaName,
            label = definition.label,
            description = definition.description,
            targetType = definition.targetType.name,
            showAmount = definition.code in setOf(
                WhatsAppTemplateCode.PAYMENT_REMINDER,
                WhatsAppTemplateCode.PAYMENT_VALIDATION,
                WhatsAppTemplateCode.SERVICE_CUT_NOTICE
            ),
            showBillingDate = definition.code in setOf(
                WhatsAppTemplateCode.PAYMENT_REMINDER,
                WhatsAppTemplateCode.SERVICE_CUT_NOTICE
            ),
            showPaymentDate = definition.code == WhatsAppTemplateCode.PAYMENT_VALIDATION,
            showInstallationDate = definition.code == WhatsAppTemplateCode.WELCOME_CUSTOMER,
            manualSendEnabled = definition.code in setOf(
                WhatsAppTemplateCode.PAYMENT_REMINDER,
                WhatsAppTemplateCode.PAYMENT_VALIDATION
            ),
            metaStatus = synced?.status,
            metaQuality = synced?.qualityScore,
            category = synced?.category
        )
    }

    private fun emptyBatchResult(templateCode: String) = WhatsAppMessageBatchResultDto(
        templateCode = templateCode,
        requestedLimit = 0,
        candidates = 0,
        sent = 0,
        skipped = 0,
        failed = 0,
        details = emptyList()
    )

    private fun sentPaymentResult(
        paymentId: Int,
        subscriptionId: Int?,
        phone: String,
        clientName: String?
    ) =
        WhatsAppMessageResultDto(
            targetId = paymentId,
            targetType = WhatsAppTargetType.PAYMENT.name,
            paymentId = paymentId,
            subscriptionId = subscriptionId,
            phone = phone,
            clientName = clientName,
            status = WhatsAppTemplateDeliveryService.STATUS_SENT,
            reason = "Mensaje enviado correctamente."
        )

    private fun skippedPaymentResult(
        paymentId: Int,
        subscriptionId: Int?,
        phone: String?,
        clientName: String?,
        reason: String
    ) =
        WhatsAppMessageResultDto(
            targetId = paymentId,
            targetType = WhatsAppTargetType.PAYMENT.name,
            paymentId = paymentId,
            subscriptionId = subscriptionId,
            phone = phone,
            clientName = clientName,
            status = WhatsAppTemplateDeliveryService.STATUS_SKIPPED,
            reason = reason
        )

    private fun failedPaymentResult(
        paymentId: Int,
        subscriptionId: Int?,
        phone: String?,
        clientName: String?,
        reason: String
    ) =
        WhatsAppMessageResultDto(
            targetId = paymentId,
            targetType = WhatsAppTargetType.PAYMENT.name,
            paymentId = paymentId,
            subscriptionId = subscriptionId,
            phone = phone,
            clientName = clientName,
            status = WhatsAppTemplateDeliveryService.STATUS_FAILED,
            reason = reason
        )

    private fun sentSubscriptionResult(subscriptionId: Int, phone: String, clientName: String?) =
        WhatsAppMessageResultDto(
            targetId = subscriptionId,
            targetType = WhatsAppTargetType.SUBSCRIPTION.name,
            paymentId = null,
            subscriptionId = subscriptionId,
            phone = phone,
            clientName = clientName,
            status = WhatsAppTemplateDeliveryService.STATUS_SENT,
            reason = "Mensaje enviado correctamente."
        )

    private fun skippedSubscriptionResult(
        subscriptionId: Int,
        phone: String?,
        clientName: String?,
        reason: String
    ) =
        WhatsAppMessageResultDto(
            targetId = subscriptionId,
            targetType = WhatsAppTargetType.SUBSCRIPTION.name,
            paymentId = null,
            subscriptionId = subscriptionId,
            phone = phone,
            clientName = clientName,
            status = WhatsAppTemplateDeliveryService.STATUS_SKIPPED,
            reason = reason
        )

    private fun failedSubscriptionResult(
        subscriptionId: Int,
        phone: String?,
        clientName: String?,
        reason: String
    ) =
        WhatsAppMessageResultDto(
            targetId = subscriptionId,
            targetType = WhatsAppTargetType.SUBSCRIPTION.name,
            paymentId = null,
            subscriptionId = subscriptionId,
            phone = phone,
            clientName = clientName,
            status = WhatsAppTemplateDeliveryService.STATUS_FAILED,
            reason = reason
        )

    companion object {
        private const val DEFAULT_CANDIDATE_LIMIT = 200
        private const val DEFAULT_REMINDER_CANDIDATE_LIMIT = 2000

        private val DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    }
}
