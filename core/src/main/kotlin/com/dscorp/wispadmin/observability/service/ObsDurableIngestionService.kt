package com.dscorp.wispadmin.observability.service

import com.dscorp.wispadmin.wispadmin.observability.ReportedEvent
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import com.dscorp.wispadmin.observability.entity.ObsDeliveryReceipt
import com.dscorp.wispadmin.observability.repository.ObsDeliveryReceiptRepository
import com.fasterxml.jackson.databind.ObjectMapper
import java.security.MessageDigest

@Service
class ObsDurableIngestionService(
    private val ingestion: ObsIngestionService,
    private val receipts: ObsDeliveryReceiptRepository,
    private val json: ObjectMapper,
) {
    @Transactional
    fun persist(deliveryId: String, event: ReportedEvent): Long? {
        require(deliveryId.matches(Regex("[a-zA-Z0-9:._-]{1,128}"))) { "INVALID_DELIVERY_ID" }
        val id = hash(json.writeValueAsBytes(listOf(event.platform, event.environment, deliveryId)))
        val payloadHash = hash(json.writeValueAsBytes(event))
        receipts.findById(id).orElse(null)?.let {
            check(it.payloadHash == payloadHash) { "DELIVERY_PAYLOAD_CONFLICT" }
            return it.issueId
        }
        // The unique receipt and event commit together. Concurrent inserts roll back and retry.
        val receipt = receipts.saveAndFlush(ObsDeliveryReceipt(id, payloadHash))
        receipt.issueId = ingestion.persistEvent(event)
        receipts.save(receipt)
        return receipt.issueId
    }

    private fun hash(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }
}
