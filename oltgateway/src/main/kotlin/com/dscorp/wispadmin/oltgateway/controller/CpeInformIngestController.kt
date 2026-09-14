package com.dscorp.wispadmin.oltgateway.controller

import com.dscorp.wispadmin.events.CpeInformPayload
import com.dscorp.wispadmin.oltgateway.service.CpeInformIngestService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/olt-gateway")
class CpeInformIngestController(
    private val ingest: CpeInformIngestService,
) {
    @PostMapping("/acs/cpe-inform")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun ingest(@RequestBody payload: CpeInformPayload): Map<String, Any> {
        require(payload.sn.isNotBlank()) { "sn required" }
        require(payload.deviceId.isNotBlank()) { "deviceId required" }
        ingest.ingest(payload)
        return mapOf("accepted" to true, "type" to "cpe.inform")
    }
}
