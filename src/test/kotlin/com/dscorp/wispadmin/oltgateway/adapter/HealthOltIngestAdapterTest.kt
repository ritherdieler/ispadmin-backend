package com.dscorp.wispadmin.oltgateway.adapter

import com.dscorp.wispadmin.oltgateway.parser.ParsedOpticalInfo
import com.dscorp.wispadmin.oltgateway.service.OltOpticalFailure
import com.dscorp.wispadmin.oltgateway.service.OltOpticalObservation
import com.dscorp.wispadmin.oltgateway.service.OltSignalPollService
import com.dscorp.wispadmin.oltgateway.service.OltStateObservation
import com.dscorp.wispadmin.servicehealth.port.HealthOltIngestPort
import com.dscorp.wispadmin.servicehealth.port.HealthOpticalObservation
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.ObjectProvider
import java.time.Instant

class HealthOltIngestAdapterTest {

    private val port = mockk<HealthOltIngestPort>(relaxed = true)
    private val provider = mockk<ObjectProvider<HealthOltIngestPort>>()
    private val adapter = HealthOltIngestAdapter(provider)
    private val now = Instant.parse("2026-08-31T20:00:00Z")

    @Test
    fun `forwards optical and state events to the ingest port`() {
        every { provider.ifAvailable } returns port

        adapter.opticalFailure(OltOpticalFailure(3L, now, "timeout"))
        adapter.optical(
            OltOpticalObservation(
                3L,
                now,
                listOf(OltSignalPollService.OpticalRow(0, 1, ParsedOpticalInfo(ontId = 8, rxPowerDbm = -21.0)))
            )
        )
        adapter.state(OltStateObservation("HWTC1", "offline", "LOS", now))

        verify { port.onOpticalFailure(3L, now, "timeout") }
        verify {
            port.onOptical(
                HealthOpticalObservation(
                    oltId = 3L,
                    observedAt = now,
                    rows = listOf(
                        com.dscorp.wispadmin.servicehealth.port.HealthOpticalRow(
                            slot = 0,
                            port = 1,
                            ontId = 8,
                            rxPowerDbm = -21.0,
                            txPowerDbm = null,
                            oltRxPowerDbm = null,
                            temperatureC = null,
                            biasCurrentMa = null,
                            distanceM = null
                        )
                    )
                )
            )
        }
        verify { port.onState("HWTC1", "offline", "LOS", now) }
    }

    @Test
    fun `without servicehealth ingest is skipped`() {
        every { provider.ifAvailable } returns null

        adapter.opticalFailure(OltOpticalFailure(3L, now, "timeout"))
        adapter.state(OltStateObservation("HWTC1", "offline", null, now))

        verify(exactly = 0) { port.onOpticalFailure(any(), any(), any()) }
        verify(exactly = 0) { port.onState(any(), any(), any(), any()) }
    }
}
