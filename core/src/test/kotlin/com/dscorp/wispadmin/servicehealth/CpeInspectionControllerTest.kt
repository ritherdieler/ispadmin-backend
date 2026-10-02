package com.dscorp.wispadmin.servicehealth

import com.dscorp.wispadmin.servicehealth.controller.CpeInspectionController
import com.dscorp.wispadmin.servicehealth.controller.HealthAccess
import com.dscorp.wispadmin.servicehealth.controller.HealthActor
import com.dscorp.wispadmin.servicehealth.port.CpeInspectionPort
import com.dscorp.wispadmin.servicehealth.port.SubscriptionDirectoryPort
import com.dscorp.wispadmin.servicehealth.port.SubscriptionHealthRef
import com.dscorp.wispadmin.servicehealth.service.IdentityService
import com.dscorp.wispadmin.servicehealth.repository.RemoteActionRepository
import com.dscorp.wispadmin.servicehealth.domain.RemoteAction
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.server.ResponseStatusException

class CpeInspectionControllerTest {
    private val access = mockk<HealthAccess>()
    private val directory = mockk<SubscriptionDirectoryPort>()
    private val identity = mockk<IdentityService>()
    private val port = mockk<CpeInspectionPort>()
    private val actions = mockk<RemoteActionRepository>()
    private val controller = CpeInspectionController(access, directory, identity, port, actions)
    private val request = MockHttpServletRequest()
    private val json = ObjectMapper()
    private val subscription = SubscriptionHealthRef(
        id = 12, onuSn = "ZTEGDC47BFFD", ip = null, vlan = null, hostDeviceId = null,
        planId = null, planDownloadMbps = null, planUploadMbps = null, napBoxId = null,
        serviceStatus = "ACTIVE",
    )

    @Test
    fun `technician can see summary but tree requires admin`() {
        every { directory.find(12) } returns subscription
        every { identity.resolveOnu("ZTEGDC47BFFD") } returns 12
        every { access.require(request, false) } returns HealthActor(1, "TECHNICIAN")
        every { access.require(request, true) } throws ResponseStatusException(HttpStatus.FORBIDDEN)
        every { port.summary("ZTEGDC47BFFD") } returns json.readTree("""{"productClass":"F6600R"}""")

        assertEquals("F6600R", controller.summary(12, request).path("productClass").asText())
        assertEquals(403, assertThrows(ResponseStatusException::class.java) {
            controller.tree(12, null, null, request)
        }.rawStatusCode)
        verify(exactly = 0) { port.tree(any(), any(), any()) }
    }

    @Test
    fun `ambiguous ONU association prevents fault disclosure`() {
        every { directory.find(12) } returns subscription
        every { identity.resolveOnu("ZTEGDC47BFFD") } returns null
        every { access.require(request, true) } returns HealthActor(2, "ADMIN")

        assertEquals(409, assertThrows(ResponseStatusException::class.java) {
            controller.currentFaults(12, request)
        }.rawStatusCode)
        verify(exactly = 0) { port.currentFaults(any()) }
    }

    @Test
    fun `fault history links a matching task to this subscription action`() {
        every { directory.find(12) } returns subscription
        every { identity.resolveOnu("ZTEGDC47BFFD") } returns 12
        every { access.require(request, true) } returns HealthActor(2, "ADMIN")
        every { port.faultHistory("ZTEGDC47BFFD", 0, 20) } returns json.readTree("""{
          "items":[{"id":"fault-1","taskId":"task-42"}],"page":0,"size":20,"total":1
        }""")
        every { actions.findTopBySubscriptionIdAndTaskIdOrderByCreatedAtDesc(12, "task-42") } returns RemoteAction(
            id = 99L, subscriptionId = 12, acsDeviceId = "ZTEGDC47BFFD", taskId = "task-42",
        )

        val page = controller.faultHistory(12, 0, 20, request)
        assertEquals(99L, page.path("items")[0].path("actionId").asLong())
    }
}
