package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.dto.FcmTokenRequestDto
import com.dscorp.wispadmin.wispadmin.dto.FcmTokenResponseDto
import com.dscorp.wispadmin.wispadmin.dto.toEntity
import com.dscorp.wispadmin.wispadmin.dto.toResponseDto
import com.dscorp.wispadmin.wispadmin.repository.FcmTokenRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class FcmTokenService(
    private val fcmTokenRepository: FcmTokenRepository
) {

    companion object {
        private val logger = LoggerFactory.getLogger(FcmTokenService::class.java)
    }

    @Transactional
    fun saveToken(request: FcmTokenRequestDto): FcmTokenResponseDto {
        logger.info("Registrando token FCM para suscripción {}", request.subscriptionId)
        return fcmTokenRepository.save(request.toEntity()).toResponseDto()
    }

    @Transactional(readOnly = true)
    fun getBySubscriptionId(subscriptionId: Int): FcmTokenResponseDto =
        fcmTokenRepository.findById(subscriptionId).orElseThrow().toResponseDto()
}
