package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.security.PlatformAuthFilter
import com.dscorp.wispadmin.wispadmin.service.subscription.AccessMigrationQuarantineJob
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockHttpServletRequest

class AccessMigrationAdminControllerTest {

    private val job = mockk<AccessMigrationQuarantineJob>()
    private val controller = AccessMigrationAdminController(job)

    @Test
    fun `ADMIN can finish due quarantines while scheduling is off`() {
        every { job.finishDueQuarantines() } returns 2

        val response = controller.finishDueQuarantines(request("ADMIN"))

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(2, (response.body as Map<*, *>)["processed"])
        verify { job.finishDueQuarantines() }
    }

    @Test
    fun `SECRETARY cannot finish due quarantines`() {
        val response = controller.finishDueQuarantines(request("SECRETARY"))

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        verify(exactly = 0) { job.finishDueQuarantines() }
    }

    private fun request(userType: String): MockHttpServletRequest {
        val request = MockHttpServletRequest()
        request.setAttribute(PlatformAuthFilter.AUTH_USER_TYPE_ATTRIBUTE, userType)
        return request
    }
}
