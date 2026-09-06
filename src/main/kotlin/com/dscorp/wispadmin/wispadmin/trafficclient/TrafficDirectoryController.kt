package com.dscorp.wispadmin.wispadmin.trafficclient

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/internal/traffic")
class TrafficDirectoryController(
    private val directoryService: TrafficDirectoryService,
) {
    @GetMapping("/targets/page")
    fun page(
        @org.springframework.web.bind.annotation.RequestParam(defaultValue="0") after: Int,
        @org.springframework.web.bind.annotation.RequestParam(defaultValue="200") size: Int,
    ): TrafficTargetPage = directoryService.page(after, size)

    @GetMapping("/targets")
    fun targets(): List<TrafficDirectoryEntryDto> = directoryService.list()
}
