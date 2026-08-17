package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOnuRepository
import com.dscorp.wispadmin.wispadmin.dto.CpeStatusDto
import com.dscorp.wispadmin.wispadmin.dto.UpdateWifiRequest
import com.dscorp.wispadmin.wispadmin.dto.UpdateWifiResponseDto
import com.dscorp.wispadmin.wispadmin.genieacs.GenieAcsClient
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
class CpeManagementService(
    private val subscriptionRepository: SubscriptionRepository,
    private val oltMgrOnuRepository: OltMgrOnuRepository,
    private val genieAcsClient: GenieAcsClient,
) {
    private val logger = LoggerFactory.getLogger(CpeManagementService::class.java)

    @Transactional(readOnly = true)
    fun getCpeStatus(subscriptionId: Int): CpeStatusDto {
        val subscription = subscriptionRepository.findById(subscriptionId).orElseThrow {
            IllegalArgumentException("Suscripción no encontrada")
        }
        val sn = subscription.fiberOnu?.sn?.takeIf { it.isNotBlank() }
            ?: return CpeStatusDto(online = false, rxDbm = null, lastInform = null, sn = null)

        val oltOnu = oltMgrOnuRepository.findBySnIgnoreCaseAndDeletedAtIsNull(sn).orElse(null)
        val status = oltOnu?.status
        val lastInform = runCatching { genieAcsClient.getLastInform(sn) }
            .onFailure { error ->
                logger.warn("GenieACS lastInform unavailable for SN {}: {}", sn, error.message)
            }
            .getOrNull()

        return CpeStatusDto(
            online = status?.runState.equals("online", ignoreCase = true),
            rxDbm = formatRxDbm(status?.onuRxDbm),
            lastInform = lastInform,
            sn = sn
        )
    }

    fun updateWifi(subscriptionId: Int, request: UpdateWifiRequest): UpdateWifiResponseDto {
        val ssid = request.ssid.trim()
        val password = request.password
        if (ssid.isBlank()) {
            throw IllegalArgumentException("El nombre de red (SSID) es obligatorio")
        }
        if (password.length < MIN_WIFI_PASSWORD_LENGTH) {
            throw IllegalArgumentException("La clave Wi-Fi debe tener al menos $MIN_WIFI_PASSWORD_LENGTH caracteres")
        }
        val subscription = subscriptionRepository.findById(subscriptionId).orElseThrow {
            IllegalArgumentException("Suscripción no encontrada")
        }
        val sn = subscription.fiberOnu?.sn?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("La suscripción no tiene ONU registrada")
        genieAcsClient.updateWifi(sn, ssid, password)
        return UpdateWifiResponseDto(
            message = "Se envió el cambio de Wi-Fi al equipo",
            subscriptionId = subscriptionId
        )
    }

    private fun formatRxDbm(value: BigDecimal?): String? {
        if (value == null) return null
        return value.stripTrailingZeros().toPlainString()
    }

    companion object {
        const val MIN_WIFI_PASSWORD_LENGTH = 8
    }
}
