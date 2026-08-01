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
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppConversationQueryService.Companion.toThreadMessage
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
    private val chatStateService: WhatsAppChatStateService
) {

    private val log = LoggerFactory.getLogger(WhatsAppConversationService::class.java)

    fun buildAutoReplyButtons(subscription: Subscription?): List<WhatsAppService.InteractiveButtonOption> {
        return listOf(
            WhatsAppService.InteractiveButtonOption(BUTTON_REPORT_FAULT, "Reportar averia"),
            WhatsAppService.InteractiveButtonOption(BUTTON_DEBT, "Ver deuda"),
            WhatsAppService.InteractiveButtonOption(BUTTON_ADVISOR, "Asesor")
        )
    }

    fun buildGreetingBody(subscription: Subscription?): String {
        return buildMainMenuBody(includeGreeting = true)
    }

    fun buildAckResponse(subscription: Subscription?): String {
        return invalidInteractiveSelectionText()
    }

    fun sendAutoReplyWithButtons(phone: String, subscription: Subscription?): AutoReplyResult {
        return sendMainMenu(phone, subscription)
    }

    fun sendMainMenu(phone: String, subscription: Subscription?): AutoReplyResult {
        return sendMainMenuInternal(phone, includeGreeting = false)
    }

    fun sendMainMenu(
        phone: String,
        subscription: Subscription?,
        includeGreeting: Boolean
    ): AutoReplyResult {
        return sendMainMenuInternal(phone, includeGreeting = includeGreeting)
    }

    fun sendMainMenuForNavigation(phone: String, subscription: Subscription?): AutoReplyResult {
        return sendMainMenuInternal(phone, includeGreeting = false)
    }

    fun sendBackNavigation(phone: String, subscription: Subscription?): AutoReplyResult {
        return if (chatStateService.currentStep(phone) == WhatsAppConversationStep.SUPPORT_DIAG) {
            sendSupportEntryMenu(phone, subscription)
        } else {
            sendMainMenuForNavigation(phone, subscription)
        }
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
            BUTTON_INSTALLATION -> buildInstallationHandoverResponse()
            BUTTON_ADVISOR -> handleHumanEscalation(
                subscription = subscription,
                phone = phone,
                messageText = sourceText,
            )
            else -> handleSupportOrTechnicalIssue(
                subscription = subscription,
                phone = phone,
                messageText = sourceText,
                fromButton = true
            )
        }
    }

    fun hasPendingSupportDiagnostic(phone: String): Boolean {
        return chatStateService.hasPendingSupportDiagnostic(phone)
    }

    fun hasPendingInteractiveMenu(phone: String): Boolean {
        return chatStateService.hasPendingInteractiveMenu(phone)
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
        return buildAdvisorClosureMessage()
    }

    fun buildInstallationHandoverResponse(): String {
        return buildAdvisorClosureMessage()
    }

    fun handleSupportOrTechnicalIssue(
        subscription: Subscription?,
        phone: String,
        messageText: String?,
        fromButton: Boolean
    ): String {
        return buildSupportEntryMenu(subscription)
    }

    fun sendSupportEntryMenu(phone: String, subscription: Subscription?): AutoReplyResult {
        val profile = supportProfile(subscription)
        val bodyText = supportEntryBody(profile)
        return sendInteractiveOptions(
            phone = phone,
            bodyText = bodyText,
            options = withGlobalNavigation(supportEntryButtons(profile)),
            marker = "[SUPPORT_MENU:${profile.code}] ",
            currentStep = WhatsAppConversationStep.SUPPORT_MENU
        )
    }

    fun sendSupportDiagnosticQuestion(
        phone: String,
        subscription: Subscription?,
        buttonReplyId: String?
    ): AutoReplyResult {
        val profile = supportProfile(subscription)
        val issue = supportIssueForButton(profile, buttonReplyId)
            ?: return invalidInteractiveSelection(phone)
        val question = diagnosticQuestion(subscription, issue)
        return sendInteractiveOptions(
            phone = phone,
            bodyText = question.text,
            options = withGlobalNavigation(question.buttons),
            marker = "[SUPPORT_DIAG:${issue.code}] ",
            currentStep = WhatsAppConversationStep.SUPPORT_DIAG
        )
    }

    fun closeSupportDiagnosticWithButton(
        phone: String,
        subscription: Subscription?,
        buttonReplyId: String?
    ): AutoReplyResult {
        if (!buttonReplyId.orEmpty().startsWith(DIAG_BUTTON_PREFIX)) {
            return invalidInteractiveSelection(phone)
        }
        val bodyText = buildAdvisorClosureMessage()
        return sendTextSupportReply(
            phone = phone,
            bodyText = bodyText,
            marker = "[SUPPORT_CLOSED:ESPERANDO_ASESOR] "
        )
    }

    fun isSupportEntryButton(buttonReplyId: String?): Boolean {
        return buttonReplyId.orEmpty().startsWith(ISSUE_BUTTON_PREFIX)
    }

    fun isSupportDiagnosticButton(buttonReplyId: String?): Boolean {
        return buttonReplyId.orEmpty().startsWith(DIAG_BUTTON_PREFIX)
    }

    fun invalidInteractiveSelectionText(): String {
        return "Para continuar, toca una opción del menú en pantalla 👇"
    }

    fun sendDebtResponseMenu(phone: String, subscription: Subscription?): AutoReplyResult {
        val bodyText = buildDebtResponse(subscription)
        return sendInteractiveOptions(
            phone = phone,
            bodyText = bodyText,
            options = listOf(
                WhatsAppService.InteractiveButtonOption(BUTTON_HOME, "🏠 Menú inicio"),
                WhatsAppService.InteractiveButtonOption(BUTTON_ADVISOR, "🙋 Asesor")
            ),
            marker = "[DEUDA] ",
            currentStep = WhatsAppConversationStep.DEBT_VIEW
        )
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
            |📄 *Estado de tu cuenta:*
            |Estimado(a) $clientName, tu saldo pendiente al día de hoy es S/ ${"%.2f".format(total)}.
            |
            |• Servicio: $planName
            |• Saldo pendiente: S/ ${"%.2f".format(total)}
            |• Fecha de vencimiento: $dueDate
            |
            |Puedes realizar tu pago mediante CCI/BCP ${cfg.bcpAccount} o Yape/Plin al número ${cfg.yapePlin}. ¡Gracias por mantenerte al día!
        """.trimMargin()
    }

    fun buildPaidResponse(subscription: Subscription?): String {
        if (subscription == null) {
            return accountNotFoundMessage()
        }
        val unpaid = unpaidPayments(subscription.id)
        val firstName = firstNameOf(subscription)
        val greeting = if (firstName != null) "Hola $firstName," else "Hola,"
        val cutOffLine = if (subscription.serviceStatus == ServiceStatus.CUT_OFF) {
            "\nSu servicio figura cortado. Se reactivara al validar el pago."
        } else {
            ""
        }

        if (unpaid.isEmpty()) {
            return """
                |$greeting acabamos de verificar en el sistema: su pago ya fue registrado.
                |Su cuenta esta al dia. Gracias por su puntualidad.$cutOffLine
            """.trimMargin()
        }

        val total = unpaid.sumOf { it.amountToPay }
        val count = unpaid.size
        val word = if (count == 1) "factura" else "facturas"
        val oldest = unpaid.firstOrNull()?.let { formatBillingPeriod(it) }

        return """
            |$greeting gracias por avisarnos.
            |
            |En el sistema aun figura(n) $count $word pendiente(s) por S/ ${"%.2f".format(total)}.
            |${if (oldest != null) "Periodo mas antiguo: $oldest." else ""}
            |Si ya realizo el pago, envie por este chat la foto o captura del voucher (Yape, Plin o BCP) para validarlo y actualizar su cuenta.
            |
            |Nuestro equipo lo revisara a la brevedad.$cutOffLine
        """.trimMargin().replace(Regex("\n{3,}"), "\n\n")
    }

    fun buildVoucherReceivedResponse(): String {
        return "Recibimos su comprobante. Nuestro equipo lo revisara a la brevedad y le confirmaremos. Gracias."
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
        val unread = inboundMessageRepository.findByPhoneAndReadAtIsNull(phone)
        val now = LocalDateTime.now()
        var allMetaOk = true
        unread.forEach { inbound ->
            try {
                if (!whatsAppService.markMessageAsRead(inbound.metaMessageId).success) {
                    allMetaOk = false
                }
            } catch (e: Exception) {
                allMetaOk = false
                log.warn("Conversation: mark-all-read fallo para ${inbound.metaMessageId}: ${e.message}")
            }
            inboundMessageRepository.save(inbound.copy(readAt = now))
        }
        return WhatsAppMarkAllReadResultDto(
            phone = phone,
            markedCount = unread.size,
            success = allMetaOk || unread.isNotEmpty()
        )
    }

    fun sendOperatorReply(
        phone: String,
        text: String,
        operatorUsername: String?
    ): WhatsAppThreadMessageDto {
        val trimmed = text.trim()
        if (trimmed.isBlank()) {
            throw IllegalArgumentException("El mensaje no puede estar vacio.")
        }
        val window = serviceWindowService.getServiceWindow(phone)
        if (!window.open) {
            throw IllegalArgumentException("La ventana de servicio de 24h esta cerrada. Solo se pueden enviar plantillas.")
        }

        val sendResult = whatsAppService.sendTextMessage(phoneNumber = phone, message = trimmed)
        if (!sendResult.success) {
            throw Exception(sendResult.metaResponse.ifBlank { "No se pudo enviar el mensaje por WhatsApp." })
        }

        val subscription = findSubscriptionByPhone(phone)
        val now = LocalDateTime.now()
        val saved = messageLogRepository.save(
            WhatsAppMessageLog(
                subscriptionId = subscription?.id,
                phone = phone,
                messageType = MESSAGE_TYPE_OPERATOR_REPLY,
                status = "SENT",
                metaMessageId = sendResult.metaMessageId,
                message = trimmed,
                operatorUsername = operatorUsername,
                sentAt = now,
                createdAt = now
            )
        )
        return saved.toThreadMessage()
    }

    fun resolveReplyToLogId(contextMessageId: String?): Int? {
        if (contextMessageId.isNullOrBlank()) return null
        return messageLogRepository.findByMetaMessageId(contextMessageId)?.id
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
            "👋 ¡Hola! Te damos la bienvenida a Atención al Cliente y Soporte Técnico. ¿En qué te podemos ayudar hoy? Estoy atento para ayudarte."
        } else {
            "Selecciona una opción para continuar 👇"
        }
    }

    private fun sendMainMenuInternal(phone: String, includeGreeting: Boolean): AutoReplyResult {
        val bodyText = buildMainMenuBody(includeGreeting)
        return try {
            val result = whatsAppService.sendInteractiveListMessage(
                phoneNumber = phone,
                bodyText = bodyText,
                buttonText = "Ver opciones",
                sectionTitle = "Menú principal",
                rows = buildMainMenuRows()
            )
            if (result.success) {
                chatStateService.setCurrentStep(phone, WhatsAppConversationStep.MAIN_MENU)
            }
            AutoReplyResult(
                success = result.success,
                messageText = "[MAIN_MENU] $bodyText",
                metaMessageId = result.metaMessageId
            )
        } catch (e: Exception) {
            log.warn("Conversation: no se pudo enviar menu principal interactivo: ${e.message}")
            AutoReplyResult(success = false, messageText = bodyText, metaMessageId = null)
        }
    }

    private fun buildAdvisorClosureMessage(): String {
        val phones = whatsAppProperties.autoReply.secretaryPhoneList().joinToString(" / ")
        return """
            |✅ Tu caso ha sido registrado. Un asesor revisará tu diagnóstico y te atenderá por este mismo chat en breve. ⏱️
            |
            |También puedes comunicarte directamente con Secretaría a los números: 📞 $phones
        """.trimMargin()
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
        currentStep: WhatsAppConversationStep? = null
    ): AutoReplyResult {
        return try {
            val result = if (options.size <= 3) {
                whatsAppService.sendInteractiveReplyButtons(
                    phoneNumber = phone,
                    bodyText = bodyText,
                    buttons = options
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
                            title = it.title
                        )
                    }
                )
            }
            if (result.success && currentStep != null) {
                chatStateService.setCurrentStep(phone, currentStep)
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

    private fun buildMainMenuRows(): List<WhatsAppService.InteractiveListOption> {
        return listOf(
            WhatsAppService.InteractiveListOption(
                id = BUTTON_REPORT_FAULT,
                title = "🛠️ Avería",
                description = "Internet, TV Cable o ambos servicios"
            ),
            WhatsAppService.InteractiveListOption(
                id = BUTTON_DEBT,
                title = "💳 Deuda",
                description = "Saldo pendiente y medios de pago"
            ),
            WhatsAppService.InteractiveListOption(
                id = BUTTON_INSTALLATION,
                title = "📦 Instalación",
                description = "Nueva instalación o traslado"
            ),
            WhatsAppService.InteractiveListOption(
                id = BUTTON_ADVISOR,
                title = "🙋 Asesor",
                description = "Transferencia a atención humana"
            )
        )
    }

    private fun buildSupportEntryMenu(subscription: Subscription?): String {
        val profile = supportProfile(subscription)
        val options = withGlobalNavigation(supportEntryButtons(profile)).joinToString("\n") { "• ${it.title}" }
        return "[SUPPORT_MENU:${profile.code}]\n${supportEntryBody(profile)}\n$options"
    }

    private fun supportEntryBody(profile: SupportProfile): String {
        return when (profile) {
            SupportProfile.COMBO -> "Selecciona qué servicio presenta el problema:"
            SupportProfile.CABLE_ONLY,
            SupportProfile.INTERNET_ONLY -> "Selecciona el problema que estás teniendo:"
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
            |${buildAdvisorClosureMessage()}
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
                text = "Por favor, revisa tu módem. ¿De qué color ves la luz del indicador LOS o PON en el frente?",
                buttons = listOf(
                    WhatsAppService.InteractiveButtonOption("${DIAG_BUTTON_PREFIX}fiber_red", "🔴 Luz roja"),
                    WhatsAppService.InteractiveButtonOption("${DIAG_BUTTON_PREFIX}fiber_green", "🟢 Verde / azul"),
                    WhatsAppService.InteractiveButtonOption("${DIAG_BUTTON_PREFIX}fiber_off", "⚪ Sin luz")
                )
            )
            issue.service == SupportService.INTERNET && isCoaxial(subscription) -> DiagnosticQuestion(
                text = "Por favor, revisa tu módem. ¿La luz de ONLINE o INTERNET está encendida fija?",
                buttons = listOf(
                    WhatsAppService.InteractiveButtonOption("${DIAG_BUTTON_PREFIX}coax_fixed", "🟢 Sí, está fija"),
                    WhatsAppService.InteractiveButtonOption("${DIAG_BUTTON_PREFIX}coax_blink", "🔴 No / Parpadea")
                )
            )
            issue.service == SupportService.CABLE -> DiagnosticQuestion(
                text = "¿Qué pantalla o mensaje ves en tu televisor?",
                buttons = listOf(
                    WhatsAppService.InteractiveButtonOption("${DIAG_BUTTON_PREFIX}tv_black", "📺 Pantalla negra"),
                    WhatsAppService.InteractiveButtonOption("${DIAG_BUTTON_PREFIX}tv_error", "⚠️ Código error")
                )
            )
            else -> DiagnosticQuestion(
                text = "Por favor, revisa tu módem. ¿De qué color ves la luz del indicador LOS o PON en el frente?",
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
            |No encontramos su cuenta con este numero. Comuniquese con secretaria:
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

    data class AutoReplyResult(
        val success: Boolean,
        val messageText: String,
        val metaMessageId: String?
    )

    companion object {
        const val BUTTON_DEBT = "ver_deuda"
        const val BUTTON_PAID = "ya_pague"
        const val BUTTON_SUPPORT = "soporte"
        const val BUTTON_REPORT_FAULT = "reportar_averia"
        const val BUTTON_INSTALLATION = "solicitud_instalacion"
        const val BUTTON_ADVISOR = "hablar_asesor"
        const val BUTTON_BACK = "nav_back"
        const val BUTTON_HOME = "nav_home"
        const val MESSAGE_TYPE_AUTO_REPLY = "AUTO_REPLY"
        const val MESSAGE_TYPE_OPERATOR_REPLY = "OPERATOR_REPLY"
        private const val ISSUE_BUTTON_PREFIX = "support_issue_"
        private const val DIAG_BUTTON_PREFIX = "support_diag_"
    }
}
