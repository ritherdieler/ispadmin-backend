package com.dscorp.wispadmin.wispadmin.trafficclient

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/internal/traffic")
class TrafficDirectoryController(
    private val directoryService: TrafficDirectoryService,
) {
    @GetMapping("/targets")
    fun targets(): List<TrafficDirectoryEntryDto> = directoryService.list()
}
