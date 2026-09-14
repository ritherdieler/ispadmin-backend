package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.dto.AppVersionResponseDto
import com.dscorp.wispadmin.wispadmin.service.AppVersionService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/app")
class AppVersionController(
    private val appVersionService: AppVersionService
) {
    @GetMapping("check_version")
    fun getLastVersion(): ResponseEntity<AppVersionResponseDto> =
        appVersionService.getLastVersion()?.let { ResponseEntity.ok(it) }
            ?: ResponseEntity.notFound().build()
}
