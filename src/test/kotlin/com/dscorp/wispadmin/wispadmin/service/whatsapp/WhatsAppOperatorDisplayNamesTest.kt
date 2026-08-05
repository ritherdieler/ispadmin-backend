package com.dscorp.wispadmin.wispadmin.service.whatsapp

import com.dscorp.wispadmin.wispadmin.data.model.User
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WhatsAppOperatorDisplayNamesTest {

    @Test
    fun `resolveDisplayName uses name and lastName from lookup`() {
        val lookup = WhatsAppOperatorDisplayNames.buildLookup(
            listOf(
                User(
                    name = "Edwin",
                    lastName = "Escobal",
                    username = "Edwin10",
                ),
            ),
        )

        assertEquals(
            "Edwin Escobal",
            WhatsAppOperatorDisplayNames.resolveDisplayName("Edwin10", lookup),
        )
    }

    @Test
    fun `resolveDisplayName is case insensitive on username`() {
        val lookup = WhatsAppOperatorDisplayNames.buildLookup(
            listOf(User(name = "Mariela", lastName = "Solorzano", username = "mariela")),
        )

        assertEquals(
            "Mariela Solorzano",
            WhatsAppOperatorDisplayNames.resolveDisplayName("MARIELA", lookup),
        )
    }
}
