package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.dto.WhatsAppTestSendResponseDto
import com.dscorp.wispadmin.wispadmin.dto.toDto
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppMessageLogRepository
import com.dscorp.wispadmin.wispadmin.requestbody.WhatsAppSelectedRemindersRequest
import com.dscorp.wispadmin.wispadmin.requestbody.WhatsAppSendMessagesRequest
import com.dscorp.wispadmin.wispadmin.requestbody.WhatsAppTemplateTestMessageRequest
import com.dscorp.wispadmin.wispadmin.service.WhatsAppBackofficeMessageService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * API de mensajes WhatsApp para el backoffice (/whatsapp en frontend).
 */
@RestController
@RequestMapping("/whatsapp")
class WhatsAppBackofficeController(
    private val messageService: WhatsAppBackofficeMessageService,
    private val whatsAppMessageLogRepository: WhatsAppMessageLogRepository,
    private val templateMessageSender: WhatsAppTemplateMessageSender
) {

    @GetMapping("/templates")
    fun listTemplates(): ResponseEntity<Any> {
        return ResponseEntity.ok(messageService.listTemplates())
    }

    @GetMapping("/message-candidates")
    fun listMessageCandidates(
        @RequestParam templateCode: String,
        @RequestParam(defaultValue = "200") limit: Int
    ): ResponseEntity<Any> {
        return ResponseEntity.ok(messageService.listCandidates(templateCode, limit))
    }

    @PostMapping("/messages/selected")
    fun sendSelectedMessages(
        @RequestBody request: WhatsAppSendMessagesRequest
    ): ResponseEntity<Any> {
        return ResponseEntity.ok(
            messageService.sendSelected(request.templateCode, request.targetIds)
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
        @RequestBody request: WhatsAppSelectedRemindersRequest
    ): ResponseEntity<Any> {
        return ResponseEntity.ok(messageService.sendSelectedReminders(request.paymentIds))
    }

    @PostMapping("/messages/template")
    fun sendManualTemplateMessage(
        @RequestBody request: WhatsAppTemplateTestMessageRequest
    ): ResponseEntity<WhatsAppTestSendResponseDto> {
        return templateMessageSender.sendPaymentReminderTemplate(request)
    }

    @GetMapping("/logs")
    fun getRecentLogs(): ResponseEntity<Any> {
        return ResponseEntity.ok(
            whatsAppMessageLogRepository.findTop50ByOrderByCreatedAtDesc().map { it.toDto() }
        )
    }

    @GetMapping("/logs/payment/{paymentId}")
    fun getLogsByPaymentId(
        @PathVariable paymentId: Int
    ): ResponseEntity<Any> {
        return ResponseEntity.ok(
            whatsAppMessageLogRepository.findByPaymentIdOrderByCreatedAtDesc(paymentId).map { it.toDto() }
        )
    }
}
