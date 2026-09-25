package com.dscorp.wispadmin.acs.exception

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

class AcsExceptionHandlerTest {
    private val handler = AcsExceptionHandler()

    @Test fun `conflict response keeps the ACS reason`() {
        val response = handler.handleStatus(ResponseStatusException(HttpStatus.CONFLICT, "ACS_WAN_REJECTED password=secret-value"))
        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals("ACS_WAN_REJECTED password=<redacted>", response.body!!.message)
    }

    @Test fun `rejected operation returns the ACS reason`() {
        val response = handler.handleState(IllegalStateException("ACS_DEVICE_OFFLINE"))
        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals("ACS_DEVICE_OFFLINE", response.body!!.message)
    }
}
