package com.dscorp.wispadmin.wispadmin.service.provisioningv2

import com.dscorp.wispadmin.wispadmin.controller.ProvisioningV2Controller
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

class ProvisioningV2ControllerTest {
    private val service = mockk<ProvisioningControlService>()
    private val mvc = MockMvcBuilders.standaloneSetup(ProvisioningV2Controller(service)).build()

    @Test fun `retry accepts the existing operation and returns capability flags`() {
        every { service.retry(42, ProvisioningActionRequest("op", 1)) } returns ProvisioningProgress(
            ProvisioningOperation("op", "staging", 42, "ZTEGDC47BFFD"), false, true, false, false)
        mvc.perform(post("/subscription/42/provisioning/retry").contentType("application/json")
            .content("""{"operationId":"op","expectedRevision":1}"""))
            .andExpect(status().isAccepted).andExpect(jsonPath("$.operation.id").value("op"))
            .andExpect(jsonPath("$.canCancel").value(true))
    }

    @Test fun `cancel rejects a stale revision without leaking an exception`() {
        every { service.cancel(42, any()) } throws IllegalArgumentException("sensitive detail")
        mvc.perform(post("/subscription/42/provisioning/cancel").contentType("application/json")
            .content("""{"operationId":"op","expectedRevision":0}"""))
            .andExpect(status().isConflict)
    }

    @Test fun `foreign subscription operation is not exposed`() {
        every { service.progress(99, "op") } throws NoSuchElementException()
        mvc.perform(get("/subscription/99/provisioning").param("operationId", "op"))
            .andExpect(status().isNotFound)
    }
}
