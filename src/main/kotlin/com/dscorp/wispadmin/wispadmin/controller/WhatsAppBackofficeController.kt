package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.dto.CrmRealtimeEventDto
import com.dscorp.wispadmin.wispadmin.dto.WhatsAppConversationContextDto
import com.dscorp.wispadmin.wispadmin.dto.WhatsAppConversationSummaryDto
import com.dscorp.wispadmin.wispadmin.dto.WhatsAppMarkAllReadResultDto
import com.dscorp.wispadmin.wispadmin.dto.WhatsAppMarkReadResultDto
import com.dscorp.wispadmin.wispadmin.dto.WhatsAppTemplateSyncResultDto
import com.dscorp.wispadmin.wispadmin.dto.WhatsAppTestSendResponseDto
import com.dscorp.wispadmin.wispadmin.dto.WhatsAppThreadMessageDto
import com.dscorp.wispadmin.wispadmin.dto.toDto
import com.dscorp.wispadmin.wispadmin.dto.toFrontendDto
import com.dscorp.wispadmin.wispadmin.dto.toSeriesPointDto
import com.dscorp.wispadmin.wispadmin.dto.toSummaryDto
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppInboundMessageRepository
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppMessageLogRepository
import com.dscorp.wispadmin.wispadmin.requestbody.WhatsAppConversationReplyBody
import com.dscorp.wispadmin.wispadmin.requestbody.WhatsAppConversationTemplateBody
import com.dscorp.wispadmin.wispadmin.requestbody.WhatsAppSelectedRemindersRequest
import com.dscorp.wispadmin.wispadmin.requestbody.WhatsAppSendMessagesRequest
import com.dscorp.wispadmin.wispadmin.requestbody.WhatsAppTemplateTestMessageRequest
import com.dscorp.wispadmin.wispadmin.security.PlatformAuthFilter
import com.dscorp.wispadmin.wispadmin.service.WhatsAppBackofficeMessageService
import com.dscorp.wispadmin.wispadmin.service.WhatsAppWelcomeRegistrationService
import com.dscorp.wispadmin.wispadmin.config.WhatsAppProperties
import com.dscorp.wispadmin.wispadmin.service.whatsapp.CrmConversationForbiddenException
import com.dscorp.wispadmin.wispadmin.service.whatsapp.CrmEventPublisher
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppAccountEventService
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppAnalyticsService
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppAuditService
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppBackofficeQueryService
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppConversationFilter
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppConversationQueryService
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppConversationService
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppCsvExportService
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppHandoffService
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppInboundFilter
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppLogsFilter
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppMediaDownloadService
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppMetaAnalyticsClient
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppMetaAnalyticsParser
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppServiceWindowService
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppTemplateCatalog
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppTemplateCode
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppTemplateSyncService
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppSyncedTemplateRepository
import org.springframework.core.io.FileSystemResource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import javax.servlet.http.HttpServletRequest

