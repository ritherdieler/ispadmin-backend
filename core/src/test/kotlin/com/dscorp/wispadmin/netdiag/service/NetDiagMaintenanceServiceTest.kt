package com.dscorp.wispadmin.netdiag.service

import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagMaintenanceWindow
import com.dscorp.wispadmin.netdiag.domain.repository.NetDiagMaintenanceWindowRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class NetDiagMaintenanceServiceTest {

    private val repository = mockk<NetDiagMaintenanceWindowRepository>()
    private lateinit var service: NetDiagMaintenanceService

    @BeforeEach
    fun setUp() {
        service = NetDiagMaintenanceService(repository)
    }

    @Test
    fun `isNotificationsSuppressed retorna true cuando hay ventana activa para el target`() {
        val now = Instant.now()
        every { repository.findActiveAt(now) } returns listOf(
            window(targetId = 7L, startsAt = now.minus(1, ChronoUnit.HOURS), endsAt = now.plus(1, ChronoUnit.HOURS))
        )

        assertTrue(service.isNotificationsSuppressed(7L, now))
    }

    @Test
    fun `isNotificationsSuppressed retorna true con ventana global target null`() {
        val now = Instant.now()
        every { repository.findActiveAt(now) } returns listOf(
            window(targetId = null, startsAt = now.minus(1, ChronoUnit.HOURS), endsAt = now.plus(1, ChronoUnit.HOURS))
        )

        assertTrue(service.isNotificationsSuppressed(99L, now))
    }

    @Test
    fun `isNotificationsSuppressed retorna false sin ventanas activas`() {
        val now = Instant.now()
        every { repository.findActiveAt(now) } returns emptyList()

        assertFalse(service.isNotificationsSuppressed(7L, now))
    }

    @Test
    fun `create persiste ventana con fechas normalizadas`() {
        val savedSlot = slot<NetDiagMaintenanceWindow>()
        every { repository.save(capture(savedSlot)) } answers { savedSlot.captured.apply { id = 1L } }

        val start = Instant.parse("2026-07-26T10:00:00Z")
        val end = Instant.parse("2026-07-26T12:00:00Z")
        val dto = service.create(
            targetId = 5L,
            title = "Upgrade MK1",
            description = "Ventana planificada",
            startsAt = start,
            endsAt = end
        )

        assertEquals(1L, dto.id)
        assertEquals(5L, dto.targetId)
        verify { repository.save(any()) }
    }

    private fun window(targetId: Long?, startsAt: Instant, endsAt: Instant): NetDiagMaintenanceWindow {
        return NetDiagMaintenanceWindow(
            id = 1L,
            targetId = targetId,
            title = "Maint",
            startsAt = startsAt,
            endsAt = endsAt,
            suppressNotifications = true
        )
    }
}
