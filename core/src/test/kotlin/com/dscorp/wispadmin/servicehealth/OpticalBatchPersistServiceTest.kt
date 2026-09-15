package com.dscorp.wispadmin.servicehealth

import com.dscorp.wispadmin.events.LiveOnuState
import com.dscorp.wispadmin.events.LiveTelemetryPort
import com.dscorp.wispadmin.events.OnuOpticalBatchItem
import com.dscorp.wispadmin.events.OnuOpticalBatchPayload
import com.dscorp.wispadmin.servicehealth.config.ServiceHealthProperties
import com.dscorp.wispadmin.servicehealth.config.ServiceHealthScope
import com.dscorp.wispadmin.servicehealth.domain.OpticalSample
import com.dscorp.wispadmin.servicehealth.domain.Quality
import com.dscorp.wispadmin.servicehealth.domain.TelemetryRun
import com.dscorp.wispadmin.servicehealth.port.HealthOnuPort
import com.dscorp.wispadmin.servicehealth.port.HealthOnuRef
import com.dscorp.wispadmin.servicehealth.repository.OpticalSampleRepository
import com.dscorp.wispadmin.servicehealth.repository.TelemetryRunRepository
import com.dscorp.wispadmin.servicehealth.service.IdentityService
import com.dscorp.wispadmin.servicehealth.service.OpticalBatchPersistService
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.ObjectProvider
import java.time.Instant

class OpticalBatchPersistServiceTest {
    private val properties = ServiceHealthProperties().apply {
        enabled = true
        opticalEnabled = true
    }
    private val scope = mockk<ServiceHealthScope>()
    private val identity = mockk<IdentityService>()
    private val onuPort = mockk<HealthOnuPort>()
    private val onuProvider = mockk<ObjectProvider<HealthOnuPort>>()
    private val optical = mockk<OpticalSampleRepository>(relaxed = true)
    private val runs = mockk<TelemetryRunRepository>(relaxed = true)
    private val live = mockk<LiveTelemetryPort>(relaxed = true)
    private val liveProvider = mockk<ObjectProvider<LiveTelemetryPort>>()
    private val json = ObjectMapper().registerModule(JavaTimeModule()).findAndRegisterModules()
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    private val service = OpticalBatchPersistService(
        properties, scope, identity, onuProvider, optical, runs, liveProvider, json,
    )
    private val polledAt = Instant.parse("2026-09-08T20:00:00Z")
    private val onu = HealthOnuRef(
        id = 7987L,
        sn = "VSOL0031C0B6",
        externalId = "gigafiber-ma5608t_1_6_10",
        oltId = 2L,
        oltName = "gigafiber-ma5608t",
        board = 1,
        port = 6,
        onuIndex = 10,
    )

    init {
        every { onuProvider.ifAvailable } returns onuPort
        every { liveProvider.ifUnique } returns live
        every { onuPort.findByExternalId("gigafiber-ma5608t_1_6_10") } returns onu
        every { identity.resolveOnuForCollection("VSOL0031C0B6") } returns 2389
        every { scope.collects(2389) } returns true
        every { runs.save(any()) } answers { firstArg() }
    }

    @Test
    fun `inserts optical sample and updates live telemetry`() {
        every { optical.findByOnuIdAndObservedAt(7987L, polledAt) } returns null
        val saved = slot<OpticalSample>()
        every { optical.save(capture(saved)) } answers { firstArg() }

        val touched = service.persist(
            OnuOpticalBatchPayload(
                oltId = 2L,
                slot = 1,
                port = 6,
                polledAt = polledAt,
                onus = listOf(
                    OnuOpticalBatchItem(
                        sn = "VSOL0031C0B6",
                        onuExternalId = "gigafiber-ma5608t_1_6_10",
                        onuRxDbm = -19.46,
                        onuTxDbm = 2.2,
                        oltRxDbm = -24.56,
                        polledAt = polledAt,
                        runState = "online",
                    ),
                ),
            ),
        )

        assertEquals(listOf(2389), touched)
        assertEquals(-19.46, saved.captured.onuRxDbm)
        assertEquals(polledAt, saved.captured.observedAt)
        verify {
            live.putOnu(
                2389,
                match<LiveOnuState> { it.rxPowerDbm == -19.46 && it.runState == "online" },
            )
        }
        val run = slot<TelemetryRun>()
        verify { runs.save(capture(run)) }
        assertEquals("OLT_OPTICAL", run.captured.source)
        assertEquals("2", run.captured.equipmentKey)
        assertEquals(polledAt, run.captured.startedAt)
        assertEquals(1, run.captured.readCount)
        assertEquals(1, run.captured.writtenCount)
        assertEquals(Quality.FRESH, run.captured.qualityStatus)
        assertNotNull(run.captured.completedAt)
    }

    @Test
    fun `same onuExternalId and polledAt is idempotent`() {
        every { optical.findByOnuIdAndObservedAt(7987L, polledAt) } returns OpticalSample(
            id = 1L,
            subscriptionId = 2389,
            onuId = 7987L,
            observedAt = polledAt,
        )

        service.persist(
            OnuOpticalBatchPayload(
                oltId = 2L,
                slot = 1,
                port = 6,
                polledAt = polledAt,
                onus = listOf(
                    OnuOpticalBatchItem(
                        sn = "VSOL0031C0B6",
                        onuExternalId = "gigafiber-ma5608t_1_6_10",
                        onuRxDbm = -19.46,
                        onuTxDbm = 2.2,
                        oltRxDbm = -24.56,
                        polledAt = polledAt,
                    ),
                ),
            ),
        )

        verify(exactly = 0) { optical.save(any()) }
        val run = slot<TelemetryRun>()
        verify { runs.save(capture(run)) }
        assertEquals("OLT_OPTICAL", run.captured.source)
        assertEquals(1, run.captured.readCount)
        assertEquals(0, run.captured.writtenCount)
    }

    @Test
    fun `out of collection scope does not persist sample`() {
        every { scope.collects(2389) } returns false
        val touched = service.persist(
            OnuOpticalBatchPayload(
                oltId = 2L,
                slot = 1,
                port = 6,
                polledAt = polledAt,
                onus = listOf(
                    OnuOpticalBatchItem(
                        sn = "VSOL0031C0B6",
                        onuExternalId = "gigafiber-ma5608t_1_6_10",
                        onuRxDbm = -19.46,
                        onuTxDbm = 2.2,
                        oltRxDbm = -24.56,
                        polledAt = polledAt,
                    ),
                ),
            ),
        )
        assertEquals(emptyList<Int>(), touched)
        verify(exactly = 0) { optical.save(any()) }
    }
}
