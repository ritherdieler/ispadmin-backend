package com.dscorp.wispadmin.observability.controller

import com.dscorp.wispadmin.observability.config.ObservabilityApiKeyFilter
import com.dscorp.wispadmin.observability.config.ObservabilityProperties
import com.dscorp.wispadmin.observability.service.ObsReplayService
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.util.StreamUtils
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import javax.servlet.http.HttpServletRequest

@RestController
@RequestMapping("/observability/replays")
class ObservabilityReplayController(
    private val replayService: ObsReplayService,
    private val properties: ObservabilityProperties
) {

    @PostMapping
    fun upload(
        @RequestParam("sessionId", required = false) sessionId: String?,
        @RequestParam("workflowId", required = false) workflowId: String?,
        @RequestParam("eventId", required = false) eventId: Long?,
        @RequestParam("issueId", required = false) issueId: Long?,
        @RequestParam("platform", required = false) platform: String?,
        @RequestParam("format", required = false) format: String?,
        @RequestParam("durationMs", required = false) durationMs: Long?,
        @RequestParam("file", required = false) file: MultipartFile?,
        request: HttpServletRequest
    ): ResponseEntity<Map<String, Any?>> {
        if (!properties.enabled) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build()
        }

        val platformFromKey = request.getAttribute(ObservabilityApiKeyFilter.PLATFORM_ATTRIBUTE) as? String
        val data: ByteArray = when {
            file != null && !file.isEmpty -> file.bytes
            else -> StreamUtils.copyToByteArray(request.inputStream)
        }

        if (data.isEmpty()) {
            return ResponseEntity.badRequest().body(mapOf("error" to "empty_body"))
        }
        if (data.size > properties.replay.maxUploadBytes) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(mapOf("error" to "payload_too_large", "maxBytes" to properties.replay.maxUploadBytes))
        }

        val encoding = request.getHeader(HttpHeaders.CONTENT_ENCODING) ?: "gzip"
        val replay = replayService.store(
            sessionId = sessionId,
            eventId = eventId,
            issueId = issueId,
            platform = platform ?: platformFromKey,
            format = format,
            durationMs = durationMs,
            contentEncoding = encoding,
            data = data,
            workflowId = workflowId
        )

        return ResponseEntity.status(HttpStatus.CREATED).body(
            mapOf(
                "id" to replay.id,
                "sessionId" to replay.sessionId,
                "workflowId" to replay.workflowId,
                "format" to replay.format,
                "sizeBytes" to replay.sizeBytes,
                "contentEncoding" to replay.contentEncoding
            )
        )
    }

    @GetMapping("/{id}")
    fun download(@PathVariable id: Long): ResponseEntity<ByteArray> {
        val replay = replayService.findMetadata(id) ?: return ResponseEntity.notFound().build()
        val blob = replayService.readBlob(replay) ?: return ResponseEntity.notFound().build()

        val builder = ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_JSON)
        if ("gzip".equals(replay.contentEncoding, ignoreCase = true)) {
            builder.header(HttpHeaders.CONTENT_ENCODING, "gzip")
        }
        return builder.body(blob)
    }
}
