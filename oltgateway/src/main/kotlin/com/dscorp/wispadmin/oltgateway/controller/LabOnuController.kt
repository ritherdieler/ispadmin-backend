package com.dscorp.wispadmin.oltgateway.controller

import com.dscorp.wispadmin.oltgateway.config.GatewayCallContext
import com.dscorp.wispadmin.oltgateway.service.LabOnuRecord
import com.dscorp.wispadmin.oltgateway.service.LabOnuRegistry
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class LabOnuAddRequest(val sn: String = "")

@RestController
@RequestMapping("/api/olt-gateway/onu/lab")
@ConditionalOnExpression("'\${oltgateway.datasource.url:}'.trim().length() > 0")
class LabOnuController(
    private val registry: LabOnuRegistry,
) {
    @GetMapping
    fun list(): ResponseEntity<List<LabOnuRecord>> {
        if (!stagingCaller()) return ResponseEntity.status(403).build()
        return ResponseEntity.ok(registry.list())
    }

    @PostMapping
    fun add(@RequestBody body: LabOnuAddRequest): ResponseEntity<LabOnuRecord> {
        if (!stagingCaller()) return ResponseEntity.status(403).build()
        if (body.sn.isBlank()) return ResponseEntity.badRequest().build()
        return ResponseEntity.ok(registry.add(body.sn))
    }

    @DeleteMapping("/{sn}")
    fun remove(@PathVariable sn: String): ResponseEntity<Void> {
        if (!stagingCaller()) return ResponseEntity.status(403).build()
        registry.remove(sn)
        return ResponseEntity.noContent().build()
    }

    private fun stagingCaller(): Boolean = GatewayCallContext.labInventoryOnly()
}
