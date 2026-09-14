package com.dscorp.wispadmin.oltgateway.service

import com.dscorp.wispadmin.oltgateway.api.SmartOltUnconfiguredItemDto
import com.dscorp.wispadmin.oltgateway.config.OltGatewayProperties
import com.dscorp.wispadmin.oltgateway.dto.AutofindRefreshResultDto
import com.dscorp.wispadmin.oltgateway.dto.AutofindStatusDto
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

open class OltAutofindCacheService(
    private val queryFacade: OltGatewayQueryFacade,
    private val writer: OltAutofindCacheWriter,
    private val properties: OltGatewayProperties
) {

    companion object {
        private val logger = LoggerFactory.getLogger(OltAutofindCacheService::class.java)
        const val SOURCE_BACKGROUND = "background"
        const val SOURCE_LIVE = "live"
    }

    private val running = AtomicBoolean(false)

    @Volatile
    private var lastRefreshAt: Instant? = null

    @Volatile
    private var lastResult: AutofindRefreshResultDto? = null

    open fun refresh(): AutofindRefreshResultDto = doRefresh(SOURCE_BACKGROUND)

    open fun refreshLive(): AutofindRefreshResultDto = doRefresh(SOURCE_LIVE)

    open fun listUnconfigured(): List<SmartOltUnconfiguredItemDto> = writer.listUnconfigured()

    open fun status(): AutofindStatusDto = AutofindStatusDto(
        running = running.get(),
        cachedCount = writer.cachedCount(),
        lastRefreshAt = lastRefreshAt?.toString(),
        lastResult = lastResult
    )

    private fun doRefresh(source: String): AutofindRefreshResultDto {
        if (!running.compareAndSet(false, true)) {
            return AutofindRefreshResultDto(source = source, skippedReason = "already_running")
        }
        val started = System.currentTimeMillis()
        return try {
            val items = if (source == SOURCE_LIVE) {
                queryFacade.autofindParsedLive(properties.autofind.liveTimeoutMs)
            } else {
                queryFacade.autofindParsedBackground()
            }
            val stored = writer.replaceSnapshot(items, source)
            AutofindRefreshResultDto(
                source = source,
                seen = items.size,
                stored = stored.stored,
                removed = stored.removed,
                durationMs = System.currentTimeMillis() - started
            ).also { lastResult = it; lastRefreshAt = Instant.now() }
        } catch (ex: Exception) {
            logger.warn("Autofind refresh ({}) failed: {}", source, ex.message)
            AutofindRefreshResultDto(
                source = source,
                durationMs = System.currentTimeMillis() - started,
                error = ex.message ?: ex::class.java.simpleName
            ).also { lastResult = it; lastRefreshAt = Instant.now() }
        } finally {
            running.set(false)
        }
    }
}
