package com.dscorp.wispadmin.wispadmin.service.whatsapp

import com.dscorp.wispadmin.wispadmin.config.WhatsAppProperties
import com.dscorp.wispadmin.wispadmin.data.model.InstallationType
import com.dscorp.wispadmin.wispadmin.data.model.Payment
import com.dscorp.wispadmin.wispadmin.data.model.ServiceStatus
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppConversationStep
import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppInboundMessage
import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppMessageLog
import com.dscorp.wispadmin.wispadmin.dto.WhatsAppMarkAllReadResultDto
import com.dscorp.wispadmin.wispadmin.dto.WhatsAppThreadMessageDto
import com.dscorp.wispadmin.wispadmin.repository.PaymentRepository
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppInboundMessageRepository
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppMessageLogRepository
import com.dscorp.wispadmin.wispadmin.service.WhatsAppService
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppThreadMessageMapper.toThreadMessage
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.util.Locale

@Service
class WhatsAppConversationService(
    private val whatsAppService: WhatsAppService,
    private val subscriptionRepository: SubscriptionRepository,
    private val paymentRepository: PaymentRepository,
    private val messageLogRepository: WhatsAppMessageLogRepository,
    private val inboundMessageRepository: WhatsAppInboundMessageRepository,
    private val serviceWindowService: WhatsAppServiceWindowService,
    private val whatsAppProperties: WhatsAppProperties,
    private val intentRouter: WhatsAppInboundIntentRouter,
    private val chatStateService: WhatsAppChatStateService,
    private val crmConversationService: CrmConversationService,
    private val mediaDownloadService: WhatsAppMediaDownloadService,
    private val templateDeliveryService: WhatsAppTemplateDeliveryService,
    private val templateDisplayService: WhatsAppTemplateDisplayService,
    private val operatorDisplayNameResolver: WhatsAppOperatorDisplayNameResolver,
    private val crmEventPublisher: CrmEventPublisher,
) {

    private val log = LoggerFactory.getLogger(WhatsAppConversationService::class.java)

    fun buildAutoReplyButtons(subscription: Subscription?): List<WhatsAppService.InteractiveButtonOption> {
        return WhatsAppBotMenuCatalog.mainMenu.map {
            WhatsAppService.InteractiveButtonOption(it.id, it.title)
        }
    }

    fun buildGreetingBody(subscription: Subscription?): String {
        return buildMainMenuBody(includeGreeting = true)
    }

    fun buildAckResponse(subscription: Subscription?): String {
        return "Gracias. Si necesita algo mas, puede escribir MENU o ASESOR."
    }

    fun sendAutoReplyWithButtons(phone: String, subscription: Subscription?): AutoReplyResult {
        return sendMainMenu(phone, subscription)
    }

    fun sendMainMenu(
        phone: String,
        subscription: Subscription?,
        contextMessageId: String? = null
    ): AutoReplyResult {
        return sendMainMenuInternal(phone, includeGreeting = false, contextMessageId = contextMessageId)
    }

    fun sendMainMenu(
        phone: String,
        subscription: Subscription?,
        includeGreeting: Boolean,
        contextMessageId: String? = null
    ): AutoReplyResult {
        return sendMainMenuInternal(
            phone = phone,
            includeGreeting = includeGreeting,
            contextMessageId = contextMessageId
        )
    }

    fun sendMainMenuForNavigation(
        phone: String,
        subscription: Subscription?,
        contextMessageId: String? = null
    ): AutoReplyResult {
        return sendMainMenuInternal(phone, includeGreeting = false, contextMessageId = contextMessageId)
    }

    fun handleButtonReply(
        phone: String,
        buttonReplyId: String?,
        subscription: Subscription?,
        sourceText: String? = null
    ): String {
        return when (buttonReplyId) {
            BUTTON_REPORT_FAULT,
            BUTTON_SUPPORT -> handleSupportOrTechnicalIssue(
                subscription = subscription,
                phone = phone,
                messageText = sourceText,
                fromButton = true
            )
            BUTTON_DEBT -> buildDebtResponse(subscription)
            BUTTON_PAID -> buildPaidResponse(subscription)
            BUTTON_PAYMENT_PROOF -> buildPaymentProofRequest(subscription)
            BUTTON_INSTALLATION -> buildInstallationHandoverResponse()
            BUTTON_ADVISOR -> handleHumanEscalation(
                subscription = subscription,
                phone = phone,
                messageText = sourceText,
            )
            else -> invalidInteractiveSelectionText()
        }
    }

    fun hasPendingSupportDiagnostic(phone: String): Boolean {
        return chatStateService.hasPendingSupportDiagnostic(phone)
    }

    fun hasPendingInteractiveMenu(phone: String): Boolean {
        return chatStateService.hasPendingInteractiveMenu(phone)
    }

    fun resolvePendingTextSelection(
        phone: String,
        subscription: Subscription?,
        messageText: String?
    ): String? {
        val options = when (chatStateService.currentStep(phone)) {
            WhatsAppConversationStep.MAIN_MENU -> WhatsAppBotMenuCatalog.mainMenu
            WhatsAppConversationStep.SUPPORT_MENU -> supportEntryButtons(supportProfile(subscription)).map {
                WhatsAppInteractiveOption(it.id, it.title)
            }
            WhatsAppConversationStep.SUPPORT_DIAG -> {
                val issue = SupportIssue.byCode(currentSupportIssueCode(phone)) ?: return null
                diagnosticQuestion(subscription, issue).buttons.map {
                    WhatsAppInteractiveOption(it.id, it.title)
                }
            }
            WhatsAppConversationStep.DEBT_VIEW -> WhatsAppBotMenuCatalog.debtMenu
            WhatsAppConversationStep.AWAITING_PAYMENT_PROOF -> WhatsAppBotMenuCatalog.paymentProofMenu
            else -> return null
        }
        return WhatsAppBotTextSelectionResolver.resolve(messageText, options)
    }

    fun handleSupportDiagnosticReply(
        subscription: Subscription?,
        phone: String,
        messageText: String?
    ): String {
        return when (chatStateService.currentStep(phone)) {
            WhatsAppConversationStep.SUPPORT_MENU -> handleSupportMenuSelection(subscription, messageText)
            WhatsAppConversationStep.SUPPORT_DIAG -> closeSupportDiagnosticFromCurrentStep(messageText)
            else -> buildSupportEntryMenu(subscription)
        }
    }

    fun handleHumanEscalation(
        subscription: Subscription?,
        phone: String,
        messageText: String?
    ): String {
        return buildHumanHandoffClientMessage()
    }

    fun buildInstallationHandoverResponse(): String {
        return buildHumanHandoffClientMessage(kind = WhatsAppHandoffCopyKind.INSTALLATION)
    }

    fun buildHumanHandoffClientMessage(
        now: LocalDateTime? = null,
        kind: WhatsAppHandoffCopyKind = WhatsAppHandoffCopyKind.ADVISOR_QUEUE
    ): String {
        val at = now ?: LocalDateTime.now(WhatsAppBusinessHoursChecker.zone)
        return if (WhatsAppBusinessHoursChecker.isWithinBusinessHours(whatsAppProperties.autoReply, at)) {
            buildAdvisorClosureMessage(kind)
        } else {
            buildAfterHoursHandoffMessage(kind)
        }
    }

    fun buildHumanFollowUpClientMessage(
        inHoursText: String,
        afterHoursLead: String,
        afterHoursFollowUp: String? = null,
        now: LocalDateTime? = null
    ): String {
        val at = now ?: LocalDateTime.now(WhatsAppBusinessHoursChecker.zone)
        if (WhatsAppBusinessHoursChecker.isWithinBusinessHours(whatsAppProperties.autoReply, at)) {
            return inHoursText
        }
        val followUp = (afterHoursFollowUp ?: whatsAppProperties.autoReply.afterHoursHumanFollowUpMessage).trim()
        return """
            |$afterHoursLead
            |
            |$followUp
            |
            |${businessHoursSentence()}
        """.trimMargin()
    }

    fun businessHoursSentence(): String {
        return "Nuestro horario es de ${whatsAppProperties.autoReply.secretaryHours}."
    }

    fun handleSupportOrTechnicalIssue(
        subscription: Subscription?,
        phone: String,
        messageText: String?,
        fromButton: Boolean
    ): String {
        return buildSupportEntryMenu(subscription)
    }

    fun sendSupportEntryMenu(
        phone: String,
        subscription: Subscription?,
        contextMessageId: String? = null
    ): AutoReplyResult {
        val profile = supportProfile(subscription)
        val bodyText = supportEntryBody(profile)
        return sendInteractiveOptions(
            phone = phone,
            bodyText = bodyText,
            options = supportEntryButtons(profile),
            marker = "[SUPPORT_MENU:${profile.code}] ",
            currentStep = WhatsAppConversationStep.SUPPORT_MENU,
            footerText = WhatsAppBotMenuCatalog.GLOBAL_COMMANDS_FOOTER,
            contextMessageId = contextMessageId
        )
    }

    fun sendSupportDiagnosticQuestion(
        phone: String,
        subscription: Subscription?,
        buttonReplyId: String?,
        contextMessageId: String? = null
    ): AutoReplyResult {
        val profile = supportProfile(subscription)
        val issue = supportIssueForButton(profile, buttonReplyId)
            ?: return invalidInteractiveSelection(phone)
        val question = diagnosticQuestion(subscription, issue)
        return sendInteractiveOptions(
            phone = phone,
            bodyText = question.text,
            options = question.buttons,
            marker = "[SUPPORT_DIAG:${issue.code}] ",
            currentStep = WhatsAppConversationStep.SUPPORT_DIAG,
            stepMetadata = "issue=${issue.code}",
            footerText = WhatsAppBotMenuCatalog.GLOBAL_COMMANDS_FOOTER,
            contextMessageId = contextMessageId
        )
    }

    fun currentSupportIssueCode(phone: String): String? {
        val meta = chatStateService.getMetadata(phone) ?: return null
        return ISSUE_META_REGEX.find(meta)?.groupValues?.getOrNull(1)
    }

    fun closeSupportDiagnosticWithButton(
        phone: String,
        subscription: Subscription?,
        buttonReplyId: String?
    ): AutoReplyResult {
        if (!buttonReplyId.orEmpty().startsWith(DIAG_BUTTON_PREFIX)) {
            return invalidInteractiveSelection(phone)
        }
        val issueCode = currentSupportIssueCode(phone)
        val diagAnswer = buttonReplyId.orEmpty().removePrefix(DIAG_BUTTON_PREFIX)
        val bodyText = buildHumanHandoffClientMessage(kind = WhatsAppHandoffCopyKind.SUPPORT_CASE)
        return sendTextSupportReply(
            phone = phone,
            bodyText = bodyText,
            marker = "[SUPPORT_CLOSED:ESPERANDO_ASESOR:${issueCode ?: "UNKNOWN"}:$diagAnswer] "
        )
    }

    fun isSupportEntryButton(buttonReplyId: String?): Boolean {
        return WhatsAppBotMenuCatalog.isSupportIssue(buttonReplyId)
    }

    fun isSupportDiagnosticButton(buttonReplyId: String?): Boolean {
        return WhatsAppBotMenuCatalog.isSupportDiagnostic(buttonReplyId)
    }

    fun invalidInteractiveSelectionText(): String {
        return "Para continuar, seleccione una opcion del menu en pantalla 👇"
    }

    fun sendDebtResponseMenu(
        phone: String,
        subscription: Subscription?,
        contextMessageId: String? = null
    ): AutoReplyResult {
        val bodyText = buildDebtResponse(subscription)
        return sendInteractiveOptions(
            phone = phone,
            bodyText = bodyText,
            options = WhatsAppBotMenuCatalog.debtMenu.map {
                WhatsAppService.InteractiveButtonOption(it.id, it.title)
            },
            marker = "[DEUDA] ",
            currentStep = WhatsAppConversationStep.DEBT_VIEW,
            footerText = WhatsAppBotMenuCatalog.PAYMENT_PROOF_FOOTER,
            contextMessageId = contextMessageId
        )
    }

    fun sendPaymentProofRequest(
        phone: String,
        subscription: Subscription?,
        contextMessageId: String? = null
    ): AutoReplyResult {
        return sendInteractiveOptions(
            phone = phone,
            bodyText = buildPaymentProofRequest(subscription),
            options = WhatsAppBotMenuCatalog.paymentProofMenu.map {
                WhatsAppService.InteractiveButtonOption(it.id, it.title)
            },
            marker = "[COMPROBANTE] ",
            currentStep = WhatsAppConversationStep.AWAITING_PAYMENT_PROOF,
            footerText = WhatsAppBotMenuCatalog.PAYMENT_PROOF_FOOTER,
            contextMessageId = contextMessageId
        )
    }

    fun sendPaidStatusMenu(
        phone: String,
        subscription: Subscription?,
        contextMessageId: String? = null
    ): AutoReplyResult {
        return sendInteractiveOptions(
            phone = phone,
            bodyText = buildPaidResponse(subscription),
            options = WhatsAppBotMenuCatalog.paymentProofMenu.map {
                WhatsAppService.InteractiveButtonOption(it.id, it.title)
            },
            marker = "[YA_PAGUE] ",
            currentStep = WhatsAppConversationStep.AWAITING_PAYMENT_PROOF,
            footerText = WhatsAppBotMenuCatalog.PAYMENT_PROOF_FOOTER,
            contextMessageId = contextMessageId
        )
    }

    fun buildPaymentProofRequest(subscription: Subscription?): String {
        val cfg = whatsAppProperties.autoReply
        return """
            |📎 Para registrar su pago, envienos el comprobante como *foto* o *PDF* por este chat.
            |
            |Puede pagar con Yape/Plin al ${cfg.yapePlin} o al BCP ${cfg.bcpAccount}.
            |Cuando lo recibamos, lo validaremos y actualizaremos su cuenta.
        """.trimMargin()
    }

    fun buildPaymentProofReminder(): String {
        return "Aun no hemos recibido su comprobante 📎 Puede enviarlo como foto o PDF por este chat, " +
            "o tocar *Menu principal* si necesita otra cosa."
    }

    fun buildDebtResponse(subscription: Subscription?): String {
        if (subscription == null) {
            return accountNotFoundMessage()
        }
        val unpaid = unpaidPayments(subscription.id)
        val total = unpaid.sumOf { it.amountToPay }
        val clientName = clientFullName(subscription)
        val planName = subscription.plan?.name?.takeIf { it.isNotBlank() }
            ?: subscription.installationType?.name
            ?: "Servicio activo"
        val dueDate = unpaid.firstOrNull()?.billingDateDatetime?.let { date ->
            "%02d/%02d/%04d".format(date.dayOfMonth, date.monthValue, date.year)
        } ?: "Sin vencimiento pendiente"
        val cfg = whatsAppProperties.autoReply

        return """
            |📄 *Estado de su cuenta:*
            |Estimado(a) $clientName, su saldo pendiente al dia de hoy es S/ ${"%.2f".format(total)}.
            |
            |• Servicio: $planName
            |• Saldo pendiente: S/ ${"%.2f".format(total)}
            |• Fecha de vencimiento: $dueDate
            |
            |Puede pagar por CCI/BCP ${cfg.bcpAccount} o Yape/Plin al ${cfg.yapePlin}. Cuando pague, envienos el comprobante por este chat.
        """.trimMargin()
    }

    fun buildPaidResponse(subscription: Subscription?, now: LocalDateTime? = null): String {
        if (subscription == null) {
            return accountNotFoundMessage()
        }
        val unpaid = unpaidPayments(subscription.id)
        val firstName = firstNameOf(subscription)
        val greeting = if (firstName != null) "Hola $firstName," else "Hola,"
        val cutOffLine = if (subscription.serviceStatus == ServiceStatus.CUT_OFF) {
            "\nSu servicio aparece cortado. Se reactivara cuando validemos el pago."
        } else {
            ""
        }

        if (unpaid.isEmpty()) {
            return """
                |$greeting acabamos de revisar su cuenta: el pago ya esta registrado.
                |Su cuenta esta al dia. Gracias por su puntualidad.$cutOffLine
            """.trimMargin()
        }

        val total = unpaid.sumOf { it.amountToPay }
        val count = unpaid.size
        val word = if (count == 1) "factura" else "facturas"
        val oldest = unpaid.firstOrNull()?.let { formatBillingPeriod(it) }

        val base = """
            |$greeting gracias por avisarnos.
            |
            |Revisamos su cuenta y aun aparecen $count $word pendiente(s) por S/ ${"%.2f".format(total)}.
            |${if (oldest != null) "Periodo mas antiguo: $oldest." else ""}
            |Si ya realizo el pago, envie por este chat la foto o captura del voucher (Yape, Plin o BCP) para validarlo y actualizar su cuenta.
        """.trimMargin().replace(Regex("\n{3,}"), "\n\n")

        return buildHumanFollowUpClientMessage(
            inHoursText = "$base\n\nNuestro equipo lo revisara en breve.$cutOffLine".trimEnd(),
            afterHoursLead = "$base$cutOffLine".trimEnd(),
            afterHoursFollowUp = "En este momento estamos fuera de horario laboral. Un asesor lo revisara a primera hora.",
            now = now
        )
    }

    fun buildVoucherReceivedResponse(now: LocalDateTime? = null): String {
        return buildHumanFollowUpClientMessage(
            inHoursText = "Recibimos su comprobante. Gracias. Nuestro equipo lo revisara en breve y le confirmaremos por este chat.",
            afterHoursLead = "Recibimos su comprobante. Gracias.",
            afterHoursFollowUp = "En este momento estamos fuera de horario laboral. Un asesor lo revisara a primera hora.",
            now = now
        )
    }

    fun buildReceiptPendingAckResponse(now: LocalDateTime? = null): String {
        return buildHumanFollowUpClientMessage(
            inHoursText = "Ya tenemos su comprobante en revision. Un asesor le confirmara en breve. Gracias.",
            afterHoursLead = "Ya tenemos su comprobante en revision. Gracias.",
            afterHoursFollowUp = "En este momento estamos fuera de horario laboral. Un asesor lo confirmara a primera hora.",
            now = now
        )
    }

    fun markInboundAsRead(inbound: WhatsAppInboundMessage): Boolean {
        val success = try {
            whatsAppService.markMessageAsRead(inbound.metaMessageId).success
        } catch (e: Exception) {
            log.warn("Conversation: mark-read fallo para ${inbound.metaMessageId}: ${e.message}")
            false
        }
        inboundMessageRepository.save(inbound.copy(readAt = LocalDateTime.now()))
        return success
    }

    fun markInboundAsRead(metaMessageId: String): Boolean {
        val inbound = inboundMessageRepository.findByMetaMessageId(metaMessageId)
            ?: return try {
                whatsAppService.markMessageAsRead(metaMessageId).success
            } catch (e: Exception) {
                log.warn("Conversation: mark-read fallo para $metaMessageId: ${e.message}")
                false
            }
        return markInboundAsRead(inbound)
    }

    fun markAllRead(phone: String): WhatsAppMarkAllReadResultDto {
        val unread = PeruvianWhatsAppPhone.queryVariants(phone)
            .flatMap { variant -> inboundMessageRepository.findByPhoneAndReadAtIsNull(variant) }
            .distinctBy { it.id }
        if (unread.isEmpty()) {
            return WhatsAppMarkAllReadResultDto(phone = phone, markedCount = 0, success = true)
        }

        val now = LocalDateTime.now()
        var allMetaOk = true
        val latest = unread.maxByOrNull { it.createdAt }
        if (latest != null) {
            try {
                if (!whatsAppService.markMessageAsRead(latest.metaMessageId).success) {
                    allMetaOk = false
                }
            } catch (e: Exception) {
                allMetaOk = false
                log.warn("Conversation: mark-all-read fallo para ${latest.metaMessageId}: ${e.message}")
            }
        }

        val ids = unread.mapNotNull { it.id }
        inboundMessageRepository.markReadByIds(ids, now)

        return WhatsAppMarkAllReadResultDto(
            phone = phone,
            markedCount = unread.size,
            success = allMetaOk
        )
    }

    fun sendOperatorReply(
        phone: String,
        text: String,
        operatorUsername: String?,
        agentId: Int? = null,
        isAdmin: Boolean = false,
        replyToMessageId: String? = null
    ): WhatsAppThreadMessageDto {
        val trimmed = text.trim()
        if (trimmed.isBlank()) {
            throw IllegalArgumentException("El mensaje no puede estar vacio.")
        }
        crmConversationService.assertCanReply(phone = phone, agentId = agentId, isAdmin = isAdmin)
        val window = serviceWindowService.getServiceWindow(phone)
        if (!window.open) {
            throw IllegalArgumentException("La ventana de servicio de 24h esta cerrada. Solo se pueden enviar plantillas.")
        }

        val replyContext = resolveReplyContext(phone, replyToMessageId)
        val sendResult = whatsAppService.sendTextMessage(
            phoneNumber = phone,
            message = trimmed,
            contextMessageId = replyContext.metaMessageId
        )
        if (!sendResult.success) {
            throw Exception(sendResult.metaResponse.ifBlank { "No se pudo enviar el mensaje por WhatsApp." })
        }

        val metaMessageId = sendResult.metaMessageId?.takeIf { it.isNotBlank() }
            ?: WhatsAppMetaResponseParser.extractMessageId(sendResult.metaResponse)
        if (metaMessageId.isNullOrBlank()) {
            log.warn(
                "Operator reply sent without Meta wamid phone={} metaResponse={}",
                phone,
                sendResult.metaResponse.take(500),
            )
        }

        chatStateService.markWaitingForAdvisor(phone, "operator_reply")
        crmConversationService.touchOutbound(phone)

        val subscription = findSubscriptionByPhone(phone)
        val now = LocalDateTime.now()
        val saved = messageLogRepository.save(
            WhatsAppMessageLog(
                subscriptionId = subscription?.id,
                phone = PeruvianWhatsAppPhone.canonicalStoragePhone(phone),
                messageType = MESSAGE_TYPE_OPERATOR_REPLY,
                status = "SENT",
                metaMessageId = metaMessageId,
                message = trimmed,
                operatorUsername = operatorUsername,
                replyToLogId = replyContext.replyToLogId,
                sentAt = now,
                createdAt = now
            )
        )
        return saved.toEnrichedThreadMessage()
    }

    fun sendOperatorMedia(
        phone: String,
        bytes: ByteArray,
        mimeType: String?,
        filename: String?,
        caption: String?,
        operatorUsername: String?,
        agentId: Int? = null,
        isAdmin: Boolean = false,
        replyToMessageId: String? = null
    ): WhatsAppThreadMessageDto {
        crmConversationService.assertCanReply(phone = phone, agentId = agentId, isAdmin = isAdmin)
        val window = serviceWindowService.getServiceWindow(phone)
        if (!window.open) {
            throw IllegalArgumentException("La ventana de servicio de 24h esta cerrada. Solo se pueden enviar plantillas.")
        }
        val kind = WhatsAppMediaConstraints.validate(mimeType, bytes.size.toLong(), filename)
        val replyContext = resolveReplyContext(phone, replyToMessageId)
        val safeFilename = filename?.takeIf { it.isNotBlank() } ?: defaultFilename(kind, mimeType)
        val storedPath = mediaDownloadService.storeOutboundBytes(bytes, mimeType, safeFilename)

        val metaMediaId = try {
            whatsAppService.uploadMedia(bytes = bytes, mimeType = mimeType!!.trim(), filename = safeFilename)
        } catch (e: Exception) {
            persistFailedMediaLog(
                phone = phone,
                kind = kind,
                caption = caption,
                operatorUsername = operatorUsername,
                replyToLogId = replyContext.replyToLogId,
                storedPath = storedPath,
                mimeType = mimeType,
                filename = safeFilename,
                error = e.message
            )
            throw e
        }

        val sendResult = try {
            whatsAppService.sendMediaMessage(
                phoneNumber = phone,
                kind = kind,
                mediaId = metaMediaId,
                caption = caption,
                filename = safeFilename,
                contextMessageId = replyContext.metaMessageId
            )
        } catch (e: Exception) {
            persistFailedMediaLog(
                phone = phone,
                kind = kind,
                caption = caption,
                operatorUsername = operatorUsername,
                replyToLogId = replyContext.replyToLogId,
                storedPath = storedPath,
                mimeType = mimeType,
                filename = safeFilename,
                mediaMetaId = metaMediaId,
                error = e.message
            )
            throw e
        }

        if (!sendResult.success) {
            persistFailedMediaLog(
                phone = phone,
                kind = kind,
                caption = caption,
                operatorUsername = operatorUsername,
                replyToLogId = replyContext.replyToLogId,
                storedPath = storedPath,
                mimeType = mimeType,
                filename = safeFilename,
                mediaMetaId = metaMediaId,
                error = sendResult.metaResponse
            )
            throw Exception(sendResult.metaResponse.ifBlank { "No se pudo enviar el media por WhatsApp." })
        }

        chatStateService.markWaitingForAdvisor(phone, "operator_media")
        crmConversationService.touchOutbound(phone)

        val subscription = findSubscriptionByPhone(phone)
        val now = LocalDateTime.now()
        val preview = caption?.trim()?.takeIf { it.isNotEmpty() }
            ?: "[${kind.name}] ${safeFilename}"
        val metaMessageId = sendResult.metaMessageId?.takeIf { it.isNotBlank() }
            ?: WhatsAppMetaResponseParser.extractMessageId(sendResult.metaResponse)
        val saved = messageLogRepository.save(
            WhatsAppMessageLog(
                subscriptionId = subscription?.id,
                phone = PeruvianWhatsAppPhone.canonicalStoragePhone(phone),
                messageType = MESSAGE_TYPE_OPERATOR_MEDIA,
                status = "SENT",
                metaMessageId = metaMessageId,
                message = preview,
                operatorUsername = operatorUsername,
                replyToLogId = replyContext.replyToLogId,
                mediaMetaId = metaMediaId,
                mediaMimeType = mimeType?.trim(),
                mediaStoredPath = storedPath,
                mediaFilename = safeFilename,
                sentAt = now,
                createdAt = now
            )
        )
        return saved.toEnrichedThreadMessage()
    }

    fun sendOperatorTemplate(
        phone: String,
        templateCode: String,
        operatorUsername: String?,
        agentId: Int? = null,
        isAdmin: Boolean = false
    ): WhatsAppThreadMessageDto {
        // Assignee (e.g. SECRETARY) or ADMIN may send HSM templates from an owned thread.
        crmConversationService.assertCanReply(phone = phone, agentId = agentId, isAdmin = isAdmin)
        val definition = WhatsAppTemplateCatalog.getByCodeString(templateCode)
        val subscription = findSubscriptionByPhone(phone)
            ?: throw IllegalArgumentException("No hay suscripcion asociada al telefono para armar la plantilla.")

        val unpaid = unpaidPayments(subscription.id)
        val payment = unpaid.firstOrNull()
        val welcomeContext = if (definition.code == WhatsAppTemplateCode.WELCOME_CUSTOMER) {
            WelcomeVariableMapper.buildContext(subscription)
        } else {
            null
        }

        if (definition.code == WhatsAppTemplateCode.PAYMENT_REMINDER ||
            definition.code == WhatsAppTemplateCode.PAYMENT_VALIDATION
        ) {
            if (payment == null && definition.code == WhatsAppTemplateCode.PAYMENT_REMINDER) {
                throw IllegalArgumentException("No hay facturas pendientes para enviar el recordatorio.")
            }
        }
        if (definition.code == WhatsAppTemplateCode.SERVICE_CUT_NOTICE && payment == null) {
            throw IllegalArgumentException("No hay deuda pendiente para el aviso de corte.")
        }

        val saved = templateDeliveryService.deliverTemplate(
            definition = definition,
            subscription = subscription,
            phone = phone,
            payment = payment,
            oldestUnpaidPayment = payment,
            paymentId = payment?.id,
            subscriptionId = subscription.id,
            welcomeContext = welcomeContext,
            operatorUsername = operatorUsername
        )

        chatStateService.markWaitingForAdvisor(phone, "operator_template")
        crmConversationService.touchOutbound(phone)
        return saved.toEnrichedThreadMessage()
    }

    fun retryFailedOutbound(
        phone: String,
        logId: Int,
        operatorUsername: String?,
        agentId: Int? = null,
        isAdmin: Boolean = false
    ): WhatsAppThreadMessageDto {
        crmConversationService.assertCanReply(phone = phone, agentId = agentId, isAdmin = isAdmin)
        val existing = messageLogRepository.findById(logId).orElse(null)
            ?: throw IllegalArgumentException("Mensaje no encontrado.")
        if (existing.phone != phone) {
            throw IllegalArgumentException("El mensaje no pertenece a esta conversacion.")
        }
        if (!existing.status.equals("FAILED", ignoreCase = true)) {
            throw IllegalArgumentException("Solo se pueden reintentar mensajes fallidos.")
        }
        if (existing.retryCount >= WhatsAppMediaConstraints.MAX_RETRY_COUNT) {
            throw IllegalArgumentException("Se alcanzo el maximo de reintentos (${WhatsAppMediaConstraints.MAX_RETRY_COUNT}).")
        }

        return if (!existing.mediaStoredPath.isNullOrBlank()) {
            val path = mediaDownloadService.resolveStoredPath(existing.mediaStoredPath)
                ?: throw IllegalArgumentException("No se encontro el archivo local para reintentar.")
            val bytes = java.nio.file.Files.readAllBytes(path)
            val result = sendOperatorMedia(
                phone = phone,
                bytes = bytes,
                mimeType = existing.mediaMimeType,
                filename = existing.mediaFilename,
                caption = existing.message,
                operatorUsername = operatorUsername,
                agentId = agentId,
                isAdmin = isAdmin
            )
            existing.retryCount = existing.retryCount + 1
            messageLogRepository.save(existing)
            result
        } else {
            val text = existing.message?.trim().orEmpty()
            if (text.isBlank()) {
                throw IllegalArgumentException("No hay contenido para reintentar.")
            }
            val result = sendOperatorReply(
                phone = phone,
                text = text,
                operatorUsername = operatorUsername,
                agentId = agentId,
                isAdmin = isAdmin,
                replyToMessageId = existing.replyToLogId?.let { "outbound:$it" }
            )
            existing.retryCount = existing.retryCount + 1
            messageLogRepository.save(existing)
            result
        }
    }

    fun resolveReplyToLogId(contextMessageId: String?): Int? {
        if (contextMessageId.isNullOrBlank()) return null
        return messageLogRepository.findByMetaMessageId(contextMessageId)?.id
    }

    /**
     * Sends a Meta reaction to an inbound (customer) message and persists [agentReactionEmoji].
     * Empty [emoji] clears the reaction (Meta contract).
     */
    fun reactToInboundMessage(
        wamid: String,
        emoji: String,
        agentId: Int? = null,
        isAdmin: Boolean = false,
    ): WhatsAppThreadMessageDto {
        val targetWamid = wamid.trim()
        if (targetWamid.isBlank()) {
            throw IllegalArgumentException("message_id (wamid) es obligatorio.")
        }
        val inbound = inboundMessageRepository.findByMetaMessageId(targetWamid)
            ?: throw IllegalArgumentException("Mensaje no encontrado para reaccionar.")
        crmConversationService.assertCanReply(phone = inbound.phone, agentId = agentId, isAdmin = isAdmin)

        val sendResult = whatsAppService.sendReaction(
            phoneNumber = inbound.phone,
            wamid = targetWamid,
            emoji = emoji,
        )
        if (!sendResult.success) {
            throw Exception(sendResult.metaResponse.ifBlank { "No se pudo enviar la reaccion." })
        }

        inbound.agentReactionEmoji = emoji.takeIf { it.isNotBlank() }
        inboundMessageRepository.save(inbound)
        crmEventPublisher.publish(
            eventType = CrmEventPublisher.MESSAGE_REACTION,
            payload = mapOf(
                "phone" to inbound.phone,
                "threadMessageId" to "inbound:${inbound.id}",
                "metaMessageId" to targetWamid,
                "emoji" to emoji,
                "direction" to "OUTBOUND_REACTION",
            ),
        )
        return with(WhatsAppThreadMessageMapper) { inbound.toThreadMessage() }
    }

    /** Soft-edits outbound plain-text within the 15-minute window (local CRM only). */
    fun editOutboundMessage(
        wamid: String,
        text: String,
        agentId: Int? = null,
        isAdmin: Boolean = false,
    ): WhatsAppThreadMessageDto {
        val log = requireOutboundLog(wamid)
        val phone = log.phone?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("El mensaje saliente no tiene telefono.")
        crmConversationService.assertCanReply(phone = phone, agentId = agentId, isAdmin = isAdmin)
        if (log.deletedAt != null) {
            throw IllegalArgumentException("No se puede editar un mensaje eliminado.")
        }
        WhatsAppMessageMutationPolicy.requireEditableOutbound(
            direction = "OUTBOUND",
            messageType = log.messageType,
            createdAt = log.createdAt,
        )
        val trimmed = text.trim()
        if (trimmed.isBlank()) {
            throw IllegalArgumentException("El mensaje no puede estar vacio.")
        }
        if (log.originalMessage.isNullOrBlank()) {
            log.originalMessage = log.message
        }
        log.message = trimmed
        log.editedAt = LocalDateTime.now()
        val saved = messageLogRepository.save(log)
        publishMessageUpdated(saved)
        return saved.toEnrichedThreadMessage()
    }

    /** Soft-deletes outbound within the 24-hour window (local CRM only). */
    fun deleteOutboundMessage(
        wamid: String,
        agentId: Int? = null,
        isAdmin: Boolean = false,
    ): WhatsAppThreadMessageDto {
        val log = requireOutboundLog(wamid)
        val phone = log.phone?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("El mensaje saliente no tiene telefono.")
        crmConversationService.assertCanReply(phone = phone, agentId = agentId, isAdmin = isAdmin)
        if (log.deletedAt != null) {
            return log.toEnrichedThreadMessage()
        }
        WhatsAppMessageMutationPolicy.requireDeletableOutbound(
            direction = "OUTBOUND",
            createdAt = log.createdAt,
        )
        log.deletedAt = LocalDateTime.now()
        val saved = messageLogRepository.save(log)
        publishMessageUpdated(saved)
        return saved.toEnrichedThreadMessage()
    }

    private fun requireOutboundLog(messageKey: String): WhatsAppMessageLog {
        val key = messageKey.trim()
        if (key.isBlank()) {
            throw IllegalArgumentException("message_id (wamid o id local) es obligatorio.")
        }

        // 1) Prefer Meta wamid on outbound log
        messageLogRepository.findByMetaMessageId(key)?.let { return it }

        // 2) Local PK on outbound log (outbound:N or numeric string → Int)
        val outboundMatch = Regex("^outbound:(\\d+)$", RegexOption.IGNORE_CASE).matchEntire(key)
        val localId: Int? = when {
            outboundMatch != null -> outboundMatch.groupValues[1].toIntOrNull()
            key.all { it.isDigit() } -> key.toIntOrNull()
            else -> null
        }
        if (localId != null) {
            messageLogRepository.findById(localId).orElse(null)?.let { return it }
            // Also probe inbound table for diagnostics when key is a local id
            val inboundById = inboundMessageRepository.findById(localId).orElse(null)
            if (inboundById != null) {
                log.info(
                    "Mutación: key={} existe en whatsapp_inbound_message id={} pero no en whatsapp_message_log",
                    key,
                    localId,
                )
            }
        } else if (!key.startsWith("wamid.", ignoreCase = true)) {
            // Non-wamid / non-numeric key: still probe inbound by meta id
            inboundMessageRepository.findByMetaMessageId(key)?.let { inbound ->
                log.info(
                    "Mutación: key={} existe en whatsapp_inbound_message id={} pero no en whatsapp_message_log",
                    key,
                    inbound.id,
                )
            }
        } else {
            inboundMessageRepository.findByMetaMessageId(key)?.let { inbound ->
                log.info(
                    "Mutación: key={} es wamid inbound id={} sin registro outbound local",
                    key,
                    inbound.id,
                )
            }
        }

        log.warn("Mensaje no encontrado con identifier: {}", key)
        throw IllegalArgumentException(WhatsAppMessageMutationPolicy.NO_LOCAL_OUTBOUND_RECORD)
    }

    private fun publishMessageUpdated(log: WhatsAppMessageLog) {
        crmEventPublisher.publish(
            eventType = CrmEventPublisher.MESSAGE_UPDATED,
            payload = mapOf(
                "phone" to log.phone,
                "threadMessageId" to "outbound:${log.id}",
                "metaMessageId" to log.metaMessageId,
                "body" to if (log.deletedAt != null) null else log.message,
                "editedAt" to log.editedAt?.toString(),
                "deletedAt" to log.deletedAt?.toString(),
                "deliveryStatus" to if (log.deletedAt != null) "DELETED" else log.deliveryStatus,
                "reactionEmoji" to log.customerReactionEmoji,
            ),
        )
    }

    private data class ReplyContext(
        val metaMessageId: String?,
        val replyToLogId: Int?
    )

    private fun resolveReplyContext(phone: String, replyToMessageId: String?): ReplyContext {
        if (replyToMessageId.isNullOrBlank()) {
            return ReplyContext(null, null)
        }
        val trimmed = replyToMessageId.trim()
        when {
            trimmed.startsWith("inbound:") -> {
                val id = trimmed.removePrefix("inbound:").toIntOrNull()
                    ?: throw IllegalArgumentException("replyToMessageId invalido.")
                val inbound = inboundMessageRepository.findById(id).orElse(null)
                    ?: throw IllegalArgumentException("Mensaje de referencia no encontrado.")
                if (inbound.phone != phone) {
                    throw IllegalArgumentException("El mensaje de referencia no pertenece a esta conversacion.")
                }
                return ReplyContext(
                    metaMessageId = inbound.metaMessageId.takeIf { it.isNotBlank() },
                    replyToLogId = inbound.replyToLogId ?: inbound.id
                )
            }
            trimmed.startsWith("outbound:") -> {
                val id = trimmed.removePrefix("outbound:").toIntOrNull()
                    ?: throw IllegalArgumentException("replyToMessageId invalido.")
                val outbound = messageLogRepository.findById(id).orElse(null)
                    ?: throw IllegalArgumentException("Mensaje de referencia no encontrado.")
                if (outbound.phone != phone) {
                    throw IllegalArgumentException("El mensaje de referencia no pertenece a esta conversacion.")
                }
                return ReplyContext(
                    metaMessageId = outbound.metaMessageId,
                    replyToLogId = outbound.id
                )
            }
            else -> {
                val byMeta = messageLogRepository.findByMetaMessageId(trimmed)
                if (byMeta != null) {
                    if (byMeta.phone != phone) {
                        throw IllegalArgumentException("El mensaje de referencia no pertenece a esta conversacion.")
                    }
                    return ReplyContext(byMeta.metaMessageId, byMeta.id)
                }
                val inbound = inboundMessageRepository.findByMetaMessageId(trimmed)
                    ?: throw IllegalArgumentException("Mensaje de referencia no encontrado.")
                if (inbound.phone != phone) {
                    throw IllegalArgumentException("El mensaje de referencia no pertenece a esta conversacion.")
                }
                return ReplyContext(inbound.metaMessageId, inbound.replyToLogId ?: inbound.id)
            }
        }
    }

    private fun persistFailedMediaLog(
        phone: String,
        kind: WhatsAppOutboundMediaKind,
        caption: String?,
        operatorUsername: String?,
        replyToLogId: Int?,
        storedPath: String?,
        mimeType: String?,
        filename: String?,
        mediaMetaId: String? = null,
        error: String?
    ): WhatsAppMessageLog {
        val subscription = findSubscriptionByPhone(phone)
        val now = LocalDateTime.now()
        return messageLogRepository.save(
            WhatsAppMessageLog(
                subscriptionId = subscription?.id,
                phone = PeruvianWhatsAppPhone.canonicalStoragePhone(phone),
                messageType = MESSAGE_TYPE_OPERATOR_MEDIA,
                status = "FAILED",
                message = caption?.trim()?.takeIf { it.isNotEmpty() } ?: "[${kind.name}] ${filename.orEmpty()}",
                operatorUsername = operatorUsername,
                replyToLogId = replyToLogId,
                mediaMetaId = mediaMetaId,
                mediaMimeType = mimeType?.trim(),
                mediaStoredPath = storedPath,
                mediaFilename = filename,
                errorMessage = error?.take(1000),
                failedAt = now,
                createdAt = now
            )
        )
    }

    private fun defaultFilename(kind: WhatsAppOutboundMediaKind, mimeType: String?): String {
        val ext = when (mimeType?.lowercase()) {
            "image/jpeg", "image/jpg" -> "jpg"
            "image/png" -> "png"
            "application/pdf" -> "pdf"
            "audio/ogg" -> "ogg"
            "audio/mpeg" -> "mp3"
            "audio/mp4" -> "m4a"
            "audio/aac" -> "aac"
            "audio/amr" -> "amr"
            else -> "bin"
        }
        return "${kind.name.lowercase()}.$ext"
    }

    fun findSubscriptionByPhone(phone: String): Subscription? {
        val digits = phone.filter { it.isDigit() }
        val normalized = when {
            digits.length == 11 && digits.startsWith("51") -> digits.substring(2)
            digits.length == 9 && digits.startsWith("9") -> digits
            else -> digits
        }
        return subscriptionRepository.findByNormalizedPhone(normalized).firstOrNull()
    }

    fun hasRecentOperatorReply(phone: String): Boolean {
        val minutes = whatsAppProperties.autoReply.operatorSilenceMinutes.coerceAtLeast(1)
        val since = LocalDateTime.now().minusMinutes(minutes.toLong())
        return messageLogRepository.existsByPhoneAndMessageTypeAndCreatedAtAfter(
            phone = phone,
            messageType = MESSAGE_TYPE_OPERATOR_REPLY,
            createdAt = since
        )
    }

    fun isInboundBurst(phone: String): Boolean {
        val seconds = whatsAppProperties.autoReply.inboundBurstSeconds.coerceAtLeast(1)
        val since = LocalDateTime.now().minusSeconds(seconds.toLong())
        // count includes the message currently being saved if already persisted
        return inboundMessageRepository.countByPhoneAndCreatedAtAfter(phone, since) >= 2
    }

    private fun buildMainMenuBody(includeGreeting: Boolean): String {
        return if (includeGreeting) {
            "👋 Hola. Le atiende el asistente virtual de GigaFiber. ¿En que podemos ayudarle hoy?"
        } else {
            "Seleccione una opcion para continuar 👇"
        }
    }

    private fun sendMainMenuInternal(
        phone: String,
        includeGreeting: Boolean,
        contextMessageId: String?
    ): AutoReplyResult {
        return sendInteractiveOptions(
            phone = phone,
            bodyText = buildMainMenuBody(includeGreeting),
            options = buildAutoReplyButtons(null),
            marker = "[MAIN_MENU] ",
            currentStep = WhatsAppConversationStep.MAIN_MENU,
            footerText = WhatsAppBotMenuCatalog.GLOBAL_COMMANDS_FOOTER,
            contextMessageId = contextMessageId,
            descriptions = WhatsAppBotMenuCatalog.mainMenuDescriptions
        )
    }

    private fun buildAdvisorClosureMessage(kind: WhatsAppHandoffCopyKind): String {
        val phones = whatsAppProperties.autoReply.secretaryPhoneList().joinToString(" / ")
        return """
            |✅ ${handoffRegisteredLead(kind)} A partir de ahora le atiende una persona de nuestro equipo por este mismo chat. ⏱️
            |
            |Tambien puede llamar a Secretaria: 📞 $phones
        """.trimMargin()
    }

    fun buildAfterHoursHandoffMessage(
        kind: WhatsAppHandoffCopyKind = WhatsAppHandoffCopyKind.ADVISOR_QUEUE
    ): String {
        val base = whatsAppProperties.autoReply.afterHoursMessage.trim()
        return """
            |✅ ${handoffRegisteredLead(kind)}
            |
            |$base
            |
            |${businessHoursSentence()}
        """.trimMargin()
    }

    fun buildAfterHoursAckMessage(): String {
        return """
            |Recibido, gracias.
            |
            |En este momento estamos fuera de horario laboral. Le responderemos a primera hora.
            |
            |${businessHoursSentence()}
        """.trimMargin()
    }

    private fun handoffRegisteredLead(kind: WhatsAppHandoffCopyKind): String {
        return when (kind) {
            WhatsAppHandoffCopyKind.ADVISOR_QUEUE -> "Su solicitud quedo registrada."
            WhatsAppHandoffCopyKind.SUPPORT_CASE -> "Su caso quedo registrado."
            WhatsAppHandoffCopyKind.INSTALLATION -> "Su solicitud de instalacion quedo registrada."
        }
    }

    private fun withGlobalNavigation(
        options: List<WhatsAppService.InteractiveButtonOption>
    ): List<WhatsAppService.InteractiveButtonOption> {
        return options + listOf(
            WhatsAppService.InteractiveButtonOption(BUTTON_BACK, "⬅️ Volver"),
            WhatsAppService.InteractiveButtonOption(BUTTON_HOME, "🏠 Menú inicio"),
            WhatsAppService.InteractiveButtonOption(BUTTON_ADVISOR, "🙋 Asesor")
        )
    }

    private fun sendInteractiveOptions(
        phone: String,
        bodyText: String,
        options: List<WhatsAppService.InteractiveButtonOption>,
        marker: String,
        currentStep: WhatsAppConversationStep? = null,
        stepMetadata: String? = null,
        footerText: String? = null,
        contextMessageId: String? = null,
        descriptions: Map<String, String> = emptyMap()
    ): AutoReplyResult {
        return try {
            val result = if (options.size <= 3) {
                whatsAppService.sendInteractiveReplyButtons(
                    phoneNumber = phone,
                    bodyText = bodyText,
                    buttons = options,
                    footerText = footerText,
                    contextMessageId = contextMessageId
                )
            } else {
                whatsAppService.sendInteractiveListMessage(
                    phoneNumber = phone,
                    bodyText = bodyText,
                    buttonText = "Ver opciones",
                    sectionTitle = "Opciones",
                    rows = options.map {
                        WhatsAppService.InteractiveListOption(
                            id = it.id,
                            title = it.title,
                            description = descriptions[it.id]
                        )
                    },
                    footerText = footerText,
                    contextMessageId = contextMessageId
                )
            }
            if (result.success && currentStep != null) {
                chatStateService.setCurrentStep(phone, currentStep, stepMetadata)
            }
            AutoReplyResult(
                success = result.success,
                messageText = marker + bodyText,
                metaMessageId = result.metaMessageId
            )
        } catch (e: Exception) {
            log.warn("Conversation: no se pudo enviar opciones interactivas: ${e.message}")
            AutoReplyResult(success = false, messageText = marker + bodyText, metaMessageId = null)
        }
    }

    private fun buildSupportEntryMenu(subscription: Subscription?): String {
        val profile = supportProfile(subscription)
        val options = withGlobalNavigation(supportEntryButtons(profile)).joinToString("\n") { "• ${it.title}" }
        return "[SUPPORT_MENU:${profile.code}]\n${supportEntryBody(profile)}\n$options"
    }

    private fun supportEntryBody(profile: SupportProfile): String {
        return when (profile) {
            SupportProfile.COMBO -> "Seleccione que servicio presenta el problema:"
            SupportProfile.CABLE_ONLY,
            SupportProfile.INTERNET_ONLY -> "Seleccione el problema que esta teniendo:"
        }
    }

    private fun supportEntryButtons(profile: SupportProfile): List<WhatsAppService.InteractiveButtonOption> {
        return when (profile) {
            SupportProfile.COMBO -> listOf(
                WhatsAppService.InteractiveButtonOption("${ISSUE_BUTTON_PREFIX}internet", "📶 Internet"),
                WhatsAppService.InteractiveButtonOption("${ISSUE_BUTTON_PREFIX}cable", "📺 TV Cable"),
                WhatsAppService.InteractiveButtonOption("${ISSUE_BUTTON_PREFIX}both", "🛠️ Ambos servicios")
            )
            SupportProfile.INTERNET_ONLY -> listOf(
                WhatsAppService.InteractiveButtonOption("${ISSUE_BUTTON_PREFIX}no_internet", "📶 Sin Internet"),
                WhatsAppService.InteractiveButtonOption("${ISSUE_BUTTON_PREFIX}slow_internet", "⚡ Internet lento")
            )
            SupportProfile.CABLE_ONLY -> listOf(
                WhatsAppService.InteractiveButtonOption("${ISSUE_BUTTON_PREFIX}tv_no_signal", "📺 Sin señal"),
                WhatsAppService.InteractiveButtonOption("${ISSUE_BUTTON_PREFIX}tv_mosaic", "🧩 Imagen congelada")
            )
        }
    }

    private fun handleSupportMenuSelection(subscription: Subscription?, messageText: String?): String {
        val state = supportProfile(subscription)
        val option = normalizedOption(messageText)
        val issue = supportIssueFor(state, option)
            ?: return invalidInteractiveSelectionText()

        val question = diagnosticQuestion(subscription, issue)
        return """
            |[SUPPORT_DIAG:${issue.code}]
            |${question.text}
            |${withGlobalNavigation(question.buttons).joinToString("\n") { "• ${it.title}" }}
        """.trimMargin()
    }

    private fun closeSupportDiagnosticFromCurrentStep(messageText: String?): String {
        normalizeDiagnosticAnswer(messageText)
            ?: return invalidInteractiveSelectionText()

        return """
            |[SUPPORT_CLOSED:ESPERANDO_ASESOR]
            |${buildHumanHandoffClientMessage(kind = WhatsAppHandoffCopyKind.SUPPORT_CASE)}
        """.trimMargin()
    }

    private fun sendInteractiveSupportReply(
        phone: String,
        bodyText: String,
        buttons: List<WhatsAppService.InteractiveButtonOption>,
        marker: String
    ): AutoReplyResult {
        return try {
            val result = whatsAppService.sendInteractiveReplyButtons(
                phoneNumber = phone,
                bodyText = bodyText,
                buttons = buttons
            )
            AutoReplyResult(
                success = result.success,
                messageText = marker + bodyText,
                metaMessageId = result.metaMessageId
            )
        } catch (e: Exception) {
            log.warn("Conversation: no se pudo enviar flujo interactivo de soporte: ${e.message}")
            AutoReplyResult(success = false, messageText = marker + bodyText, metaMessageId = null)
        }
    }

    private fun sendTextSupportReply(
        phone: String,
        bodyText: String,
        marker: String
    ): AutoReplyResult {
        return try {
            val result = whatsAppService.sendTextMessage(phoneNumber = phone, message = bodyText)
            AutoReplyResult(
                success = result.success,
                messageText = marker + bodyText,
                metaMessageId = result.metaMessageId
            )
        } catch (e: Exception) {
            log.warn("Conversation: no se pudo cerrar flujo de soporte: ${e.message}")
            AutoReplyResult(success = false, messageText = marker + bodyText, metaMessageId = null)
        }
    }

    private fun invalidInteractiveSelection(phone: String): AutoReplyResult {
        return sendTextSupportReply(
            phone = phone,
            bodyText = invalidInteractiveSelectionText(),
            marker = "[SUPPORT_INVALID] "
        )
    }

    private fun diagnosticQuestion(subscription: Subscription?, issue: SupportIssue): DiagnosticQuestion {
        return when {
            issue.service == SupportService.INTERNET && isFiber(subscription) -> DiagnosticQuestion(
                text = "Por favor revise su modem. ¿De que color ve la luz del indicador LOS o PON en el frente?",
                buttons = listOf(
                    WhatsAppService.InteractiveButtonOption("${DIAG_BUTTON_PREFIX}fiber_red", "🔴 Luz roja"),
                    WhatsAppService.InteractiveButtonOption("${DIAG_BUTTON_PREFIX}fiber_green", "🟢 Verde / azul"),
                    WhatsAppService.InteractiveButtonOption("${DIAG_BUTTON_PREFIX}fiber_off", "⚪ Sin luz")
                )
            )
            issue.service == SupportService.INTERNET && isCoaxial(subscription) -> DiagnosticQuestion(
                text = "Por favor revise su modem. ¿La luz de ONLINE o INTERNET esta encendida fija?",
                buttons = listOf(
                    WhatsAppService.InteractiveButtonOption("${DIAG_BUTTON_PREFIX}coax_fixed", "🟢 Sí, está fija"),
                    WhatsAppService.InteractiveButtonOption("${DIAG_BUTTON_PREFIX}coax_blink", "🔴 No / Parpadea")
                )
            )
            issue.service == SupportService.CABLE -> DiagnosticQuestion(
                text = "¿Que pantalla o mensaje ve en su televisor?",
                buttons = listOf(
                    WhatsAppService.InteractiveButtonOption("${DIAG_BUTTON_PREFIX}tv_black", "📺 Pantalla negra"),
                    WhatsAppService.InteractiveButtonOption("${DIAG_BUTTON_PREFIX}tv_error", "⚠️ Código error")
                )
            )
            else -> DiagnosticQuestion(
                text = "Por favor revise su modem. ¿De que color ve la luz del indicador LOS o PON en el frente?",
                buttons = listOf(
                    WhatsAppService.InteractiveButtonOption("${DIAG_BUTTON_PREFIX}fiber_red", "🔴 Luz roja"),
                    WhatsAppService.InteractiveButtonOption("${DIAG_BUTTON_PREFIX}fiber_green", "🟢 Verde / azul"),
                    WhatsAppService.InteractiveButtonOption("${DIAG_BUTTON_PREFIX}fiber_off", "⚪ Sin luz")
                )
            )
        }
    }

    private fun normalizedOption(messageText: String?): Int? {
        val normalized = WhatsAppInboundIntentRouter.normalize(messageText) ?: return null
        return when {
            normalized == "1" || normalized == "a" -> 1
            normalized == "2" || normalized == "b" -> 2
            normalized == "3" || normalized == "c" -> 3
            normalized.contains("internet") -> 1
            normalized.contains("cable") || normalized.contains("tv") || normalized.contains("senal") -> 2
            normalized.contains("ambos") -> 3
            normalized.contains("lento") -> 2
            normalized.contains("wifi") || normalized.contains("wi fi") -> 3
            else -> null
        }
    }

    private fun normalizeDiagnosticAnswer(messageText: String?): String? {
        val normalized = WhatsAppInboundIntentRouter.normalize(messageText) ?: return null
        return when {
            normalized == "a" || normalized == "1" -> "A"
            normalized == "b" || normalized == "2" -> "B"
            normalized == "c" || normalized == "3" -> "C"
            normalized.length >= 2 -> messageText?.trim()?.take(500)
            else -> null
        }
    }

    private fun supportIssueFor(profile: SupportProfile, option: Int?): SupportIssue? {
        return when (profile) {
            SupportProfile.COMBO -> when (option) {
                1 -> SupportIssue.INTERNET_INTERRUPTION
                2 -> SupportIssue.CABLE_INTERRUPTION
                3 -> SupportIssue.BOTH_SERVICES
                else -> null
            }
            SupportProfile.INTERNET_ONLY -> when (option) {
                1 -> SupportIssue.NO_INTERNET
                2 -> SupportIssue.SLOW_INTERNET
                3 -> SupportIssue.WIFI_NOT_VISIBLE
                else -> null
            }
            SupportProfile.CABLE_ONLY -> when (option) {
                1 -> SupportIssue.TV_NO_SIGNAL
                2 -> SupportIssue.TV_INTERFERENCE
                3 -> SupportIssue.DECODER_ERROR
                else -> null
            }
        }
    }

    private fun supportIssueForButton(profile: SupportProfile, buttonReplyId: String?): SupportIssue? {
        val issueKey = buttonReplyId
            ?.takeIf { it.startsWith(ISSUE_BUTTON_PREFIX) }
            ?.removePrefix(ISSUE_BUTTON_PREFIX)
            ?: return null
        return when (profile) {
            SupportProfile.COMBO -> when (issueKey) {
                "internet" -> SupportIssue.INTERNET_INTERRUPTION
                "cable" -> SupportIssue.CABLE_INTERRUPTION
                "both" -> SupportIssue.BOTH_SERVICES
                else -> null
            }
            SupportProfile.INTERNET_ONLY -> when (issueKey) {
                "no_internet" -> SupportIssue.NO_INTERNET
                "slow_internet" -> SupportIssue.SLOW_INTERNET
                else -> null
            }
            SupportProfile.CABLE_ONLY -> when (issueKey) {
                "tv_no_signal" -> SupportIssue.TV_NO_SIGNAL
                "tv_mosaic" -> SupportIssue.TV_INTERFERENCE
                else -> null
            }
        }
    }

    private fun supportProfile(subscription: Subscription?): SupportProfile {
        val planName = subscription?.plan?.name.orEmpty().lowercase()
        val isComboPlan = listOf("combo", "duo", "internet + tv", "internet tv", "cable + internet")
            .any { planName.contains(it) }
        if (isComboPlan) return SupportProfile.COMBO
        return when (subscription?.installationType ?: subscription?.plan?.type) {
            InstallationType.ONLY_TV_FIBER -> SupportProfile.CABLE_ONLY
            else -> SupportProfile.INTERNET_ONLY
        }
    }

    private fun isFiber(subscription: Subscription?): Boolean {
        return subscription?.installationType == InstallationType.FIBER ||
            subscription?.plan?.type == InstallationType.FIBER ||
            subscription?.fiberOnu != null
    }

    private fun isCoaxial(subscription: Subscription?): Boolean {
        val planName = subscription?.plan?.name.orEmpty().lowercase()
        return listOf("coaxial", "hfc", "cablemodem", "cable modem")
            .any { planName.contains(it) }
    }

    private enum class SupportProfile(val code: String) {
        INTERNET_ONLY("SOLO_INTERNET"),
        CABLE_ONLY("SOLO_CABLE"),
        COMBO("COMBO")
    }

    private enum class SupportService {
        INTERNET,
        CABLE,
        BOTH
    }

    private enum class SupportIssue(
        val code: String,
        val service: SupportService
    ) {
        NO_INTERNET("NO_INTERNET", SupportService.INTERNET),
        SLOW_INTERNET("SLOW_INTERNET", SupportService.INTERNET),
        WIFI_NOT_VISIBLE("WIFI_NOT_VISIBLE", SupportService.INTERNET),
        INTERNET_INTERRUPTION("INTERNET_INTERRUPTION", SupportService.INTERNET),
        CABLE_INTERRUPTION("CABLE_INTERRUPTION", SupportService.CABLE),
        BOTH_SERVICES("BOTH_SERVICES", SupportService.BOTH),
        TV_NO_SIGNAL("TV_NO_SIGNAL", SupportService.CABLE),
        TV_INTERFERENCE("TV_INTERFERENCE", SupportService.CABLE),
        DECODER_ERROR("DECODER_ERROR", SupportService.CABLE);

        companion object {
            fun byCode(code: String?): SupportIssue? = values().firstOrNull { it.code == code }
        }
    }

    private data class DiagnosticQuestion(
        val text: String,
        val buttons: List<WhatsAppService.InteractiveButtonOption>
    )

    private fun accountNotFoundMessage(): String {
        val phones = whatsAppProperties.autoReply.secretaryPhoneList()
            .joinToString("\n") { "• $it" }
        return """
            |No encontramos una cuenta asociada a este numero. Por favor comuniquese con Secretaria:
            |$phones
        """.trimMargin()
    }

    private fun unpaidPayments(subscriptionId: Int?): List<Payment> {
        if (subscriptionId == null) return emptyList()
        return paymentRepository.findUnpaidBySubscriptionIdOrderByBillingDateDatetimeAsc(subscriptionId)
    }

    private fun formatBillingPeriod(payment: Payment): String {
        val date = payment.billingDateDatetime
        val month = date.month.getDisplayName(TextStyle.FULL, Locale("es", "PE"))
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("es", "PE")) else it.toString() }
        return "$month ${date.year}"
    }

    private fun firstNameOf(subscription: Subscription?): String? {
        return subscription?.firstName?.trim()?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
            ?.substringBefore(" ")
    }

    private fun clientFullName(subscription: Subscription): String {
        return listOf(subscription.firstName, subscription.lastName)
            .mapNotNull { it?.trim()?.takeIf { value -> value.isNotBlank() && !value.equals("null", ignoreCase = true) } }
            .joinToString(" ")
            .ifBlank { "cliente" }
    }

    private fun WhatsAppMessageLog.toEnrichedThreadMessage(): WhatsAppThreadMessageDto =
        operatorDisplayNameResolver.enrichMessage(toThreadMessage(templateDisplayService))

    data class AutoReplyResult(
        val success: Boolean,
        val messageText: String,
        val metaMessageId: String?
    )

    companion object {
        const val BUTTON_DEBT = WhatsAppBotMenuCatalog.DEBT
        const val BUTTON_PAID = WhatsAppBotMenuCatalog.PAID
        const val BUTTON_PAYMENT_PROOF = WhatsAppBotMenuCatalog.PAYMENT_PROOF
        const val BUTTON_SUPPORT = WhatsAppBotMenuCatalog.SUPPORT_LEGACY
        const val BUTTON_REPORT_FAULT = WhatsAppBotMenuCatalog.REPORT_FAULT
        const val BUTTON_INSTALLATION = WhatsAppBotMenuCatalog.INSTALLATION_LEGACY
        const val BUTTON_ADVISOR = WhatsAppBotMenuCatalog.ADVISOR
        const val BUTTON_BACK = WhatsAppBotMenuCatalog.BACK
        const val BUTTON_HOME = WhatsAppBotMenuCatalog.HOME
        const val MESSAGE_TYPE_AUTO_REPLY = "AUTO_REPLY"
        const val MESSAGE_TYPE_OPERATOR_REPLY = "OPERATOR_REPLY"
        const val MESSAGE_TYPE_OPERATOR_MEDIA = "OPERATOR_MEDIA"
        private const val ISSUE_BUTTON_PREFIX = WhatsAppBotMenuCatalog.SUPPORT_ISSUE_PREFIX
        private const val DIAG_BUTTON_PREFIX = WhatsAppBotMenuCatalog.SUPPORT_DIAG_PREFIX
        private val ISSUE_META_REGEX = Regex("""(?:^|;)issue=([A-Z0-9_]+)""")
    }
}
