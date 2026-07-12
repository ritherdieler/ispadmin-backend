package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.dto.AppVersionResponseDto
import com.dscorp.wispadmin.wispadmin.dto.toResponseDto
import com.dscorp.wispadmin.wispadmin.repository.AppVersionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AppVersionService(
    private val appVersionRepository: AppVersionRepository
) {

    @Transactional(readOnly = true)
    fun getLastVersion(): AppVersionResponseDto? =
        appVersionRepository.findAll().firstOrNull()?.toResponseDto()
}
