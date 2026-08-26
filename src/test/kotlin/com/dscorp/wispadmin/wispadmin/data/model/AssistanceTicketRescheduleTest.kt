package com.dscorp.wispadmin.wispadmin.data.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.Date

class AssistanceTicketRescheduleTest {

    @Test
    fun `reschedule updates scheduledAt and returns ticket to PENDING without clearing responsible`() {
        val technician = User(id = 7, name = "Luis", lastName = "Rojas")
        val originalSchedule = Date(1_700_000_000_000L)
        val ticket = AssistanceTicket(
            id = 42,
            phone = "987654321",
            category = "Internet Lento",
            description = "Se cae el servicio",
            status = AssistanceTicketStatus.ASSIGNED,
            scheduledAt = originalSchedule,
            assignedAt = originalSchedule,
            responsible = technician
        )

        val newSchedule = 1_800_000_000_000L
        ticket.applyReschedule(newSchedule)

        assertEquals(Date(newSchedule), ticket.scheduledAt)
        assertEquals(AssistanceTicketStatus.PENDING, ticket.status)
        assertEquals(technician, ticket.responsible)
        assertEquals(originalSchedule, ticket.assignedAt)
    }
}
