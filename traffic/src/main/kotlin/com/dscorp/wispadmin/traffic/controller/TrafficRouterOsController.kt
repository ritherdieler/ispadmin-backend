package com.dscorp.wispadmin.traffic.controller

import com.dscorp.wispadmin.traffic.dto.RouterOsAckResponse
import com.dscorp.wispadmin.traffic.dto.RouterOsAddRequest
import com.dscorp.wispadmin.traffic.dto.RouterOsCallRequest
import com.dscorp.wispadmin.traffic.dto.RouterOsPrintRequest
import com.dscorp.wispadmin.traffic.dto.RouterOsRemoveRequest
import com.dscorp.wispadmin.traffic.dto.RouterOsRowsResponse
import com.dscorp.wispadmin.traffic.dto.RouterOsSetRequest
import com.dscorp.wispadmin.traffic.service.RouterOsCommandUseCase
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/traffic/v1/routeros")
class TrafficRouterOsController(
    private val useCase: RouterOsCommandUseCase,
) {
    @PostMapping("/{hostDeviceId}/print")
    fun print(
        @PathVariable hostDeviceId: Int,
        @RequestBody request: RouterOsPrintRequest,
    ): RouterOsRowsResponse {
        return RouterOsRowsResponse(useCase.print(hostDeviceId, request).getOrThrow())
    }

    @PostMapping("/{hostDeviceId}/add")
    fun add(
        @PathVariable hostDeviceId: Int,
        @RequestBody request: RouterOsAddRequest,
    ): RouterOsAckResponse {
        useCase.add(hostDeviceId, request).getOrThrow()
        return RouterOsAckResponse()
    }

    @PostMapping("/{hostDeviceId}/set")
    fun set(
        @PathVariable hostDeviceId: Int,
        @RequestBody request: RouterOsSetRequest,
    ): RouterOsAckResponse {
        useCase.set(hostDeviceId, request).getOrThrow()
        return RouterOsAckResponse()
    }

    @PostMapping("/{hostDeviceId}/remove")
    fun remove(
        @PathVariable hostDeviceId: Int,
        @RequestBody request: RouterOsRemoveRequest,
    ): RouterOsAckResponse {
        useCase.remove(hostDeviceId, request).getOrThrow()
        return RouterOsAckResponse()
    }

    @PostMapping("/{hostDeviceId}/call")
    fun call(
        @PathVariable hostDeviceId: Int,
        @RequestBody request: RouterOsCallRequest,
    ): RouterOsRowsResponse {
        return RouterOsRowsResponse(useCase.call(hostDeviceId, request).getOrThrow())
    }
}
