package com.dscorp.wispadmin.wispadmin.service.whatsapp

import com.dscorp.wispadmin.wispadmin.dto.WhatsAppBatchSendAcceptedDto
import com.dscorp.wispadmin.wispadmin.dto.WhatsAppBatchSendStatusDto
import com.dscorp.wispadmin.wispadmin.dto.WhatsAppMessageResultDto
import com.dscorp.wispadmin.wispadmin.service.WhatsAppBackofficeMessageService
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor

@Service
class WhatsAppBatchSendJobService(
    private val messageService: WhatsAppBackofficeMessageService,
    private val crmEventPublisher: CrmEventPublisher,
    @Qualifier("whatsAppBatchTaskExecutor") private val batchExecutor: Executor
) {

    private val jobs = ConcurrentHashMap<String, MutableBatchJob>()

    fun enqueueSendSelected(
        templateCode: String,
        targetIds: List<Int>,
        operatorUsername: String?
    ): WhatsAppBatchSendAcceptedDto {
        val uniqueTargetIds = targetIds.distinct()
        val campaignId = UUID.randomUUID().toString()
        jobs[campaignId] = MutableBatchJob(
            campaignId = campaignId,
            templateCode = templateCode,
            total = uniqueTargetIds.size
        )
        batchExecutor.execute {
            runBatch(campaignId, templateCode, uniqueTargetIds, operatorUsername)
        }
        return WhatsAppBatchSendAcceptedDto(
            campaignId = campaignId,
            templateCode = templateCode,
            total = uniqueTargetIds.size,
            status = STATUS_RUNNING
        )
    }

    fun getBatchStatus(campaignId: String): WhatsAppBatchSendStatusDto? {
        val job = jobs[campaignId] ?: return null
        synchronized(job) {
            return job.toDto()
        }
    }

    private fun runBatch(
        campaignId: String,
        templateCode: String,
        targetIds: List<Int>,
        operatorUsername: String?
    ) {
        val job = jobs[campaignId] ?: return
        try {
            val result = messageService.executeSendSelected(
                templateCode = templateCode,
                targetIds = targetIds,
                campaignId = campaignId,
                operatorUsername = operatorUsername,
                onEachResult = { detail -> recordProgress(job, detail) }
            )
            synchronized(job) {
                job.status = STATUS_COMPLETED
                if (job.processed == 0) {
                    job.sent = result.sent
                    job.skipped = result.skipped
                    job.failed = result.failed
                    job.processed = result.details.size
                    job.details.addAll(result.details)
                }
            }
            publishProgress(job, finished = true)
        } catch (_: Exception) {
            synchronized(job) {
                job.status = STATUS_FAILED
            }
            publishProgress(job, finished = true)
        }
    }

    private fun recordProgress(job: MutableBatchJob, detail: WhatsAppMessageResultDto) {
        synchronized(job) {
            job.processed += 1
            when (detail.status) {
                com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppTemplateDeliveryService.STATUS_SENT ->
                    job.sent += 1
                com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppTemplateDeliveryService.STATUS_SKIPPED ->
                    job.skipped += 1
                else -> job.failed += 1
            }
            job.details.add(detail)
        }
        publishProgress(job, finished = false)
    }

    private fun publishProgress(job: MutableBatchJob, finished: Boolean) {
        val snapshot = synchronized(job) { job.toDto() }
        crmEventPublisher.publish(
            eventType = CrmEventPublisher.BATCH_PROGRESS,
            payload = mapOf(
                "campaignId" to snapshot.campaignId,
                "templateCode" to snapshot.templateCode,
                "status" to snapshot.status,
                "total" to snapshot.total,
                "processed" to snapshot.processed,
                "sent" to snapshot.sent,
                "skipped" to snapshot.skipped,
                "failed" to snapshot.failed,
                "finished" to finished,
                "latestDetail" to snapshot.details.lastOrNull()?.let { detail ->
                    mapOf(
                        "targetId" to detail.targetId,
                        "status" to detail.status,
                        "clientName" to detail.clientName,
                        "reason" to detail.reason
                    )
                }
            )
        )
    }

    private class MutableBatchJob(
        val campaignId: String,
        val templateCode: String,
        val total: Int,
        var status: String = STATUS_RUNNING,
        var processed: Int = 0,
        var sent: Int = 0,
        var skipped: Int = 0,
        var failed: Int = 0,
        val details: MutableList<WhatsAppMessageResultDto> = mutableListOf()
    ) {
        fun toDto() = WhatsAppBatchSendStatusDto(
            campaignId = campaignId,
            templateCode = templateCode,
            status = status,
            total = total,
            processed = processed,
            sent = sent,
            skipped = skipped,
            failed = failed,
            details = details.toList()
        )
    }

    companion object {
        const val STATUS_RUNNING = "RUNNING"
        const val STATUS_COMPLETED = "COMPLETED"
        const val STATUS_FAILED = "FAILED"
    }
}
