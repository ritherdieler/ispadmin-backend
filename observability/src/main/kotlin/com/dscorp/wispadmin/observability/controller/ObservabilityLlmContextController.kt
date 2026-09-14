package com.dscorp.wispadmin.observability.controller

import com.dscorp.wispadmin.observability.dto.LlmContextDto
import com.dscorp.wispadmin.observability.service.ObsLlmContextService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@RestController
@RequestMapping("/observability")
class ObservabilityLlmContextController(
    private val llmContextService: ObsLlmContextService
) {

    @GetMapping("/issues/{id}/llm-context")
    fun issueContext(@PathVariable id: Long): ResponseEntity<LlmContextDto> {
        val content = llmContextService.buildIssueContext(id)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(response(content))
    }

    @GetMapping("/traces/{traceId}/llm-context")
    fun traceContext(@PathVariable traceId: String): ResponseEntity<LlmContextDto> {
        val content = llmContextService.buildTraceContext(traceId)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(response(content))
    }

    @GetMapping("/sessions/{sessionId}/llm-context")
    fun sessionContext(@PathVariable sessionId: String): ResponseEntity<LlmContextDto> {
        val content = llmContextService.buildSessionContext(sessionId)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(response(content))
    }

    private fun response(content: String) = LlmContextDto(
        format = "markdown",
        content = content,
        generatedAt = Instant.now().toString()
    )
}
