package com.dscorp.wispadmin.oltgateway.controller

import com.dscorp.wispadmin.oltgateway.api.SmartOltActionResponseDto
import com.dscorp.wispadmin.oltgateway.service.OnuGovernanceDeleteService
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/olt-gateway")
class OnuGovernanceController(
    private val governanceDelete: OnuGovernanceDeleteService,
) {
    @DeleteMapping("/onus/by-sn/{sn}")
    fun deleteBySn(@PathVariable sn: String): SmartOltActionResponseDto = governanceDelete.deleteBySn(sn)
}
