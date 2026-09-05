package com.dscorp.wispadmin.acs.controller

import com.dscorp.wispadmin.acs.CpeCommandResult
import com.dscorp.wispadmin.acs.CpeProvisionCommand
import com.dscorp.wispadmin.acs.CpeProvisionResult
import com.dscorp.wispadmin.acs.CpeTelemetryResult
import com.dscorp.wispadmin.acs.service.CpeFacadeService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/acs/v1")
class AcsCpeController(
    private val facade: CpeFacadeService,
) {
    @GetMapping("/health")
    fun health(): Map<String, String> = mapOf("status" to "UP")

    @PostMapping("/cpe/provision")
    fun provision(@RequestBody command: CpeProvisionCommand): CpeProvisionResult = facade.provision(command)

    @GetMapping("/cpe/{sn}/status")
    fun status(@PathVariable sn: String): CpeProvisionResult = facade.status(sn)

    @GetMapping("/cpe/{sn}/telemetry")
    fun telemetry(@PathVariable sn: String): CpeTelemetryResult = facade.telemetry(sn)

    @PostMapping("/cpe/{sn}/reboot")
    fun reboot(@PathVariable sn: String): CpeCommandResult = facade.reboot(sn)

    @PostMapping("/cpe/{sn}/wifi-refresh")
    fun wifiRefresh(@PathVariable sn: String): CpeCommandResult = facade.wifiRefresh(sn)
}
