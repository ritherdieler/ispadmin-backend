package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.config.WhatsAppProperties
import com.dscorp.wispadmin.wispadmin.controller.sendNotification
import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppInboundMessage
import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppMessageLog
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppInboundMessageRepository
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppMessageLogRepository
import com.dscorp.wispadmin.wispadmin.service.whatsapp.CrmConversationService
import com.dscorp.wispadmin.wispadmin.service.whatsapp.CrmEventPublisher
import com.dscorp.wispadmin.wispadmin.service.whatsapp.CrmTicketLinkService
import com.dscorp.wispadmin.wispadmin.service.whatsapp.CsatSurveyService
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppBusinessHoursChecker
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppChatStateService
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppConversationQueryService
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppConversationService
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppHandoffService
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppInboundSession
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppInboundIntent
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppInboundIntentRouter
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppIntentClassifier
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppInboundPayload
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppMediaDownloadService
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppTicketDescriptionFormatter
import com.dscorp.wispadmin.wispadmin.util.fcm.FcmConstants
import com.dscorp.wispadmin.wispadmin.util.fcm.FcmMessage
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap

@Service
class WhatsAppInboundMessageService(
    private val inboundMessageRepository: WhatsAppInboundMessageRepository,
    private val conversationService: WhatsAppConversationService,
    private val mediaDownloadService: WhatsAppMediaDownloadService,
    private val whatsAppService: WhatsAppService,
    private val messageLogRepository: WhatsAppMessageLogRepository,
    private val intentRouter: WhatsAppInboundIntentRouter,
    private val intentClassifier: WhatsAppIntentClassifier,
    private val whatsAppProperties: WhatsAppProperties,
    private val fcm: FirebaseMessaging,
    private val chatStateService: WhatsAppChatStateService,
    private val handoffService: WhatsAppHandoffService,
    private val crmEventPublisher: CrmEventPublisher,
    private val crmConversationService: CrmConversationService,
    private val crmTicketLinkService: CrmTicketLinkService,
    private val csatSurveyService: CsatSurveyService
) {

    private val log = LoggerFactory.getLogger(WhatsAppInboundMessageService::class.java)
    private val alertCooldownByPhone = ConcurrentHashMap<String, Long>()

    suspend fun processInboundMessageAsync(payload: WhatsAppInboundPayload) = withContext(Dispatchers.IO) {
        processInboundMessage(payload)
    }

    fun processInboundMessage(payload: WhatsAppInboundPayload) {
        val replyToLogId = conversationService.resolveReplyToLogId(payload.contextMessageId)
        val mediaStoredPath = payload.mediaId?.let {
            mediaDownloadService.downloadAndStore(it, payload.mediaMimeType)
        }

        val saved = inboundMessageRepository.save(
            WhatsAppInboundMessage(
                metaMessageId = payload.metaMessageId,
                phone = payload.phone,
                messageText = payload.messageText,
                messageType = payload.messageType,
                buttonReplyId = payload.buttonReplyId,
                buttonReplyTitle = payload.buttonReplyTitle,
                mediaId = payload.mediaId,
                mediaMimeType = payload.mediaMimeType,
                mediaStoredPath = mediaStoredPath,
                contextMessageId = payload.contextMessageId,
                replyToLogId = replyToLogId,
                processed = false,
                replySent = false
            )
        )

        val subscription = conversationService.findSubscriptionByPhone(payload.phone)
        notifySecretaryInbound(payload.phone, subscription?.getFullName())
        val session = chatStateService.beginInboundInteraction(payload.phone)
        if (session.autoResumedFromAdvisorWait) {
            try {
                handoffService.resumeBotAndTakeControl(payload.phone, "auto_resume_advisor_wait")
            } catch (e: Exception) {
                log.warn("Auto-resume Meta/handoff sync failed phone={}: {}", payload.phone, e.message)
            }
        }

        val csatHandled = try {
            csatSurveyService.tryHandleInbound(
                phone = payload.phone,
                messageType = payload.messageType,
                buttonReplyId = payload.buttonReplyId,
                messageText = payload.messageText,
                metaMessageId = payload.metaMessageId
            )
        } catch (e: Exception) {
            log.warn("CSAT inbound handle failed phone={}: {}", payload.phone, e.message)
            false
        }

        val (replySent, errorMsg) = when {
            csatHandled -> Pair(true, null)

            session.botPaused -> {
                log.info("Bot pausado: chat {} esta esperando asesor", payload.phone)
                Pair(false, null)
            }

            conversationService.hasRecentOperatorReply(payload.phone) -> {
                log.info("Bot en silencio: hay respuesta reciente de operador para {}", payload.phone)
                Pair(false, null)
            }

            shouldSkipForInboundBurst(payload) -> {
                log.info("Anti-spam: rafaga de mensajes para {}", payload.phone)
                Pair(false, null)
            }

            else -> routeAndReply(payload, subscription, session)
        }

        val finalized = inboundMessageRepository.save(
            saved.copy(
                subscriptionId = subscription?.id,
                processed = true,
                replySent = replySent,
                errorMessage = errorMsg
            )
        )
        val crmConversation = try {
            crmConversationService.touchInbound(finalized.phone, finalized.subscriptionId)
        } catch (e: Exception) {
            log.warn("No se pudo sincronizar CrmConversation inbound {}: {}", finalized.id, e.message)
            null
        }
        publishInboundRealtimeEvents(finalized, subscription?.getFullName(), crmConversation)
    }

    private fun publishInboundRealtimeEvents(
        inbound: WhatsAppInboundMessage,
        clientName: String?,
        crmConversation: com.dscorp.wispadmin.wispadmin.data.model.CrmConversation?
    ) {
        try {
            val preview = inbound.messageText
                ?: inbound.buttonReplyTitle
                ?: inbound.messageType
            val hasMedia = WhatsAppConversationQueryService.inboundHasMedia(inbound)
            val unreadCount = inboundMessageRepository.findByPhoneAndReadAtIsNull(inbound.phone).size
            crmEventPublisher.publish(
                eventType = CrmEventPublisher.MESSAGE_RECEIVED,
                payload = mapOf(
                    "phone" to inbound.phone,
                    "inboundMessageId" to inbound.id,
                    "threadMessageId" to "inbound:${inbound.id}",
                    "messageType" to inbound.messageType,
                    "body" to inbound.messageText,
                    "buttonReplyTitle" to inbound.buttonReplyTitle,
                    "hasMedia" to hasMedia,
                    "mediaId" to inbound.id,
                    "mediaMimeType" to inbound.mediaMimeType,
                    "replyToLogId" to inbound.replyToLogId,
                    "clientName" to clientName,
                    "subscriptionId" to inbound.subscriptionId,
                    "createdAt" to inbound.createdAt.toString()
                )
            )
            crmEventPublisher.publish(
                eventType = CrmEventPublisher.CONVERSATION_UPDATED,
                payload = mapOf(
                    "phone" to inbound.phone,
                    "clientName" to clientName,
                    "subscriptionId" to inbound.subscriptionId,
                    "identified" to (inbound.subscriptionId != null),
                    "lastMessagePreview" to preview?.take(240),
                    "lastMessageAt" to inbound.createdAt.toString(),
                    "unreadCount" to unreadCount,
                    "lastButtonReplyId" to inbound.buttonReplyId,
                    "lastHasMedia" to hasMedia,
                    "conversationId" to crmConversation?.id,
                    "status" to crmConversation?.status?.name,
                    "assignedAgentId" to crmConversation?.assignedAgentId,
                    "priority" to crmConversation?.priority
                )
            )
        } catch (e: Exception) {
            log.warn("No se pudo publicar evento CRM para inbound {}: {}", inbound.id, e.message)
        }
    }

    private fun routeAndReply(
        payload: WhatsAppInboundPayload,
        subscription: com.dscorp.wispadmin.wispadmin.data.model.Subscription?,
        session: WhatsAppInboundSession
    ): Pair<Boolean, String?> {
        return when (payload.messageType) {
            "text" -> handleText(payload, subscription, session)
            "button_reply" -> {
                if (payload.buttonReplyId == WhatsAppConversationService.BUTTON_HOME) {
                    return sendAutoReplyResult(
                        phone = payload.phone,
                        subscriptionId = subscription?.id,
                        result = conversationService.sendMainMenuForNavigation(payload.phone, subscription)
                    )
                }

                if (payload.buttonReplyId == WhatsAppConversationService.BUTTON_BACK) {
                    return sendAutoReplyResult(
                        phone = payload.phone,
                        subscriptionId = subscription?.id,
                        result = conversationService.sendBackNavigation(payload.phone, subscription)
                    )
                }

                if (conversationService.isSupportEntryButton(payload.buttonReplyId)) {
                    return sendAutoReplyResult(
                        phone = payload.phone,
                        subscriptionId = subscription?.id,
                        result = conversationService.sendSupportDiagnosticQuestion(
                            phone = payload.phone,
                            subscription = subscription,
                            buttonReplyId = payload.buttonReplyId
                        )
                    )
                }

                if (conversationService.isSupportDiagnosticButton(payload.buttonReplyId)) {
                    val issueCode = conversationService.currentSupportIssueCode(payload.phone)
                    val result = conversationService.closeSupportDiagnosticWithButton(
                        phone = payload.phone,
                        subscription = subscription,
                        buttonReplyId = payload.buttonReplyId
                    )
                    val reply = sendAutoReplyResult(payload.phone, subscription?.id, result)
                    if (reply.first) {
                        createGuidedTicketFromDiagnostic(
                            phone = payload.phone,
                            subscription = subscription,
                            issueCode = issueCode,
                            buttonReplyId = payload.buttonReplyId
                        )
                        handoffService.pauseBotAndPassToAdvisor(payload.phone, "support_diagnostic")
                        notifySecretaryInbound(
                            phone = payload.phone,
                            clientName = subscription?.getFullName(),
                            force = true,
                            advisorRequired = true
                        )
                    }
                    return reply
                }

                if (payload.buttonReplyId == WhatsAppConversationService.BUTTON_SUPPORT ||
                    payload.buttonReplyId == WhatsAppConversationService.BUTTON_REPORT_FAULT
                ) {
                    return sendAutoReplyResult(
                        phone = payload.phone,
                        subscriptionId = subscription?.id,
                        result = conversationService.sendSupportEntryMenu(payload.phone, subscription)
                    )
                }

                if (payload.buttonReplyId == WhatsAppConversationService.BUTTON_DEBT) {
                    return sendAutoReplyResult(
                        phone = payload.phone,
                        subscriptionId = subscription?.id,
                        result = conversationService.sendDebtResponseMenu(payload.phone, subscription)
                    )
                }

                val text = conversationService.handleButtonReply(
                    phone = payload.phone,
                    buttonReplyId = payload.buttonReplyId,
                    subscription = subscription,
                    sourceText = payload.buttonReplyTitle
                )
                val prefix = when (payload.buttonReplyId) {
                    WhatsAppConversationService.BUTTON_DEBT -> "[DEUDA] "
                    WhatsAppConversationService.BUTTON_PAID -> "[YA_PAGUE] "
                    WhatsAppConversationService.BUTTON_SUPPORT,
                    WhatsAppConversationService.BUTTON_REPORT_FAULT -> "[AVERIA] "
                    WhatsAppConversationService.BUTTON_INSTALLATION -> "[INSTALACION] "
                    WhatsAppConversationService.BUTTON_ADVISOR -> "[ASESOR] "
                    else -> "[AUTO] "
                }
                val reply = sendTextReply(payload.phone, text, subscription?.id, prefix)
                if (reply.first &&
                    (payload.buttonReplyId == WhatsAppConversationService.BUTTON_INSTALLATION ||
                        payload.buttonReplyId == WhatsAppConversationService.BUTTON_ADVISOR)
                ) {
                    handoffService.pauseBotAndPassToAdvisor(
                        payload.phone,
                        if (payload.buttonReplyId == WhatsAppConversationService.BUTTON_INSTALLATION) {
                            "installation_request"
                        } else {
                            "advisor_request"
                        }
                    )
                    notifySecretaryInbound(
                        phone = payload.phone,
                        clientName = subscription?.getFullName(),
                        force = true,
                        advisorRequired = true
                    )
                }
                reply
            }
            "image", "document" -> sendTextReply(
                payload.phone,
                conversationService.buildVoucherReceivedResponse(),
                subscription?.id,
                "[VOUCHER] "
            )
            else -> Pair(false, null)
        }
    }

    private fun handleText(
        payload: WhatsAppInboundPayload,
        subscription: com.dscorp.wispadmin.wispadmin.data.model.Subscription?,
        session: WhatsAppInboundSession
    ): Pair<Boolean, String?> {
        val withinHours = WhatsAppBusinessHoursChecker.isWithinBusinessHours(whatsAppProperties.autoReply)
        val classification = intentClassifier.classify(payload.phone, payload.messageText)
        val intent = classification.intent
        val normalizedText = WhatsAppInboundIntentRouter.normalize(payload.messageText).orEmpty()

        if (session.isNewOrExpired) {
            val result = conversationService.sendMainMenu(
                phone = payload.phone,
                subscription = subscription,
                includeGreeting = true
            )
            return sendAutoReplyResult(payload.phone, subscription?.id, result)
        }

        if (normalizedText in setOf("menu", "menu inicio", "menu principal", "inicio")) {
            val result = conversationService.sendMainMenuForNavigation(payload.phone, subscription)
            return sendAutoReplyResult(payload.phone, subscription?.id, result)
        }

        if (normalizedText in setOf("volver", "menu anterior", "atras", "regresar")) {
            val result = conversationService.sendBackNavigation(payload.phone, subscription)
            return sendAutoReplyResult(payload.phone, subscription?.id, result)
        }

        if (intent == WhatsAppInboundIntent.HUMAN_ESCALATION || classification.escalate) {
            val escalateReason = classification.escalateReason ?: "human_escalation"
            val replyText = if (!withinHours) {
                conversationService.buildAfterHoursHandoffMessage()
            } else {
                conversationService.handleHumanEscalation(
                    subscription = subscription,
                    phone = payload.phone,
                    messageText = payload.messageText
                )
            }
            val reply = sendTextReply(
                payload.phone,
                replyText,
                subscription?.id,
                "[ASESOR:${classification.source}] "
            )
            if (reply.first) {
                handoffService.pauseBotAndPassToAdvisor(payload.phone, escalateReason)
                notifySecretaryInbound(
                    phone = payload.phone,
                    clientName = subscription?.getFullName(),
                    force = true,
                    advisorRequired = true
                )
            }
            return reply
        }

        if (chatStateService.hasPendingInteractiveMenu(payload.phone)) {
            return sendTextReply(
                payload.phone,
                conversationService.invalidInteractiveSelectionText(),
                subscription?.id,
                "[MENU_INVALID] "
            )
        }

        if (!withinHours && intent == WhatsAppInboundIntent.ACK) {
            return sendTextReply(
                payload.phone,
                conversationService.buildAfterHoursHandoffMessage(),
                subscription?.id,
                "[AFTER_HOURS] "
            )
        }

        return when (intent) {
            WhatsAppInboundIntent.ACK -> sendTextReply(
                payload.phone,
                conversationService.buildAckResponse(subscription),
                subscription?.id,
                "[ACK] "
            )

            WhatsAppInboundIntent.DEBT_INQUIRY -> {
                val result = conversationService.sendDebtResponseMenu(payload.phone, subscription)
                sendAutoReplyResult(payload.phone, subscription?.id, result)
            }

            WhatsAppInboundIntent.PAYMENT_CLAIM -> sendTextReply(
                payload.phone,
                conversationService.buildPaidResponse(subscription),
                subscription?.id,
                "[YA_PAGUE] "
            )

            WhatsAppInboundIntent.HUMAN_ESCALATION -> sendTextReply(
                payload.phone,
                conversationService.handleHumanEscalation(
                    subscription = subscription,
                    phone = payload.phone,
                    messageText = payload.messageText
                ),
                subscription?.id,
                "[ASESOR] "
            )

            WhatsAppInboundIntent.TECHNICAL_ISSUE,
            WhatsAppInboundIntent.SUPPORT -> {
                val result = conversationService.sendSupportEntryMenu(
                    phone = payload.phone,
                    subscription = subscription
                )
                sendAutoReplyResult(payload.phone, subscription?.id, result)
            }

            WhatsAppInboundIntent.TICKET_STATUS -> sendTextReply(
                payload.phone,
                crmTicketLinkService.formatStatusReply(payload.phone),
                subscription?.id,
                "[TICKET_STATUS] "
            )

            WhatsAppInboundIntent.INSTALLATION_REQUEST -> {
                val reply = sendTextReply(
                    payload.phone,
                    conversationService.buildInstallationHandoverResponse(),
                    subscription?.id,
                    "[INSTALACION] "
                )
                if (reply.first) {
                    handoffService.pauseBotAndPassToAdvisor(payload.phone, "installation_request")
                    notifySecretaryInbound(
                        phone = payload.phone,
                        clientName = subscription?.getFullName(),
                        force = true,
                        advisorRequired = true
                    )
                }
                reply
            }

            WhatsAppInboundIntent.GREETING -> {
                val result = conversationService.sendMainMenu(
                    phone = payload.phone,
                    subscription = subscription,
                    includeGreeting = session.isNewOrExpired
                )
                sendAutoReplyResult(payload.phone, subscription?.id, result)
            }

            WhatsAppInboundIntent.UNKNOWN -> {
                val result = conversationService.sendMainMenu(
                    phone = payload.phone,
                    subscription = subscription,
                    includeGreeting = session.isNewOrExpired
                )
                sendAutoReplyResult(payload.phone, subscription?.id, result)
            }
        }
    }

    private fun shouldSkipForInboundBurst(payload: WhatsAppInboundPayload): Boolean {
        if (payload.messageType != "text") return false
        if (!conversationService.isInboundBurst(payload.phone)) return false
        return when (intentRouter.route(payload.messageText)) {
            WhatsAppInboundIntent.DEBT_INQUIRY,
            WhatsAppInboundIntent.TECHNICAL_ISSUE,
            WhatsAppInboundIntent.SUPPORT,
            WhatsAppInboundIntent.TICKET_STATUS,
            WhatsAppInboundIntent.INSTALLATION_REQUEST,
            WhatsAppInboundIntent.HUMAN_ESCALATION,
            WhatsAppInboundIntent.GREETING -> false
            else -> !chatStateService.hasPendingInteractiveMenu(payload.phone)
        }
    }

    private fun createGuidedTicketFromDiagnostic(
        phone: String,
        subscription: com.dscorp.wispadmin.wispadmin.data.model.Subscription?,
        issueCode: String?,
        buttonReplyId: String?
    ) {
        try {
            val category = when (issueCode) {
                "NO_INTERNET", "SLOW_INTERNET", "WIFI_NOT_VISIBLE",
                "INTERNET_INTERRUPTION", "BOTH_SERVICES" -> "Sin Conexión a Internet"
                "CABLE_INTERRUPTION", "TV_NO_SIGNAL", "TV_INTERFERENCE", "DECODER_ERROR" -> "Otros"
                else -> "Sin Conexión a Internet"
            }
            val description = WhatsAppTicketDescriptionFormatter.buildHumanReadableTicketDescription(
                issueCode = issueCode,
                buttonReplyId = buttonReplyId,
            )
            val conversation = crmConversationService.getByPhone(phone)
            crmTicketLinkService.createGuidedFaultTicket(
                phone = phone,
                conversationId = conversation?.id,
                category = category,
                description = description,
                subscription = subscription,
                createdBy = "bot"
            )
        } catch (e: Exception) {
            log.warn("No se pudo crear ticket desde diagnostico WhatsApp para {}: {}", phone, e.message)
        }
    }

    private fun sendTextReply(
        phone: String,
        text: String,
        subscriptionId: Int?,
        logPrefix: String = ""
    ): Pair<Boolean, String?> {
        return try {
            val result = whatsAppService.sendTextMessage(phoneNumber = phone, message = sanitizeOutboundText(text))
            if (result.success) {
                persistAutoReplyLog(
                    phone = phone,
                    subscriptionId = subscriptionId,
                    message = "$logPrefix$text",
                    metaMessageId = result.metaMessageId
                )
                Pair(true, null)
            } else {
                Pair(false, result.metaResponse.take(1000).ifBlank { "No se pudo enviar respuesta." })
            }
        } catch (e: Exception) {
            log.warn("Inbound: no se pudo responder al telefono $phone: ${e.message}")
            Pair(false, e.message?.take(1000))
        }
    }

    private fun sendAutoReplyResult(
        phone: String,
        subscriptionId: Int?,
        result: WhatsAppConversationService.AutoReplyResult
    ): Pair<Boolean, String?> {
        if (result.success) {
            persistAutoReplyLog(
                phone = phone,
                subscriptionId = subscriptionId,
                message = result.messageText,
                metaMessageId = result.metaMessageId
            )
        }
        return Pair(result.success, if (result.success) null else "No se pudo enviar respuesta interactiva.")
    }

    private fun sanitizeOutboundText(text: String): String {
        return text
            .replace(Regex("""^\[SUPPORT_(?:MENU|DIAG|CLOSED|INVALID):?[A-Z_]*]\s*"""), "")
            .trim()
    }

    private fun persistAutoReplyLog(
        phone: String,
        subscriptionId: Int?,
        message: String,
        metaMessageId: String?
    ) {
        val now = LocalDateTime.now()
        messageLogRepository.save(
            WhatsAppMessageLog(
                subscriptionId = subscriptionId,
                phone = phone,
                messageType = WhatsAppConversationService.MESSAGE_TYPE_AUTO_REPLY,
                status = "SENT",
                metaMessageId = metaMessageId,
                message = message,
                sentAt = now,
                createdAt = now
            )
        )
    }

    private fun notifySecretaryInbound(
        phone: String,
        clientName: String?,
        force: Boolean = false,
        advisorRequired: Boolean = false
    ) {
        if (!whatsAppProperties.inboundAlert.enabled) return

        val cooldownMs = whatsAppProperties.inboundAlert.cooldownMinutes.coerceAtLeast(1) * 60_000L
        val now = System.currentTimeMillis()
        val last = alertCooldownByPhone[phone]
        if (!force && last != null && now - last < cooldownMs) {
            return
        }
        alertCooldownByPhone[phone] = now

        try {
            val preview = clientName?.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
            val title = if (advisorRequired) {
                "WhatsApp: asesor requerido"
            } else {
                "WhatsApp: mensaje nuevo"
            }
            val message = if (advisorRequired) {
                "Cliente$preview espera atención por WhatsApp."
            } else {
                "Hay mensajes en WhatsApp. Por favor atienda a los clientes.$preview"
            }
            FcmMessage(
                title = title,
                message = message,
                topic = FcmConstants.FCM_SECRETARY_TOPIC,
                type = FcmMessage.FcmMessageType.WHATSAPP_INBOUND,
                id = phone
            ).sendNotification(fcm)
        } catch (e: Exception) {
            log.warn("No se pudo enviar alerta FCM WhatsApp para {}: {}", phone, e.message)
        }
    }
}
