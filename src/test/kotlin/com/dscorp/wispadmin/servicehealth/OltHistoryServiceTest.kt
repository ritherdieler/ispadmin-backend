package com.dscorp.wispadmin.servicehealth

import com.dscorp.wispadmin.servicehealth.config.ServiceHealthProperties
import com.dscorp.wispadmin.servicehealth.config.ServiceHealthScope
import com.dscorp.wispadmin.servicehealth.domain.OpticalSample
import com.dscorp.wispadmin.servicehealth.domain.Quality
import com.dscorp.wispadmin.servicehealth.domain.TelemetryRun
import com.dscorp.wispadmin.servicehealth.port.HealthOnuPort
import com.dscorp.wispadmin.servicehealth.port.HealthOnuRef
import com.dscorp.wispadmin.servicehealth.port.HealthOpticalObservation
import com.dscorp.wispadmin.servicehealth.port.HealthOpticalRow
import com.dscorp.wispadmin.servicehealth.repository.HealthCursorRepository
import com.dscorp.wispadmin.servicehealth.repository.OnuStateEventRepository
import com.dscorp.wispadmin.servicehealth.repository.OpticalSampleRepository
import com.dscorp.wispadmin.servicehealth.repository.TelemetryRunRepository
import com.dscorp.wispadmin.servicehealth.service.IdentityService
import com.dscorp.wispadmin.servicehealth.service.OltHistoryService
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.ObjectProvider
import java.time.Instant

class OltHistoryServiceTest {
    private val properties = ServiceHealthProperties().apply {
        enabled = true
        opticalEnabled = true
    }
    private val scope = mockk<ServiceHealthScope>()
    private val identity = mockk<IdentityService>()
    private val onuPort = mockk<HealthOnuPort>()
    private val onuProvider = mockk<ObjectProvider<HealthOnuPort>>()
    private val optical = mockk<OpticalSampleRepository>(relaxed = true)
    private val states = mockk<OnuStateEventRepository>(relaxed = true)
    private val cursors = mockk<HealthCursorRepository>(relaxed = true)
    private val runs = mockk<TelemetryRunRepository>(relaxed = true)
    private val service = OltHistoryService(properties, scope, identity, onuProvider, optical, states, cursors, runs)
    private val now = Instant.parse("2026-08-31T20:00:00Z")

    @Test
    fun `staging lab subscription is persisted when scope collects`() {
        every { onuProvider.ifAvailable } returns onuPort
        every { onuPort.findByOlt(2L) } returns listOf(
            HealthOnuRef(7627L, "VSOL0031C0B6", "gigafiber-ma5608t_1_6_10", 2L, "gigafiber-ma5608t", 1, 6, 10),
        )
        every { identity.resolveOnuForCollection("VSOL0031C0B6") } returns 2329
        every { scope.collects(2329) } returns true
        every { runs.save(any()) } answers { firstArg<TelemetryRun>().also { if (it.id == null) it.id = 1L } }
        val saved = slot<OpticalSample>()
        every { optical.save(capture(saved)) } answers { firstArg() }

        service.onOptical(
            HealthOpticalObservation(
                oltId = 2L,
                observedAt = now,
                rows = listOf(HealthOpticalRow(1, 6, 10, -21.0, -2.0, -27.0, null, null, null)),
            ),
        )

        assertEquals(2329, saved.captured.subscriptionId)
        assertEquals(-21.0, saved.captured.onuRxDbm)
        assertEquals(Quality.FRESH, saved.captured.qualityStatus)
        verify(exactly = 1) { scope.collects(2329) }
    }

    @Test
    fun `staging skips non-lab subscription even if identity resolves`() {
        every { onuProvider.ifAvailable } returns onuPort
        every { onuPort.findByOlt(2L) } returns listOf(
            HealthOnuRef(11L, "HWTC1", "ext", 2L, "olt", 0, 1, 8),
        )
        every { identity.resolveOnuForCollection("HWTC1") } returns 2328
        every { scope.collects(2328) } returns false
        every { runs.save(any()) } answers { firstArg<TelemetryRun>().also { if (it.id == null) it.id = 1L } }

        service.onOptical(
            HealthOpticalObservation(
                oltId = 2L,
                observedAt = now,
                rows = listOf(HealthOpticalRow(0, 1, 8, -20.0, null, null, null, null, null)),
            ),
        )

        verify(exactly = 0) { optical.save(any()) }
    }
}
