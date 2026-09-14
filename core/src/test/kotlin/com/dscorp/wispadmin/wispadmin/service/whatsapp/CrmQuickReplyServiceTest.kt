package com.dscorp.wispadmin.wispadmin.service.whatsapp

import com.dscorp.wispadmin.wispadmin.data.model.CrmQuickReply
import com.dscorp.wispadmin.wispadmin.dto.CrmQuickReplyBody
import com.dscorp.wispadmin.wispadmin.repository.CrmQuickReplyRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Optional

class CrmQuickReplyServiceTest {

    private val repository = mockk<CrmQuickReplyRepository>()
    private lateinit var service: CrmQuickReplyService

    @BeforeEach
    fun setUp() {
        service = CrmQuickReplyService(repository)
    }

    @Test
    fun `create personal quick reply for secretary`() {
        every { repository.save(any()) } answers {
            firstArg<CrmQuickReply>().copy(id = 1)
        }

        val dto = service.create(
            userId = 7,
            isAdmin = false,
            body = CrmQuickReplyBody(title = "Saludo", body = "Hola {{clientName}}", global = false)
        )

        assertEquals(1, dto.id)
        assertEquals(7, dto.ownerUserId)
        assertEquals(false, dto.global)
    }

    @Test
    fun `secretary cannot create global quick reply`() {
        assertThrows(CrmConversationForbiddenException::class.java) {
            service.create(
                userId = 7,
                isAdmin = false,
                body = CrmQuickReplyBody(title = "Global", body = "Texto", global = true)
            )
        }
        verify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `admin can create global quick reply`() {
        every { repository.save(any()) } answers {
            firstArg<CrmQuickReply>().copy(id = 2)
        }

        val dto = service.create(
            userId = 1,
            isAdmin = true,
            body = CrmQuickReplyBody(title = "Global", body = "Texto", global = true)
        )

        assertTrue(dto.global)
        assertEquals(null, dto.ownerUserId)
    }

    @Test
    fun `delete forbidden for other users personal reply`() {
        every { repository.findById(9) } returns Optional.of(
            CrmQuickReply(id = 9, title = "X", body = "Y", ownerUserId = 3)
        )

        assertThrows(CrmConversationForbiddenException::class.java) {
            service.delete(9, userId = 7, isAdmin = false)
        }
    }

    @Test
    fun `renderBody replaces placeholders`() {
        val text = service.renderBody("Hola {{clientName}}, tel {{phone}}", "Ana", "51999")
        assertEquals("Hola Ana, tel 51999", text)
    }
}
