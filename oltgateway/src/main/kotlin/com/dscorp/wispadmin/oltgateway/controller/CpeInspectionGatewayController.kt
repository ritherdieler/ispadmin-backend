package com.dscorp.wispadmin.oltgateway.controller

import com.dscorp.wispadmin.oltgateway.client.AcsInspectionProxy
import com.dscorp.wispadmin.oltgateway.config.GatewayCallContext
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/api/olt-gateway/onus/{sn}/cpe/inspection")
class CpeInspectionGatewayController(private val proxy: AcsInspectionProxy) {
    @GetMapping("/summary")
    fun summary(@PathVariable sn: String) = proxy.summary(sn, GatewayCallContext.env())

    @GetMapping("/tree")
    fun tree(@PathVariable sn: String, @RequestParam(required = false) parent: String?,
             @RequestParam(required = false, name = "q") query: String?) =
        proxy.tree(sn, parent, query, GatewayCallContext.env())

    @GetMapping("/faults/current")
    fun currentFaults(@PathVariable sn: String) = proxy.currentFaults(sn, GatewayCallContext.env())

    @GetMapping("/faults/history")
    fun faultHistory(@PathVariable sn: String, @RequestParam(defaultValue = "0") page: Int,
                     @RequestParam(defaultValue = "20") size: Int): com.fasterxml.jackson.databind.JsonNode {
        if (page < 0 || size !in 1..100) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid pagination")
        return proxy.faultHistory(sn, page, size, GatewayCallContext.env())
    }
}
