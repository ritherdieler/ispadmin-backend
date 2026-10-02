package com.dscorp.wispadmin.acs.controller

import com.dscorp.wispadmin.acs.service.CpeInspectionService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/acs/v1/cpe/{sn}/inspection")
class AcsInspectionController(private val service: CpeInspectionService) {
    @GetMapping("/summary")
    fun summary(@PathVariable sn: String) = service.summary(sn)

    @GetMapping("/tree")
    fun tree(@PathVariable sn: String, @RequestParam(required = false) parent: String?,
             @RequestParam(required = false) q: String?) = service.tree(sn, parent, q)

    @GetMapping("/faults/current")
    fun currentFaults(@PathVariable sn: String) = service.currentFaults(sn)

    @GetMapping("/faults/history")
    fun faultHistory(@PathVariable sn: String, @RequestParam(defaultValue = "0") page: Int,
                     @RequestParam(defaultValue = "20") size: Int) = service.faultHistory(sn, page, size)
}
