package com.dscorp.wispadmin.wispadmin.service.genieacs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.net.URI

class GenieAcsCurlLoggerTest {

    @Test
    fun `formatResponse includes http status and body`() {
        val formatted = GenieAcsCurlLogger.formatResponse(
            statusCode = 202,
            body = """{"_id":"task-1"}""",
        )

        assertEquals(
            """
            HTTP 202
            {"_id":"task-1"}
            """.trimIndent(),
            formatted,
        )
    }

    @Test
    fun `formatResponse shows empty body placeholder`() {
        assertEquals("HTTP 400\n(empty body)", GenieAcsCurlLogger.formatResponse(400, null))
    }

    @Test
    fun `formatPostTask includes url headers and json body`() {
        val uri = URI("http://127.0.0.1:7557/devices/B46415-V2804AX15T-12345/tasks?connection_request")
        val body = """{"name":"setParameterValues","parameterValues":[["path","value","xsd:string"]]}"""

        val curl = GenieAcsCurlLogger.formatPostTask(uri, body)

        assertEquals(
            """
            curl -X POST 'http://127.0.0.1:7557/devices/B46415-V2804AX15T-12345/tasks?connection_request' \
              -H 'Content-Type: application/json' \
              -d '{"name":"setParameterValues","parameterValues":[["path","value","xsd:string"]]}'
            """.trimIndent(),
            curl,
        )
    }

    @Test
    fun `formatGet escapes single quotes in url`() {
        val uri = URI("http://127.0.0.1:7557/devices/?query=%7B%7D")

        val curl = GenieAcsCurlLogger.formatGet(uri)

        assertEquals(
            "curl -X GET 'http://127.0.0.1:7557/devices/?query=%7B%7D'",
            curl,
        )
    }

    @Test
    fun `formatPostTask escapes single quotes in json body`() {
        val uri = URI("http://127.0.0.1:7557/devices/dev-1/tasks")
        val body = """{"name":"setParameterValues","parameterValues":[["SSID","it's","xsd:string"]]}"""

        val curl = GenieAcsCurlLogger.formatPostTask(uri, body)

        assertEquals(
            """
            curl -X POST 'http://127.0.0.1:7557/devices/dev-1/tasks' \
              -H 'Content-Type: application/json' \
              -d '{"name":"setParameterValues","parameterValues":[["SSID","it'"'"'s","xsd:string"]]}'
            """.trimIndent(),
            curl,
        )
    }
}
