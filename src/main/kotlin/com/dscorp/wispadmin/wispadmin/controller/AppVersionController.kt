package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.data.model.AppVersion
import com.dscorp.wispadmin.wispadmin.repository.AppVersionRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController


@RestController
@RequestMapping("/app")
class AppVersionController @Autowired constructor(
    private val appVersionRepository: AppVersionRepository
) {
    @GetMapping("check_version")
    fun getLastVersion(): ResponseEntity<AppVersion> {
        return try {
            val appVersion = appVersionRepository.findAll().first()
            ResponseEntity.ok(appVersion)
        } catch (e: Exception) {
            e.printStackTrace()
            ResponseEntity.notFound().build()
        }
    }
}


