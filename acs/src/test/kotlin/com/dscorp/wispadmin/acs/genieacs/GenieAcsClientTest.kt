package com.dscorp.wispadmin.acs.genieacs

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.web.client.RestTemplate

import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import io.mockk.mockk
import io.mockk.verify
import com.dscorp.wispadmin.acs.service.AcsFaultArchive
import org.springframework.http.HttpMethod
import org.springframework.test.web.client.match.MockRestRequestMatchers.method

class GenieAcsClientTest {
    @Test
    fun `deleting one fault snapshots every current fault before resolving the deleted one`() {
        val properties = GenieAcsProperties().apply { nbiBaseUrl = "http://genieacs.test" }
        val http = RestTemplate()
        val server = MockRestServiceServer.createServer(http)
        val archive = mockk<AcsFaultArchive>(relaxed = true)
        val client = GenieAcsClient(properties, ObjectMapper(), http)
        client.setFaultArchive(archive)
        server.expect(requestTo("http://genieacs.test/faults/?query=%7B%22_id%22:%22fault-1%22%7D"))
            .andRespond(withSuccess("""[{"_id":"fault-1","device":"dev"}]""", MediaType.APPLICATION_JSON))
        server.expect(requestTo("http://genieacs.test/faults/?query=%7B%22device%22:%22dev%22%7D"))
            .andRespond(withSuccess("""[{"_id":"fault-1","device":"dev"},{"_id":"fault-2","device":"dev"}]""", MediaType.APPLICATION_JSON))
        server.expect(requestTo("http://genieacs.test/faults/fault-1")).andExpect(method(HttpMethod.DELETE))
            .andRespond(withSuccess("", MediaType.APPLICATION_JSON))
        server.expect(requestTo("http://genieacs.test/faults/?query=%7B%22device%22:%22dev%22%7D"))
            .andRespond(withSuccess("""[{"_id":"fault-2","device":"dev"}]""", MediaType.APPLICATION_JSON))

        org.junit.jupiter.api.Assertions.assertEquals(true, client.deleteFault("fault-1"))
        verify { archive.captureKnownDevice("dev", match { it.size == 2 && it.any { fault -> fault.id == "fault-2" } }, any()) }
        server.verify()
    }
    @Test
    fun `inspection reads only the selected cached device and its faults`() {
        val properties = GenieAcsProperties().apply { nbiBaseUrl = "http://genieacs.test" }
        val restTemplate = RestTemplate()
        val server = MockRestServiceServer.createServer(restTemplate)
        server.expect(requestTo("http://genieacs.test/devices/?query=%7B%22_id%22:%22device-1%22%7D"))
            .andRespond(withSuccess("""[{"_id":"device-1","_lastInform":"2026-10-02T07:00:00Z"}]""", MediaType.APPLICATION_JSON))
        server.expect(requestTo("http://genieacs.test/faults/?query=%7B%22device%22:%22device-1%22%7D"))
            .andRespond(withSuccess("""[{"_id":"device-1:inform","device":"device-1"}]""", MediaType.APPLICATION_JSON))

        val client = GenieAcsClient(properties, ObjectMapper(), restTemplate)

        assertEquals("device-1", client.getDeviceSnapshot("device-1")?.path("_id")?.asText())
        assertEquals(1, client.listFaultDocuments("device-1").size)
        server.verify()
    }

    @Test
    fun `completed task is not pending when it is absent from the NBI task list`() {
        val properties = GenieAcsProperties().apply { nbiBaseUrl = "http://genieacs.test" }
        val restTemplate = RestTemplate()
        val server = MockRestServiceServer.createServer(restTemplate)
        server.expect(requestTo("http://genieacs.test/tasks/?query=%7B%22_id%22:%22task-123%22%7D"))
            .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON))

        val client = GenieAcsClient(properties, ObjectMapper(), restTemplate)

        assertFalse(client.isTaskPending("task-123"))
        server.verify()
    }
}