@RestController
@RequestMapping("/whatsapp")
class WhatsAppBackofficeController(
    private val messageService: WhatsAppBackofficeMessageService,
    private val queryService: WhatsAppBackofficeQueryService,
    private val messageLogRepository: WhatsAppMessageLogRepository,
    private val inboundMessageRepository: WhatsAppInboundMessageRepository,
    private val templateMessageSender: WhatsAppTemplateMessageSender,
    private val welcomeRegistrationService: WhatsAppWelcomeRegistrationService,
    private val whatsAppProperties: WhatsAppProperties,
    private val analyticsService: WhatsAppAnalyticsService,
    private val metaAnalyticsClient: WhatsAppMetaAnalyticsClient,
    private val templateSyncService: WhatsAppTemplateSyncService,
    private val syncedTemplateRepository: WhatsAppSyncedTemplateRepository,
    private val accountEventService: WhatsAppAccountEventService,
    private val serviceWindowService: WhatsAppServiceWindowService,
    private val conversationService: WhatsAppConversationService,
    private val conversationQueryService: WhatsAppConversationQueryService,
    private val mediaDownloadService: WhatsAppMediaDownloadService,
    private val handoffService: WhatsAppHandoffService,
    private val csvExportService: WhatsAppCsvExportService,
    private val auditService: WhatsAppAuditService,
    private val crmEventPublisher: CrmEventPublisher
) {

    @GetMapping("/events")
    fun listEvents(
        @RequestParam(defaultValue = "0") sinceEventId: Long
    ): ResponseEntity<List<CrmRealtimeEventDto>> {
        return ResponseEntity.ok(crmEventPublisher.findSince(sinceEventId))
    }

    @GetMapping("/templates")
    fun listTemplates(): ResponseEntity<Any> {
        return ResponseEntity.ok(messageService.listTemplates())
    }

    @GetMapping("/message-candidates")
    fun listMessageCandidates(
        @RequestParam templateCode: String
    ): ResponseEntity<Any> {
        return ResponseEntity.ok(messageService.listCandidates(templateCode))
    }

    @PostMapping("/messages/selected")
    fun sendSelectedMessages(
        @RequestBody request: WhatsAppSendMessagesRequest,
        httpRequest: HttpServletRequest
    ): ResponseEntity<Any> {
        return ResponseEntity.ok(
            messageService.sendSelected(
                templateCode = request.templateCode,
                targetIds = request.targetIds,
                operatorUsername = resolveOperator(httpRequest)
            )
        )
    }

    @GetMapping("/reminder-candidates")
    fun listReminderCandidates(
        @RequestParam(defaultValue = "200") limit: Int
    ): ResponseEntity<Any> {
        return ResponseEntity.ok(messageService.listReminderCandidates(limit))
    }

    @PostMapping("/reminders/selected")
    fun sendSelectedReminders(
        @RequestBody request: WhatsAppSelectedRemindersRequest,
        httpRequest: HttpServletRequest
    ): ResponseEntity<Any> {
        return ResponseEntity.ok(
            messageService.sendSelectedReminders(
                paymentIds = request.paymentIds,
                operatorUsername = resolveOperator(httpRequest)
            )
        )
    }

    @PostMapping("/messages/template")
    fun sendManualTemplateMessage(
        @RequestBody request: WhatsAppTemplateTestMessageRequest
    ): ResponseEntity<WhatsAppTestSendResponseDto> {
        return templateMessageSender.sendPaymentReminderTemplate(request)
    }

    @GetMapping("/registration-status")
    fun getRegistrationStatus(): ResponseEntity<Any> {
        val phoneNumberId = whatsAppProperties.phoneNumberId
        return ResponseEntity.ok(
            mapOf(
                "welcomeOnRegistrationEnabled" to whatsAppProperties.welcomeOnRegistration.enabled,
                "whatsappConfigured" to whatsAppProperties.isConfigured(),
                "apiVersion" to whatsAppProperties.apiVersion,
                "phoneNumberIdSuffix" to phoneNumberId.takeLast(4).takeIf { phoneNumberId.length >= 4 },
                "hasAccessToken" to whatsAppProperties.accessToken.isNotBlank(),
                "welcomeTemplate" to WhatsAppTemplateCatalog.get(WhatsAppTemplateCode.WELCOME_CUSTOMER).metaName
            )
        )
    }

    @PostMapping("/messages/welcome/{subscriptionId}")
    fun sendWelcomeForSubscription(
        @PathVariable subscriptionId: Int
    ): ResponseEntity<Any> {
        val result = welcomeRegistrationService.sendWelcomeAndGetResult(subscriptionId)
        val recentLog = queryService.listLogs(
            WhatsAppLogsFilter(
                templateCode = WhatsAppTemplateCode.WELCOME_CUSTOMER.name,
                limit = 1
            )
        ).firstOrNull { it.subscriptionId == subscriptionId }

        return ResponseEntity.ok(
            mapOf(
                "subscriptionId" to result.subscriptionId,
                "outcome" to result.outcome,
                "detail" to result.detail,
                "latestLog" to recentLog
            )
        )
    }

    @GetMapping("/logs")
    fun getRecentLogs(
        @RequestParam(required = false) templateCode: String?,
        @RequestParam(required = false) template: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) deliveryStatus: String?,
        @RequestParam(required = false) dateFrom: String?,
        @RequestParam(required = false) dateTo: String?,
        @RequestParam(required = false) from: String?,
        @RequestParam(required = false) to: String?,
        @RequestParam(required = false) phone: String?,
        @RequestParam(required = false) limit: Int?
    ): ResponseEntity<Any> {
        val range = resolveOptionalRange(dateFrom ?: from, dateTo ?: to)
        return ResponseEntity.ok(
            queryService.listLogs(
                WhatsAppLogsFilter(
                    templateCode = templateCode ?: template,
                    status = status,
                    deliveryStatus = deliveryStatus,
                    dateFrom = range?.first,
                    dateTo = range?.second,
                    phone = phone,
                    limit = limit ?: 50
                )
            )
        )
    }

    @GetMapping("/logs/export", produces = ["text/csv"])
    fun exportLogs(
        @RequestParam(required = false) templateCode: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) deliveryStatus: String?,
        @RequestParam(required = false) dateFrom: String?,
        @RequestParam(required = false) dateTo: String?,
        @RequestParam(required = false) phone: String?,
        @RequestParam(required = false) limit: Int?
    ): ResponseEntity<String> {
        val range = resolveOptionalRange(dateFrom, dateTo)
        val logs = queryService.listLogs(
            WhatsAppLogsFilter(
                templateCode = templateCode,
                status = status,
                deliveryStatus = deliveryStatus,
                dateFrom = range?.first,
                dateTo = range?.second,
                phone = phone,
                limit = limit ?: 500
            )
        )
        return csvResponse("whatsapp-logs.csv", csvExportService.exportLogs(logs))
    }

    @GetMapping("/logs/payment/{paymentId}")
    fun getLogsByPaymentId(@PathVariable paymentId: Int): ResponseEntity<Any> {
        return ResponseEntity.ok(
            messageLogRepository.findByPaymentIdOrderByCreatedAtDesc(paymentId).map { it.toDto() }
        )
    }

    @GetMapping("/inbound-messages")
    fun getRecentInboundMessages(
        @RequestParam(required = false) phone: String?,
        @RequestParam(required = false) search: String?,
        @RequestParam(required = false) dateFrom: String?,
        @RequestParam(required = false) dateTo: String?,
        @RequestParam(required = false) from: String?,
        @RequestParam(required = false) to: String?,
        @RequestParam(required = false) limit: Int?
    ): ResponseEntity<Any> {
        val range = resolveOptionalRange(dateFrom ?: from, dateTo ?: to)
        return ResponseEntity.ok(
            queryService.listInbound(
                WhatsAppInboundFilter(
                    phone = phone,
                    search = search,
                    dateFrom = range?.first,
                    dateTo = range?.second,
                    limit = limit ?: 50
                )
            )
        )
    }

    @GetMapping("/inbound-messages/export", produces = ["text/csv"])
    fun exportInboundMessages(
        @RequestParam(required = false) phone: String?,
        @RequestParam(required = false) search: String?,
        @RequestParam(required = false) dateFrom: String?,
        @RequestParam(required = false) dateTo: String?,
        @RequestParam(required = false) limit: Int?
    ): ResponseEntity<String> {
        val range = resolveOptionalRange(dateFrom, dateTo)
        val messages = queryService.listInbound(
            WhatsAppInboundFilter(
                phone = phone,
                search = search,
                dateFrom = range?.first,
                dateTo = range?.second,
                limit = limit ?: 500
            )
        )
        return csvResponse("whatsapp-inbound.csv", csvExportService.exportInbound(messages))
    }

    @GetMapping("/inbound-messages/subscription/{subscriptionId}")
    fun getInboundMessagesBySubscription(@PathVariable subscriptionId: Int): ResponseEntity<Any> {
        return ResponseEntity.ok(
            inboundMessageRepository.findBySubscriptionIdOrderByCreatedAtDesc(subscriptionId).map { it.toDto() }
        )
    }

    @GetMapping("/analytics/overview")
    fun analyticsOverview(
        @RequestParam(required = false) dateFrom: String?,
        @RequestParam(required = false) dateTo: String?,
        @RequestParam(required = false) from: String?,
        @RequestParam(required = false) to: String?,
        @RequestParam(required = false) periodDays: Int?,
        @RequestParam(required = false) templateCode: String?
    ): ResponseEntity<Any> {
        val windowDays = (periodDays ?: 7).coerceAtLeast(1)
        val range = queryService.resolveDateRange(dateFrom ?: from, dateTo ?: to, windowDays)
        val overview = analyticsService.overview(range.first, range.second, windowDays, templateCode)
        return ResponseEntity.ok(overview.toDto())
    }

    @GetMapping("/analytics/overview/series")
    fun analyticsOverviewSeries(
        @RequestParam(required = false) dateFrom: String?,
        @RequestParam(required = false) dateTo: String?,
        @RequestParam(required = false) from: String?,
        @RequestParam(required = false) to: String?,
        @RequestParam(required = false) periodDays: Int?,
        @RequestParam(required = false) templateCode: String?
    ): ResponseEntity<Any> {
        val windowDays = (periodDays ?: 7).coerceAtLeast(1)
        val range = queryService.resolveDateRange(dateFrom ?: from, dateTo ?: to, windowDays)
        return ResponseEntity.ok(
            analyticsService.overviewSeries(range.first, range.second, templateCode).map { it.toSeriesPointDto() }
        )
    }

    @GetMapping("/analytics/campaigns")
    fun analyticsCampaigns(
        @RequestParam(required = false) dateFrom: String?,
        @RequestParam(required = false) dateTo: String?,
        @RequestParam(required = false) from: String?,
        @RequestParam(required = false) to: String?,
        @RequestParam(required = false) periodDays: Int?,
        @RequestParam(required = false) templateCode: String?,
        @RequestParam(required = false) operatorUsername: String?
    ): ResponseEntity<Any> {
        val windowDays = (periodDays ?: 7).coerceAtLeast(1)
        val range = queryService.resolveDateRange(dateFrom ?: from, dateTo ?: to, windowDays)
        return ResponseEntity.ok(
            analyticsService.campaigns(range.first, range.second, windowDays, templateCode, operatorUsername)
                .map { it.toSummaryDto() }
        )
    }

    @GetMapping("/analytics/campaigns/export", produces = ["text/csv"])
    fun exportCampaigns(
        @RequestParam(required = false) dateFrom: String?,
        @RequestParam(required = false) dateTo: String?,
        @RequestParam(required = false) from: String?,
        @RequestParam(required = false) to: String?,
        @RequestParam(required = false) periodDays: Int?,
        @RequestParam(required = false) templateCode: String?,
        @RequestParam(required = false) operatorUsername: String?
    ): ResponseEntity<String> {
        val windowDays = (periodDays ?: 7).coerceAtLeast(1)
        val range = queryService.resolveDateRange(dateFrom ?: from, dateTo ?: to, windowDays)
        val campaigns = analyticsService.campaigns(range.first, range.second, windowDays, templateCode, operatorUsername)
            .map { it.toSummaryDto() }
        return csvResponse("whatsapp-campaigns.csv", csvExportService.exportCampaigns(campaigns))
    }

    @GetMapping("/analytics/campaigns/{campaignId}")
    fun analyticsCampaignDetail(
        @PathVariable campaignId: String,
        @RequestParam(required = false) periodDays: Int?,
        @RequestParam(required = false) templateCode: String?,
        @RequestParam(required = false) operatorUsername: String?
    ): ResponseEntity<Any> {
        val windowDays = (periodDays ?: 7).coerceAtLeast(1)
        val detail = analyticsService.campaignDetail(campaignId, windowDays, templateCode, operatorUsername)
            ?: return ResponseEntity.notFound().build()
        val logs = messageLogRepository.findByCampaignId(campaignId).map { it.toDto() }
        return ResponseEntity.ok(detail.toFrontendDto(logs))
    }

    @GetMapping("/analytics/conversion")
    fun analyticsConversion(
        @RequestParam(required = false) dateFrom: String?,
        @RequestParam(required = false) dateTo: String?,
        @RequestParam(required = false) from: String?,
        @RequestParam(required = false) to: String?,
        @RequestParam(required = false) periodDays: Int?,
        @RequestParam(required = false) windowDays: Int?
    ): ResponseEntity<Any> {
        val days = (periodDays ?: windowDays ?: 7).coerceAtLeast(1)
        val range = queryService.resolveDateRange(dateFrom ?: from, dateTo ?: to, days)
        return ResponseEntity.ok(analyticsService.conversion(range.first, range.second, days).toFrontendDto())
    }

    @GetMapping("/analytics/meta/templates")
    fun metaTemplateAnalytics(
        @RequestParam(required = false) dateFrom: String?,
        @RequestParam(required = false) dateTo: String?,
        @RequestParam(required = false) from: String?,
        @RequestParam(required = false) to: String?,
        @RequestParam(required = false) templateCode: String?,
        @RequestParam(required = false) templateNames: List<String>?
    ): ResponseEntity<Any> {
        val range = resolveInstantRange(dateFrom ?: from, dateTo ?: to)
        val names = templateNames.orEmpty().toMutableList()
        if (!templateCode.isNullOrBlank()) {
            runCatching {
                WhatsAppTemplateCatalog.getByCodeString(templateCode).metaName
            }.getOrNull()?.let { names.add(it) }
        }
        val ids = names.distinct().mapNotNull { templateSyncService.findMetaTemplateIdByName(it) }
        val node = metaAnalyticsClient.fetchTemplateAnalytics(ids, range.first, range.second)
        return ResponseEntity.ok(
            WhatsAppMetaAnalyticsParser.parseTemplateAnalytics(node, syncedTemplateRepository)
        )
    }

    @GetMapping("/analytics/meta/conversations")
    fun metaConversationAnalytics(
        @RequestParam(required = false) dateFrom: String?,
        @RequestParam(required = false) dateTo: String?,
        @RequestParam(required = false) from: String?,
        @RequestParam(required = false) to: String?
    ): ResponseEntity<Any> {
        val range = resolveInstantRange(dateFrom ?: from, dateTo ?: to)
        val node = metaAnalyticsClient.fetchConversationAnalytics(range.first, range.second)
        return ResponseEntity.ok(WhatsAppMetaAnalyticsParser.parseConversationAnalytics(node))
    }

    @GetMapping("/analytics/meta/pricing")
    fun metaPricingAnalytics(
        @RequestParam(required = false) dateFrom: String?,
        @RequestParam(required = false) dateTo: String?,
        @RequestParam(required = false) from: String?,
        @RequestParam(required = false) to: String?
    ): ResponseEntity<Any> {
        val range = resolveInstantRange(dateFrom ?: from, dateTo ?: to)
        val node = metaAnalyticsClient.fetchPricingAnalytics(range.first, range.second)
        return ResponseEntity.ok(WhatsAppMetaAnalyticsParser.parsePricingAnalytics(node))
    }

    @GetMapping("/account/health")
    fun accountHealth(): ResponseEntity<Any> {
        return ResponseEntity.ok(accountEventService.getAccountHealth().toFrontendDto())
    }

    @GetMapping("/templates/sync")
    fun listSyncedTemplates(): ResponseEntity<WhatsAppTemplateSyncResultDto> {
        val synced = templateSyncService.listSynced()
        return ResponseEntity.ok(
            WhatsAppTemplateSyncResultDto(
                synced = synced.size,
                created = 0,
                updated = synced.size,
                errors = emptyList()
            )
        )
    }

    @PostMapping("/templates/sync")
    fun syncTemplates(): ResponseEntity<Any> {
        return ResponseEntity.ok(templateSyncService.syncFromMeta().toFrontendDto())
    }

    @GetMapping("/service-window/{phone}")
    fun serviceWindow(@PathVariable phone: String): ResponseEntity<Any> {
        return ResponseEntity.ok(serviceWindowService.getServiceWindow(phone).toDto())
    }

    @GetMapping("/conversations")
    fun listConversations(
        @RequestParam(required = false) search: String?,
        @RequestParam(required = false) dateFrom: String?,
        @RequestParam(required = false) dateTo: String?,
        @RequestParam(required = false) from: String?,
        @RequestParam(required = false) to: String?,
        @RequestParam(required = false) unreadOnly: Boolean?,
        @RequestParam(required = false) limit: Int?,
        httpRequest: HttpServletRequest
    ): ResponseEntity<List<WhatsAppConversationSummaryDto>> {
        val range = resolveOptionalRange(dateFrom ?: from, dateTo ?: to)
        auditService.recordAccess(
            operatorUsername = resolveOperator(httpRequest),
            resource = "/whatsapp/conversations",
            details = listOfNotNull(
                search?.takeIf { it.isNotBlank() }?.let { "search=${it.take(64)}" },
                unreadOnly?.let { "unreadOnly=$it" }
            ).joinToString(";").ifBlank { null }
        )
        return ResponseEntity.ok(
            conversationQueryService.listConversations(
                WhatsAppConversationFilter(
                    search = search,
                    dateFrom = range?.first,
                    dateTo = range?.second,
                    unreadOnly = unreadOnly == true,
                    limit = limit ?: 50
                )
            )
        )
    }

    @GetMapping("/conversations/{phone}/thread")
    fun getConversationThread(
        @PathVariable phone: String,
        @RequestParam(required = false) dateFrom: String?,
        @RequestParam(required = false) dateTo: String?,
        @RequestParam(required = false) from: String?,
        @RequestParam(required = false) to: String?,
        @RequestParam(required = false) limit: Int?,
        httpRequest: HttpServletRequest
    ): ResponseEntity<List<WhatsAppThreadMessageDto>> {
        val range = resolveOptionalRange(dateFrom ?: from, dateTo ?: to)
        auditService.recordAccess(
            operatorUsername = resolveOperator(httpRequest),
            resource = "/whatsapp/conversations/$phone/thread",
            details = "phone=$phone"
        )
        return ResponseEntity.ok(
            conversationQueryService.getThread(
                phone = phone,
                dateFrom = range?.first,
                dateTo = range?.second,
                limit = limit ?: 200
            )
        )
    }

    @GetMapping("/conversations/{phone}/context")
    fun getConversationContext(@PathVariable phone: String): ResponseEntity<WhatsAppConversationContextDto> {
        return ResponseEntity.ok(conversationQueryService.getContext(phone))
    }

    @PostMapping("/conversations/{phone}/reply")
    fun replyToConversation(
        @PathVariable phone: String,
        @RequestBody request: WhatsAppConversationReplyBody,
        httpRequest: HttpServletRequest
    ): ResponseEntity<Any> {
        return try {
            val operator = resolveOperator(httpRequest)
            val agentId = resolveUserId(httpRequest)
            val isAdmin = resolveUserType(httpRequest) == "ADMIN"
            val result = conversationService.sendOperatorReply(
                phone = phone,
                text = request.text,
                operatorUsername = operator,
                agentId = agentId,
                isAdmin = isAdmin,
                replyToMessageId = request.replyToMessageId
            )
            auditService.recordReply(
                operatorUsername = operator,
                phone = phone,
                textLength = request.text.trim().length
            )
            ResponseEntity.ok(result)
        } catch (e: CrmConversationForbiddenException) {
            ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(mapOf("error" to (e.message ?: "No autorizado")))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Solicitud invalida")))
        } catch (e: Exception) {
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(mapOf("error" to (e.message ?: "Error al enviar")))
        }
    }

    @PostMapping("/conversations/{phone}/media", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun sendConversationMedia(
        @PathVariable phone: String,
        @RequestParam("file") file: MultipartFile,
        @RequestParam(required = false) caption: String?,
        @RequestParam(required = false) replyToMessageId: String?,
        httpRequest: HttpServletRequest
    ): ResponseEntity<Any> {
        return try {
            val operator = resolveOperator(httpRequest)
            val result = conversationService.sendOperatorMedia(
                phone = phone,
                bytes = file.bytes,
                mimeType = file.contentType,
                filename = file.originalFilename,
                caption = caption,
                operatorUsername = operator,
                agentId = resolveUserId(httpRequest),
                isAdmin = resolveUserType(httpRequest) == "ADMIN",
                replyToMessageId = replyToMessageId
            )
            auditService.recordReply(
                operatorUsername = operator,
                phone = phone,
                textLength = caption?.trim()?.length ?: 0
            )
            ResponseEntity.ok(result)
        } catch (e: CrmConversationForbiddenException) {
            ResponseEntity.status(HttpStatus.FORBIDDEN).body(mapOf("error" to (e.message ?: "No autorizado")))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Solicitud invalida")))
        } catch (e: Exception) {
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(mapOf("error" to (e.message ?: "Error al enviar media")))
        }
    }

    @PostMapping("/conversations/{phone}/template")
    fun sendConversationTemplate(
        @PathVariable phone: String,
        @RequestBody request: WhatsAppConversationTemplateBody,
        httpRequest: HttpServletRequest
    ): ResponseEntity<Any> {
        return try {
            val operator = resolveOperator(httpRequest)
            val result = conversationService.sendOperatorTemplate(
                phone = phone,
                templateCode = request.templateCode,
                operatorUsername = operator,
                agentId = resolveUserId(httpRequest),
                isAdmin = resolveUserType(httpRequest) == "ADMIN"
            )
            auditService.recordReply(
                operatorUsername = operator,
                phone = phone,
                textLength = request.templateCode.length
            )
            ResponseEntity.ok(result)
        } catch (e: CrmConversationForbiddenException) {
            ResponseEntity.status(HttpStatus.FORBIDDEN).body(mapOf("error" to (e.message ?: "No autorizado")))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Solicitud invalida")))
        } catch (e: Exception) {
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(mapOf("error" to (e.message ?: "Error al enviar plantilla")))
        }
    }

    @PostMapping("/conversations/{phone}/outbound/{logId}/retry")
    fun retryConversationOutbound(
        @PathVariable phone: String,
        @PathVariable logId: Int,
        httpRequest: HttpServletRequest
    ): ResponseEntity<Any> {
        return try {
            val operator = resolveOperator(httpRequest)
            val result = conversationService.retryFailedOutbound(
                phone = phone,
                logId = logId,
                operatorUsername = operator,
                agentId = resolveUserId(httpRequest),
                isAdmin = resolveUserType(httpRequest) == "ADMIN"
            )
            ResponseEntity.ok(result)
        } catch (e: CrmConversationForbiddenException) {
            ResponseEntity.status(HttpStatus.FORBIDDEN).body(mapOf("error" to (e.message ?: "No autorizado")))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Solicitud invalida")))
        } catch (e: Exception) {
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(mapOf("error" to (e.message ?: "Error al reintentar")))
        }
    }

    @GetMapping("/outbound-messages/{id}/media")
    fun downloadOutboundMedia(@PathVariable id: Int): ResponseEntity<Any> {
        val logEntry = messageLogRepository.findById(id).orElse(null)
            ?: return ResponseEntity.notFound().build()
        val path = mediaDownloadService.resolveStoredPath(logEntry.mediaStoredPath)
            ?: return ResponseEntity.notFound().build()
        val resource = FileSystemResource(path)
        val contentType = logEntry.mediaMimeType ?: MediaType.APPLICATION_OCTET_STREAM_VALUE
        val filename = logEntry.mediaFilename ?: path.fileName.toString()
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$filename\"")
            .contentType(MediaType.parseMediaType(contentType))
            .body(resource)
    }

    @PostMapping("/conversations/{phone}/resume-bot")
    fun resumeConversationBot(@PathVariable phone: String): ResponseEntity<Map<String, Any?>> {
        val result = handoffService.resumeBotAndTakeControl(phone, "advisor_closed")
        return ResponseEntity.ok(
            mapOf(
                "phone" to phone,
                "botPaused" to result.botPaused,
                "metaTransferred" to result.metaTransferred,
                "warning" to result.warning
            )
        )
    }

    @PostMapping("/conversations/{phone}/mark-all-read")
    fun markConversationAllRead(
        @PathVariable phone: String,
        httpRequest: HttpServletRequest
    ): ResponseEntity<WhatsAppMarkAllReadResultDto> {
        val result = conversationService.markAllRead(phone)
        auditService.recordMarkRead(
            operatorUsername = resolveOperator(httpRequest),
            phone = phone,
            inboundMessageId = null
        )
        return ResponseEntity.ok(result)
    }

    @PostMapping("/conversations/{id}/mark-read")
    fun markConversationRead(
        @PathVariable id: Int,
        httpRequest: HttpServletRequest
    ): ResponseEntity<Any> {
        val inbound = inboundMessageRepository.findById(id).orElse(null)
            ?: return ResponseEntity.notFound().build()
        val success = conversationService.markInboundAsRead(inbound)
        auditService.recordMarkRead(
            operatorUsername = resolveOperator(httpRequest),
            phone = inbound.phone,
            inboundMessageId = id
        )
        return ResponseEntity.ok(
            WhatsAppMarkReadResultDto(
                success = success,
                inboundMessageId = id,
                metaMessageId = inbound.metaMessageId
            )
        )
    }

    @GetMapping("/inbound-messages/{id}/media")
    fun downloadInboundMedia(@PathVariable id: Int): ResponseEntity<Any> {
        val inbound = inboundMessageRepository.findById(id).orElse(null)
            ?: return ResponseEntity.notFound().build()
        val path = mediaDownloadService.resolveStoredPath(inbound.mediaStoredPath)
            ?: return ResponseEntity.notFound().build()
        val resource = FileSystemResource(path)
        val contentType = inbound.mediaMimeType ?: MediaType.APPLICATION_OCTET_STREAM_VALUE
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"${path.fileName}\"")
            .contentType(MediaType.parseMediaType(contentType))
            .body(resource)
    }

    private fun resolveOperator(request: HttpServletRequest): String? {
        return request.getAttribute(PlatformAuthFilter.AUTH_USERNAME_ATTRIBUTE)?.toString()
    }

    private fun resolveUserId(request: HttpServletRequest): Int? {
        val raw = request.getAttribute(PlatformAuthFilter.AUTH_USER_ID_ATTRIBUTE) ?: return null
        return when (raw) {
            is Int -> raw
            is Number -> raw.toInt()
            else -> raw.toString().toIntOrNull()
        }
    }

    private fun resolveUserType(request: HttpServletRequest): String? =
        request.getAttribute(PlatformAuthFilter.AUTH_USER_TYPE_ATTRIBUTE)?.toString()?.trim()?.uppercase()

    private fun resolveOptionalRange(from: String?, to: String?): Pair<LocalDateTime, LocalDateTime>? {
        if (from.isNullOrBlank() && to.isNullOrBlank()) return null
        return queryService.resolveDateRange(from, to, 7)
    }

    private fun resolveInstantRange(from: String?, to: String?): Pair<Instant, Instant> {
        val range = queryService.resolveDateRange(from, to, 7)
        val zone = ZoneId.of("America/Lima")
        return range.first.atZone(zone).toInstant() to range.second.atZone(zone).toInstant()
    }

    private fun csvResponse(filename: String, content: String): ResponseEntity<String> {
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$filename\"")
            .contentType(MediaType.parseMediaType("text/csv"))
            .body(content)
    }
}
