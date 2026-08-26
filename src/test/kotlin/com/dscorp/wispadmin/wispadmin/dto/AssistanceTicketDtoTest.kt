package com.dscorp.wispadmin.wispadmin.dto

import com.dscorp.wispadmin.wispadmin.controller.toDto
import com.dscorp.wispadmin.wispadmin.data.model.AssistanceTicket
import com.dscorp.wispadmin.wispadmin.data.model.EquipmentCondition
import com.dscorp.wispadmin.wispadmin.data.model.Place
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AssistanceTicketDtoTest {

    @Test
    fun `toDto includes subscription ip for a client ticket`() {
        val ticket = AssistanceTicket(
            id = 12,
            phone = "946734696",
            category = "Sin Conexión a Internet",
            description = "Sin señal",
            subscription = Subscription(
                id = 88,
                firstName = "Patricia",
                lastName = "Carillo Cuitana",
                phone = "946734696",
                ip = "192.168.30.10",
                place = Place(id = 1, name = "La Villa"),
                equipmentCondition = EquipmentCondition.LOAN
            )
        )

        val dto = ticket.toDto()

        assertEquals("192.168.30.10", dto.ip)
        assertEquals("Patricia Carillo Cuitana", dto.name)
        assertEquals("946734696", dto.phone)
        assertEquals("La Villa", dto.place)
    }

    @Test
    fun `toDto omits blank subscription ip`() {
        val ticket = AssistanceTicket(
            id = 13,
            phone = "946734696",
            category = "Otros",
            description = "Consulta",
            subscription = Subscription(
                id = 89,
                firstName = "Ana",
                lastName = "Lopez",
                ip = "   ",
                equipmentCondition = EquipmentCondition.LOAN
            )
        )

        assertNull(ticket.toDto().ip)
    }

    @Test
    fun `toDto leaves ip null for external customers`() {
        val ticket = AssistanceTicket(
            id = 14,
            phone = "999888777",
            category = "Otros",
            description = "Visita",
            isExternalCustomer = true,
            externalCustomerName = "Pedro Sanchez",
            placeName = "Huacho"
        )

        assertNull(ticket.toDto().ip)
    }
}
