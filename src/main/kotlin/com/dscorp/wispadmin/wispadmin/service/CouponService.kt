package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.dto.CouponRequestDto
import com.dscorp.wispadmin.wispadmin.dto.CouponResponseDto
import com.dscorp.wispadmin.wispadmin.dto.toEntity
import com.dscorp.wispadmin.wispadmin.dto.toResponseDto
import com.dscorp.wispadmin.wispadmin.repository.CouponRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CouponService(
    private val couponRepository: CouponRepository
) {

    companion object {
        private val logger = LoggerFactory.getLogger(CouponService::class.java)
    }

    @Transactional
    fun register(request: CouponRequestDto): CouponResponseDto {
        logger.info("Registrando cupón con código {}", request.code)
        return couponRepository.save(request.toEntity()).toResponseDto()
    }

    @Transactional(readOnly = true)
    fun findAll(): List<CouponResponseDto> =
        couponRepository.findAll().map { it.toResponseDto() }
}
