package com.dscorp.wispadmin.shared.telemetry

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

class TelemetryRetentionCoordinatorTest {

    private val netdiag = mockk<TelemetryRetentionPort>(relaxed = true)
    private val observability = mockk<TelemetryRetentionPort>(relaxed = true)
    private val traffic = mockk<TelemetryRetentionPort>(relaxed = true)

    @Test
    fun `ejecuta todos los puertos y suma lo que cada uno borro`() {
        every { netdiag.purgeExpired(any()) } returns 12
        every { observability.purgeExpired(any()) } returns 4
        every { traffic.purgeExpired(any()) } returns 7

        val coordinator = TelemetryRetentionCoordinator(listOf(netdiag, observability, traffic))
        val now = Instant.parse("2026-09-01T12:00:00Z")

        val result = coordinator.purgeExpired(now)

        assertEquals(23, result.deleted)
        assertEquals(3, result.ran)
        assertEquals(0, result.failed)
        verify { netdiag.purgeExpired(now) }
        verify { observability.purgeExpired(now) }
        verify { traffic.purgeExpired(now) }
    }

    @Test
    fun `un puerto que falla no detiene a los demas`() {
        every { netdiag.purgeExpired(any()) } throws IllegalStateException("locked")
        every { observability.purgeExpired(any()) } returns 2
        every { traffic.purgeExpired(any()) } returns 1

        val coordinator = TelemetryRetentionCoordinator(listOf(netdiag, observability, traffic))

        val result = coordinator.purgeExpired()

        assertEquals(3, result.deleted)
        assertEquals(2, result.ran)
        assertEquals(1, result.failed)
    }

    @Test
    fun `sin puertos registrados no borra nada`() {
        val result = TelemetryRetentionCoordinator(emptyList()).purgeExpired()

        assertEquals(0, result.deleted)
        assertEquals(0, result.ran)
    }
}
