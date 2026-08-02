package com.dscorp.wispadmin.wispadmin.service.whatsapp

import com.dscorp.wispadmin.wispadmin.config.CrmCsatProperties
import com.dscorp.wispadmin.wispadmin.data.model.AssistanceTicket
import com.dscorp.wispadmin.wispadmin.data.model.AssistanceTicketStatus
import com.dscorp.wispadmin.wispadmin.data.model.CsatDissatisfactionReason
import com.dscorp.wispadmin.wispadmin.data.model.CsatFollowUp
import com.dscorp.wispadmin.wispadmin.data.model.CsatFollowUpStatus
import com.dscorp.wispadmin.wispadmin.data.model.CsatSendChannel
import com.dscorp.wispadmin.wispadmin.data.model.CsatSurvey
import com.dscorp.wispadmin.wispadmin.data.model.CsatSurveyStatus
import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppMessageLog
import com.dscorp.wispadmin.wispadmin.dto.CsatEvolutionPointDto
import com.dscorp.wispadmin.wispadmin.dto.CsatFollowUpDto
import com.dscorp.wispadmin.wispadmin.dto.CsatReasonCountDto
import com.dscorp.wispadmin.wispadmin.dto.CsatSampleBucketDto
import com.dscorp.wispadmin.wispadmin.dto.CsatSummaryDto
import com.dscorp.wispadmin.wispadmin.dto.CsatSurveyDto
import com.dscorp.wispadmin.wispadmin.repository.AssistanceTicketRepository
import com.dscorp.wispadmin.wispadmin.repository.CsatFollowUpRepository
import com.dscorp.wispadmin.wispadmin.repository.CsatSurveyRepository
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppMessageLogRepository
import com.dscorp.wispadmin.wispadmin.service.WhatsAppService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.round

