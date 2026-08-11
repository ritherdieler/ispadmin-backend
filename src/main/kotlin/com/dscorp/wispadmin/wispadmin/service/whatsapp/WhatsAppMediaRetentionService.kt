package com.dscorp.wispadmin.wispadmin.service.whatsapp

import com.dscorp.wispadmin.wispadmin.config.WhatsAppProperties
import com.dscorp.wispadmin.wispadmin.data.model.CrmChannel
import com.dscorp.wispadmin.wispadmin.data.model.CrmConversation
import com.dscorp.wispadmin.wispadmin.data.model.CrmConversationStatus
import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppInboundMessage
import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppMessageLog
import com.dscorp.wispadmin.wispadmin.repository.CrmConversationRepository
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppInboundMessageRepository
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppMessageLogRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.nio.file.Files
import java.sql.Timestamp
import java.time.LocalDateTime

@Service
class WhatsAppMediaRetentionService(
    private val whatsAppProperties: WhatsAppProperties,
    private val inboundMessageRepository: WhatsAppInboundMessageRepository,
    private val messageLogRepository: WhatsAppMessageLogRepository,
    private val crmConversationRepository: CrmConversationRepository,
    private val mediaDownloadService: WhatsAppMediaDownloadService,
) {

    private val log = LoggerFactory.getLogger(WhatsAppMediaRetentionService::class.java)

    data class PurgeResult(
        val inboundPurged: Int,
        val outboundPurged: Int,
    )

    @Transactional
    fun purgeExpiredMedia(now: LocalDateTime = LocalDateTime.now()): PurgeResult {
        val settings = WhatsAppMediaRetentionSettings.from(whatsAppProperties.retention)
        val minAgeCutoff = now.minusDays(settings.minAgeDays)
        val inboundPurged = purgeInboundBatches(minAgeCutoff, now, settings)
        val outboundPurged = purgeOutboundBatches(minAgeCutoff, now, settings)
        if (inboundPurged > 0 || outboundPurged > 0) {
            log.info("WhatsApp media retention purged inbound={} outbound={}", inboundPurged, outboundPurged)
        }
        return PurgeResult(inboundPurged = inboundPurged, outboundPurged = outboundPurged)
    }

    private fun purgeInboundBatches(
        minAgeCutoff: LocalDateTime,
        now: LocalDateTime,
        settings: WhatsAppMediaRetentionSettings,
    ): Int {
        var purged = 0
        var page = 0
        while (true) {
            val batch = inboundMessageRepository.findInboundMediaRetentionCandidates(
                minAgeCutoff,
                PageRequest.of(page, PAGE_SIZE)
            )
            if (batch.isEmpty()) {
                break
            }
            val contexts = loadInboundContexts(batch)
            batch.forEach { inbound ->
                val context = contexts[inbound.id] ?: emptyInboundContext()
                if (WhatsAppMediaRetentionPolicy.shouldPurgeInbound(inbound, context, now, settings)) {
                    if (purgeInboundRecord(inbound, now)) {
                        purged++
                    }
                }
            }
            page++
        }
        return purged
    }

    private fun purgeOutboundBatches(
        minAgeCutoff: LocalDateTime,
        now: LocalDateTime,
        settings: WhatsAppMediaRetentionSettings,
    ): Int {
        var purged = 0
        var page = 0
        while (true) {
            val batch = messageLogRepository.findOutboundMediaRetentionCandidates(
                minAgeCutoff,
                PageRequest.of(page, PAGE_SIZE)
            )
            if (batch.isEmpty()) {
                break
            }
            batch.forEach { logEntry ->
                if (WhatsAppMediaRetentionPolicy.shouldPurgeOutbound(logEntry, now, settings)) {
                    if (purgeOutboundRecord(logEntry, now)) {
                        purged++
                    }
                }
            }
            page++
        }
        return purged
    }

    private fun purgeInboundRecord(inbound: WhatsAppInboundMessage, now: LocalDateTime): Boolean {
        deleteStoredFile(inbound.mediaStoredPath)
        inbound.mediaStoredPath = null
        inbound.mediaPurgedAt = now
        inboundMessageRepository.save(inbound)
        return true
    }

    private fun purgeOutboundRecord(logEntry: WhatsAppMessageLog, now: LocalDateTime): Boolean {
        deleteStoredFile(logEntry.mediaStoredPath)
        logEntry.mediaStoredPath = null
        logEntry.mediaPurgedAt = now
        messageLogRepository.save(logEntry)
        return true
    }

    private fun deleteStoredFile(storedPath: String?) {
        val path = mediaDownloadService.resolveStoredPath(storedPath) ?: return
        try {
            Files.deleteIfExists(path)
        } catch (ex: Exception) {
            log.warn("WhatsApp media retention: no se pudo borrar {}: {}", path, ex.message)
        }
    }

    private fun loadInboundContexts(batch: List<WhatsAppInboundMessage>): Map<Int?, InboundRetentionContext> {
        if (batch.isEmpty()) {
            return emptyMap()
        }
        val canonicalPhones = batch.map { PeruvianWhatsAppPhone.canonicalConversationKey(it.phone) }.distinct()
        val variants = canonicalPhones.flatMap { PeruvianWhatsAppPhone.queryVariants(it) }.distinct()
        val crmByCanonical = if (variants.isEmpty()) {
            emptyMap()
        } else {
            crmConversationRepository.findByChannelAndPhoneIn(CrmChannel.WHATSAPP, variants)
                .associateBy { PeruvianWhatsAppPhone.canonicalConversationKey(it.phone) }
        }
        val latestMediaByCanonical = if (variants.isEmpty()) {
            emptyMap()
        } else {
            inboundMessageRepository.findLatestMediaAtByPhoneIn(variants).associate { row ->
                PeruvianWhatsAppPhone.canonicalConversationKey(row[0].toString()) to toLocalDateTime(row[1])
            }
        }
        return batch.associate { inbound ->
            val canonical = PeruvianWhatsAppPhone.canonicalConversationKey(inbound.phone)
            val crm = pickBestCrm(canonical, crmByCanonical)
            val status = crm?.status
            val pendingReceipt = WhatsAppInboxViewPolicy.hasPendingReceipt(
                status = status?.name,
                resolvedAt = crm?.resolvedAt,
                latestMediaAt = latestMediaByCanonical[canonical]
            )
            inbound.id to InboundRetentionContext(
                crmStatus = status,
                resolvedAt = crm?.resolvedAt,
                hasPendingReceipt = pendingReceipt,
                latestPaymentProofMediaAt = latestMediaByCanonical[canonical],
            )
        }
    }

    private fun pickBestCrm(
        phone: String,
        crmByPhone: Map<String, CrmConversation>,
    ): CrmConversation? {
        PeruvianWhatsAppPhone.queryVariants(phone).forEach { variant ->
            crmByPhone[PeruvianWhatsAppPhone.canonicalConversationKey(variant)]?.let { return it }
        }
        return crmByPhone[phone]
    }

    private fun emptyInboundContext() = InboundRetentionContext(
        crmStatus = null,
        resolvedAt = null,
        hasPendingReceipt = false,
        latestPaymentProofMediaAt = null,
    )

    private fun toLocalDateTime(value: Any?): LocalDateTime? = when (value) {
        null -> null
        is LocalDateTime -> value
        is Timestamp -> value.toLocalDateTime()
        is java.util.Date -> Timestamp(value.time).toLocalDateTime()
        else -> null
    }

    companion object {
        private const val PAGE_SIZE = 200
    }
}
