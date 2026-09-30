package com.dscorp.wispadmin.acs.genieacs

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.web.client.RestTemplate

import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess

class GenieAcsClientTest {
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