@Service
class CsatSurveyService(
    private val surveyRepository: CsatSurveyRepository,
    private val followUpRepository: CsatFollowUpRepository,
    private val ticketRepository: AssistanceTicketRepository,
    private val messageLogRepository: WhatsAppMessageLogRepository,
    private val whatsAppService: WhatsAppService,
    private val serviceWindowService: WhatsAppServiceWindowService,
    private val eventPublisher: CrmEventPublisher,
    private val properties: CrmCsatProperties
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun scheduleOnTicketClose(ticket: AssistanceTicket): CsatSurvey {
        if (!properties.enabled) {
            return surveyRepository.findByTicketId(ticket.id)
                ?: CsatSurvey(ticketId = ticket.id, phone = ticket.phone, status = CsatSurveyStatus.FAILED)
        }
        if (ticket.status !in CLOSE_STATUSES) {
            return surveyRepository.findByTicketId(ticket.id)
                ?: error("Ticket ${ticket.id} no esta cerrado/resuelto")
        }
        surveyRepository.findByTicketId(ticket.id)?.let { return it }

        val now = LocalDateTime.now()
        val survey = CsatSurvey(
            ticketId = ticket.id,
            phone = ticket.phone.trim(),
            status = CsatSurveyStatus.SCHEDULED,
            technicianId = ticket.responsible?.id?.takeIf { it > 0 },
            placeName = ticket.placeName?.trim()?.takeIf { it.isNotEmpty() },
            ticketCategory = ticket.category,
            sendIdempotencyKey = sendKey(ticket.id, 0),
            retries = 0,
            maxRetries = properties.maxRetries,
            nextAttemptAt = now.plusMinutes(properties.delayMinutes),
            scheduledAt = now,
            expiresAt = now.plusHours(properties.expireHours),
            createdAt = now,
            updatedAt = now
        )
        return try {
            surveyRepository.save(survey)
        } catch (e: Exception) {
            surveyRepository.findByTicketId(ticket.id) ?: throw e
        }
    }

    @Transactional
    fun processDueSurveys() {
        if (!properties.enabled) return
        val now = LocalDateTime.now()
        expireDue(now)
        val due = surveyRepository.findDueForSend(
            statuses = listOf(CsatSurveyStatus.SCHEDULED, CsatSurveyStatus.FAILED),
            now = now
        )
        due.forEach { sendSurvey(it, now) }
    }

    @Transactional
    fun tryHandleInbound(
        phone: String,
        messageType: String,
        buttonReplyId: String?,
        messageText: String?,
        metaMessageId: String?
    ): Boolean {
        if (!properties.enabled) return false
        val buttonId = buttonReplyId.orEmpty()
        scorePattern.matchEntire(buttonId)?.let { match ->
            val surveyId = match.groupValues[1].toLong()
            val score = match.groupValues[2].toInt()
            captureScoreReply(surveyId, score, metaMessageId.orEmpty(), phone)
            return true
        }
        reasonPattern.matchEntire(buttonId)?.let { match ->
            val surveyId = match.groupValues[1].toLong()
            val reason = runCatching { CsatDissatisfactionReason.valueOf(match.groupValues[2]) }.getOrNull()
                ?: return true
            captureReason(surveyId, reason, metaMessageId)
            return true
        }
        if (messageType == "text" && !messageText.isNullOrBlank()) {
            return captureOptionalComment(phone, messageText.trim(), metaMessageId)
        }
        return false
    }

    @Transactional
    fun captureScoreReply(
        surveyId: Long,
        score: Int,
        metaMessageId: String,
        phone: String
    ): CsatSurvey? {
        if (score !in 1..5) return null
        if (metaMessageId.isNotBlank()) {
            surveyRepository.findByCaptureIdempotencyKey(metaMessageId)?.let { return it }
        }
        val survey = surveyRepository.findById(surveyId).orElse(null) ?: return null
        if (survey.phone != phone.trim()) return null
        if (survey.status == CsatSurveyStatus.ANSWERED) return survey
        if (survey.status == CsatSurveyStatus.EXPIRED || survey.status == CsatSurveyStatus.FAILED) return survey

        val now = LocalDateTime.now()
        survey.status = CsatSurveyStatus.ANSWERED
        survey.score = score
        survey.respondedAt = now
        survey.updatedAt = now
        survey.nextAttemptAt = null
        if (metaMessageId.isNotBlank()) {
            survey.captureIdempotencyKey = metaMessageId
        }
        val saved = try {
            surveyRepository.save(survey)
        } catch (e: Exception) {
            if (metaMessageId.isNotBlank()) {
                surveyRepository.findByCaptureIdempotencyKey(metaMessageId)?.let { return it }
            }
            throw e
        }

        eventPublisher.publish(
            EVENT_ANSWERED,
            mapOf(
                "surveyId" to saved.id,
                "ticketId" to saved.ticketId,
                "phone" to saved.phone,
                "score" to score
            )
        )

        if (score <= properties.lowScoreThreshold) {
            ensureFollowUp(saved)
            sendReasonPrompt(saved)
            eventPublisher.publish(
                EVENT_LOW_SCORE,
                mapOf(
                    "surveyId" to saved.id,
                    "ticketId" to saved.ticketId,
                    "phone" to saved.phone,
                    "score" to score
                )
            )
        } else {
            sendThanks(saved.phone, score)
        }
        return saved
    }

    fun buildSummary(from: LocalDateTime, to: LocalDateTime): CsatSummaryDto {
        val surveys = surveyRepository.findByScheduledAtBetween(from, to)
        val scheduled = surveys.count { it.status == CsatSurveyStatus.SCHEDULED }.toLong()
        val sent = surveys.count { it.status == CsatSurveyStatus.SENT }.toLong()
        val answered = surveys.count { it.status == CsatSurveyStatus.ANSWERED }.toLong()
        val expired = surveys.count { it.status == CsatSurveyStatus.EXPIRED }.toLong()
        val failed = surveys.count { it.status == CsatSurveyStatus.FAILED }.toLong()
        val deliveredLike = sent + answered + expired + failed
        val responseRate = if (deliveredLike == 0L) 0.0 else answered.toDouble() / deliveredLike.toDouble()
        val scores = surveys.mapNotNull { it.score }
        val averageScore = scores.takeIf { it.isNotEmpty() }?.average()?.let { round2(it) }

        return CsatSummaryDto(
            from = from,
            to = to,
            scheduled = scheduled,
            sent = sent,
            answered = answered,
            expired = expired,
            failed = failed,
            responseRate = round2(responseRate),
            averageScore = averageScore,
            byTechnician = bucketBy(surveys) {
                val key = it.technicianId?.toString() ?: "none"
                key to (it.technicianId?.toString() ?: "Sin técnico")
            },
            byPlace = bucketBy(surveys) {
                val key = it.placeName ?: "Sin zona"
                key to key
            },
            byCategory = bucketBy(surveys) {
                val key = it.ticketCategory ?: "Sin tipo"
                key to key
            },
            evolution = evolution(surveys),
            reasons = surveys
                .mapNotNull { it.dissatisfactionReason }
                .groupingBy { it.name }
                .eachCount()
                .map { CsatReasonCountDto(it.key, it.value.toLong()) }
                .sortedByDescending { it.count }
        )
    }

    fun listSurveys(from: LocalDateTime, to: LocalDateTime, status: String?): List<CsatSurveyDto> {
        val surveys = surveyRepository.findByScheduledAtBetween(from, to)
        val filtered = if (status.isNullOrBlank()) surveys
        else surveys.filter { it.status.name.equals(status, ignoreCase = true) }
        return filtered.map { it.toDto() }
    }

    fun listFollowUps(status: String?): List<CsatFollowUpDto> {
        val statuses = if (status.isNullOrBlank()) {
            CsatFollowUpStatus.values().toList()
        } else {
            listOf(CsatFollowUpStatus.valueOf(status.trim().uppercase()))
        }
        return followUpRepository.findByStatusInOrderByCreatedAtDesc(statuses).map { followUp ->
            val survey = surveyRepository.findById(followUp.surveyId).orElse(null)
            followUp.toDto(survey)
        }
    }

    @Transactional
    fun updateFollowUp(
        id: Long,
        status: String?,
        assignedTo: Int?,
        actions: String?,
        reason: String?
    ): CsatFollowUpDto {
        val followUp = followUpRepository.findById(id).orElseThrow { NoSuchElementException("Follow-up $id no existe") }
        val now = LocalDateTime.now()
        status?.let {
            followUp.status = CsatFollowUpStatus.valueOf(it.trim().uppercase())
            followUp.closedAt = if (followUp.status == CsatFollowUpStatus.CLOSED) now else null
        }
        if (assignedTo != null) followUp.assignedTo = assignedTo
        if (actions != null) followUp.actions = actions.take(2000)
        reason?.let {
            followUp.reason = CsatDissatisfactionReason.valueOf(it.trim().uppercase())
            surveyRepository.findById(followUp.surveyId).ifPresent { survey ->
                survey.dissatisfactionReason = followUp.reason
                survey.updatedAt = now
                surveyRepository.save(survey)
            }
        }
        followUp.updatedAt = now
        val saved = followUpRepository.save(followUp)
        val survey = surveyRepository.findById(saved.surveyId).orElse(null)
        return saved.toDto(survey)
    }

    @Transactional
    fun reopenTicketFromFollowUp(followUpId: Long): AssistanceTicket {
        val followUp = followUpRepository.findById(followUpId)
            .orElseThrow { NoSuchElementException("Follow-up $followUpId no existe") }
        val survey = surveyRepository.findById(followUp.surveyId)
            .orElseThrow { NoSuchElementException("Survey ${followUp.surveyId} no existe") }
        val ticket = ticketRepository.findById(survey.ticketId)
            .orElseThrow { NoSuchElementException("Ticket ${survey.ticketId} no existe") }
        ticket.status = AssistanceTicketStatus.REOPEN
        ticket.closedAt = null
        ticket.resolvedAt = null
        val saved = ticketRepository.save(ticket)
        followUp.status = CsatFollowUpStatus.IN_PROGRESS
        followUp.updatedAt = LocalDateTime.now()
        followUpRepository.save(followUp)
        return saved
    }

    private fun sendSurvey(survey: CsatSurvey, now: LocalDateTime) {
        if (survey.status == CsatSurveyStatus.ANSWERED || survey.status == CsatSurveyStatus.EXPIRED) return
        if (survey.expiresAt.isBefore(now) || survey.expiresAt.isEqual(now)) {
            survey.status = CsatSurveyStatus.EXPIRED
            survey.nextAttemptAt = null
            survey.updatedAt = now
            surveyRepository.save(survey)
            return
        }
        if (survey.retries >= survey.maxRetries && survey.status == CsatSurveyStatus.FAILED) {
            survey.nextAttemptAt = null
            survey.updatedAt = now
            surveyRepository.save(survey)
            return
        }

        val windowOpen = serviceWindowService.getServiceWindow(survey.phone).open
        try {
            val result = if (windowOpen) {
                val body = """
                    |Gracias por contactarnos. Califique la atencion del ticket #${survey.ticketId}
                    |(1 = muy mala, 5 = excelente).
                """.trimMargin()
                whatsAppService.sendInteractiveListMessage(
                    phoneNumber = survey.phone,
                    bodyText = body,
                    buttonText = "Calificar",
                    sectionTitle = "Satisfaccion",
                    rows = (1..5).map { score ->
                        WhatsAppService.InteractiveListOption(
                            id = scoreButtonId(survey.id!!, score),
                            title = "$score",
                            description = scoreLabel(score)
                        )
                    }
                )
            } else {
                if (properties.templateName.isBlank()) {
                    throw IllegalStateException("Plantilla CSAT no configurada (crm.csat.template-name)")
                }
                whatsAppService.sendTemplateMessageWithMetaResponse(
                    phoneNumber = survey.phone,
                    templateName = properties.templateName,
                    languageCode = properties.templateLanguage,
                    parameters = listOf(
                        NamedTemplateParameter("ticket_id", survey.ticketId.toString())
                    )
                )
            }
            if (!result.success) {
                throw IllegalStateException("Envio CSAT sin success")
            }
            survey.status = CsatSurveyStatus.SENT
            survey.sendChannel = if (windowOpen) CsatSendChannel.INTERACTIVE else CsatSendChannel.TEMPLATE
            survey.metaMessageId = result.metaMessageId
            survey.sentAt = now
            survey.nextAttemptAt = null
            survey.lastError = null
            survey.sendIdempotencyKey = sendKey(survey.ticketId, survey.retries)
            survey.updatedAt = now
            surveyRepository.save(survey)
            persistOutboundLog(
                phone = survey.phone,
                message = "CSAT survey ticket #${survey.ticketId}",
                metaMessageId = result.metaMessageId,
                messageType = MESSAGE_TYPE_SURVEY
            )
            eventPublisher.publish(
                EVENT_SENT,
                mapOf(
                    "surveyId" to survey.id,
                    "ticketId" to survey.ticketId,
                    "phone" to survey.phone,
                    "channel" to survey.sendChannel?.name
                )
            )
        } catch (e: Exception) {
            survey.retries += 1
            survey.lastError = e.message?.take(500)
            survey.updatedAt = now
            survey.sendIdempotencyKey = sendKey(survey.ticketId, survey.retries)
            if (survey.retries >= survey.maxRetries) {
                survey.status = CsatSurveyStatus.FAILED
                survey.nextAttemptAt = null
            } else {
                survey.status = CsatSurveyStatus.SCHEDULED
                survey.nextAttemptAt = now.plusMinutes(properties.retryMinutes)
            }
            surveyRepository.save(survey)
            log.warn("CSAT send failed ticketId={} retries={}: {}", survey.ticketId, survey.retries, e.message)
        }
    }

    private fun expireDue(now: LocalDateTime) {
        val candidates = surveyRepository.findExpiredCandidates(
            statuses = listOf(CsatSurveyStatus.SCHEDULED, CsatSurveyStatus.SENT, CsatSurveyStatus.FAILED),
            now = now
        )
        candidates.forEach { survey ->
            survey.status = CsatSurveyStatus.EXPIRED
            survey.nextAttemptAt = null
            survey.updatedAt = now
            surveyRepository.save(survey)
        }
    }

    private fun ensureFollowUp(survey: CsatSurvey): CsatFollowUp {
        followUpRepository.findBySurveyId(survey.id!!)?.let { return it }
        val now = LocalDateTime.now()
        return try {
            followUpRepository.save(
                CsatFollowUp(
                    surveyId = survey.id!!,
                    reason = survey.dissatisfactionReason,
                    status = CsatFollowUpStatus.OPEN,
                    createdAt = now,
                    updatedAt = now
                )
            ).also {
                eventPublisher.publish(
                    EVENT_FOLLOW_UP,
                    mapOf(
                        "followUpId" to it.id,
                        "surveyId" to survey.id,
                        "ticketId" to survey.ticketId,
                        "score" to survey.score
                    )
                )
            }
        } catch (e: Exception) {
            followUpRepository.findBySurveyId(survey.id!!) ?: throw e
        }
    }

    private fun captureReason(surveyId: Long, reason: CsatDissatisfactionReason, metaMessageId: String?) {
        val survey = surveyRepository.findById(surveyId).orElse(null) ?: return
        val now = LocalDateTime.now()
        survey.dissatisfactionReason = reason
        survey.updatedAt = now
        surveyRepository.save(survey)
        val followUp = ensureFollowUp(survey)
        followUp.reason = reason
        followUp.updatedAt = now
        followUpRepository.save(followUp)
        if (!metaMessageId.isNullOrBlank()) {
            persistOutboundLog(
                phone = survey.phone,
                message = "CSAT reason $reason",
                metaMessageId = null,
                messageType = MESSAGE_TYPE_REASON
            )
        }
        sendThanks(survey.phone, survey.score ?: 1)
    }

    private fun captureOptionalComment(phone: String, text: String, metaMessageId: String?): Boolean {
        val now = LocalDateTime.now()
        val candidates = surveyRepository.findByPhoneAndStatusIn(
            phone.trim(),
            listOf(CsatSurveyStatus.ANSWERED)
        )
        val survey = candidates
            .filter { it.comment.isNullOrBlank() }
            .filter { it.respondedAt != null }
            .filter {
                ChronoUnit.MINUTES.between(it.respondedAt, now) <= properties.commentWindowMinutes
            }
            .maxByOrNull { it.respondedAt!! }
            ?: return false

        if (!metaMessageId.isNullOrBlank()) {
            surveyRepository.findByCaptureIdempotencyKey("comment:$metaMessageId")?.let { return true }
        }
        survey.comment = text.take(1000)
        survey.updatedAt = now
        surveyRepository.save(survey)
        return true
    }

    private fun sendReasonPrompt(survey: CsatSurvey) {
        try {
            whatsAppService.sendInteractiveListMessage(
                phoneNumber = survey.phone,
                bodyText = "Lamentamos su experiencia con el ticket #${survey.ticketId}. Indique el motivo principal:",
                buttonText = "Motivo",
                sectionTitle = "Inconformidad",
                rows = CsatDissatisfactionReason.values().map { reason ->
                    WhatsAppService.InteractiveListOption(
                        id = reasonButtonId(survey.id!!, reason),
                        title = reasonTitle(reason),
                        description = null
                    )
                }
            )
        } catch (e: Exception) {
            log.warn("CSAT reason prompt failed surveyId={}: {}", survey.id, e.message)
        }
    }

    private fun sendThanks(phone: String, score: Int) {
        try {
            whatsAppService.sendTextMessage(
                phoneNumber = phone,
                message = "Gracias por su calificacion ($score/5). Su opinion nos ayuda a mejorar."
            )
        } catch (e: Exception) {
            log.warn("CSAT thanks failed phone={}: {}", phone, e.message)
        }
    }

    private fun persistOutboundLog(
        phone: String,
        message: String,
        metaMessageId: String?,
        messageType: String
    ) {
        try {
            messageLogRepository.save(
                WhatsAppMessageLog(
                    paymentId = null,
                    subscriptionId = null,
                    phone = phone,
                    messageType = messageType,
                    status = "SENT",
                    message = message,
                    metaMessageId = metaMessageId,
                    sentAt = LocalDateTime.now(),
                    operatorUsername = "system"
                )
            )
        } catch (e: Exception) {
            log.warn("CSAT message log failed: {}", e.message)
        }
    }

    private fun bucketBy(
        surveys: List<CsatSurvey>,
        keyLabel: (CsatSurvey) -> Pair<String, String>
    ): List<CsatSampleBucketDto> {
        return surveys
            .groupBy { keyLabel(it) }
            .map { (pair, items) ->
                val answeredScores = items.mapNotNull { it.score }
                CsatSampleBucketDto(
                    key = pair.first,
                    label = pair.second,
                    averageScore = answeredScores.takeIf { it.isNotEmpty() }?.average()?.let { round2(it) },
                    answered = answeredScores.size.toLong(),
                    sampleSize = answeredScores.size.toLong()
                )
            }
            .sortedByDescending { it.sampleSize }
    }

    private fun evolution(surveys: List<CsatSurvey>): List<CsatEvolutionPointDto> {
        val byDay = surveys.groupBy { it.scheduledAt.toLocalDate() }
        val min = byDay.keys.minOrNull() ?: return emptyList()
        val max = byDay.keys.maxOrNull() ?: return emptyList()
        var day: LocalDate = min
        val points = mutableListOf<CsatEvolutionPointDto>()
        while (!day.isAfter(max)) {
            val items = byDay[day].orEmpty()
            val answeredScores = items.mapNotNull { it.score }
            val sentCount = items.count {
                it.status == CsatSurveyStatus.SENT ||
                    it.status == CsatSurveyStatus.ANSWERED ||
                    it.status == CsatSurveyStatus.EXPIRED ||
                    it.status == CsatSurveyStatus.FAILED
            }.toLong()
            points += CsatEvolutionPointDto(
                date = day.toString(),
                averageScore = answeredScores.takeIf { it.isNotEmpty() }?.average()?.let { round2(it) },
                answered = answeredScores.size.toLong(),
                sent = sentCount
            )
            day = day.plusDays(1)
        }
        return points
    }

    private fun CsatSurvey.toDto() = CsatSurveyDto(
        id = requireNotNull(id),
        ticketId = ticketId,
        phone = phone,
        status = status.name,
        score = score,
        comment = comment,
        dissatisfactionReason = dissatisfactionReason?.name,
        technicianId = technicianId,
        placeName = placeName,
        ticketCategory = ticketCategory,
        sendChannel = sendChannel?.name,
        retries = retries,
        scheduledAt = scheduledAt,
        sentAt = sentAt,
        expiresAt = expiresAt,
        respondedAt = respondedAt,
        lastError = lastError
    )

    private fun CsatFollowUp.toDto(survey: CsatSurvey?) = CsatFollowUpDto(
        id = requireNotNull(id),
        surveyId = surveyId,
        ticketId = survey?.ticketId,
        phone = survey?.phone,
        score = survey?.score,
        reason = reason?.name,
        status = status.name,
        assignedTo = assignedTo,
        actions = actions,
        createdAt = createdAt,
        updatedAt = updatedAt,
        closedAt = closedAt
    )

    companion object {
        const val EVENT_SENT = "CSAT_SURVEY_SENT"
        const val EVENT_ANSWERED = "CSAT_SURVEY_ANSWERED"
        const val EVENT_LOW_SCORE = "CSAT_LOW_SCORE_ALERT"
        const val EVENT_FOLLOW_UP = "CSAT_FOLLOW_UP_CREATED"
        const val MESSAGE_TYPE_SURVEY = "CSAT_SURVEY"
        const val MESSAGE_TYPE_REASON = "CSAT_REASON"
        private val CLOSE_STATUSES = setOf(AssistanceTicketStatus.RESOLVED, AssistanceTicketStatus.CLOSED)
        private val scorePattern = Regex("^csat_s_(\\d+)_([1-5])$")
        private val reasonPattern = Regex("^csat_r_(\\d+)_([A-Z_]+)$")

        fun scoreButtonId(surveyId: Long, score: Int) = "csat_s_${surveyId}_$score"
        fun reasonButtonId(surveyId: Long, reason: CsatDissatisfactionReason) = "csat_r_${surveyId}_${reason.name}"
        fun sendKey(ticketId: Int, attempt: Int) = "CSAT-SEND-$ticketId-$attempt"

        private fun scoreLabel(score: Int) = when (score) {
            1 -> "Muy mala"
            2 -> "Mala"
            3 -> "Regular"
            4 -> "Buena"
            else -> "Excelente"
        }

        private fun reasonTitle(reason: CsatDissatisfactionReason) = when (reason) {
            CsatDissatisfactionReason.PUNTUALIDAD -> "Puntualidad"
            CsatDissatisfactionReason.TRATO -> "Trato"
            CsatDissatisfactionReason.NO_RESUELTO -> "No resuelto"
            CsatDissatisfactionReason.CALIDAD -> "Calidad"
            CsatDissatisfactionReason.INCUMPLIMIENTO_VISITA -> "Incumpl. visita"
            CsatDissatisfactionReason.OTRO -> "Otro"
        }

        private fun round2(value: Double): Double = round(value * 100.0) / 100.0
    }
}
