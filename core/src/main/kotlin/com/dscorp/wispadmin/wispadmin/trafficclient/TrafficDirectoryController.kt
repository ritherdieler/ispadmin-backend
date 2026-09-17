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

    @GetMapping("/targets/{id}")
    fun target(@org.springframework.web.bind.annotation.PathVariable id: Int): TrafficDirectoryEntryDto {
        return directoryService.find(id) ?: throw org.springframework.web.server.ResponseStatusException(
            org.springframework.http.HttpStatus.NOT_FOUND,
            "Traffic target $id not found",
        )
    }

    @GetMapping("/targets")
    fun targets(): List<TrafficDirectoryEntryDto> = directoryService.list()
}
