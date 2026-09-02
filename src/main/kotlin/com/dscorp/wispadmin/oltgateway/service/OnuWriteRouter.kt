package com.dscorp.wispadmin.oltgateway.service

import com.dscorp.wispadmin.oltgateway.api.AuthorizeOnuFormDto
import com.dscorp.wispadmin.oltgateway.api.MoveOnuFormDto
import com.dscorp.wispadmin.oltgateway.api.SmartOltActionResponseDto
import com.dscorp.wispadmin.oltgateway.config.OltGatewayProperties
import com.dscorp.wispadmin.oltgateway.config.OnuWriteProvider
import com.dscorp.wispadmin.oltgateway.config.OnuWriteProviderProperties
import com.dscorp.wispadmin.wispadmin.data.model.NapBox
import com.dscorp.wispadmin.wispadmin.data.model.Onu
import com.dscorp.wispadmin.wispadmin.requestbody.smartoltrequest.MoveOnuRequest
import com.dscorp.wispadmin.wispadmin.requestbody.smartoltrequest.OnuAuthorizationRequest
import com.dscorp.wispadmin.wispadmin.service.OltService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service

@Service
class OnuWriteRouter(
    private val oltService: OltService,
    private val oltManagerFacade: ObjectProvider<OltManagerFacade>,
    private val providers: OnuWriteProviderProperties,
    private val gatewayProperties: ObjectProvider<OltGatewayProperties>
) {

    fun authorize(request: AuthorizeOnuFormDto): SmartOltActionResponseDto {
        gatewayFor(providers.authorize)?.let { facade ->
            return facade.authorizeOnu(request)
        }

        oltService.authorizeOnuInSmartOltWidthPostMethod(request.toLegacyAuthorizationRequest())
        if (providers.authorizeShadow) {
            runShadow(request)
        }
        return SmartOltActionResponseDto(status = true, message = "authorized via SmartOLT")
    }

    fun authorize(request: OnuAuthorizationRequest): SmartOltActionResponseDto = authorize(
        AuthorizeOnuFormDto(
            olt_id = request.olt_id,
            pon_type = request.pon_type,
            board = request.board,
            port = request.port,
            sn = request.sn,
            vlan = request.vlan,
            onu_type = request.onu_type,
            zone = request.zone,
            name = request.name,
            onu_mode = request.onu_mode,
            custom_profile = request.custom_profile
        )
    )

    fun delete(externalId: String): SmartOltActionResponseDto {
        gatewayFor(providers.delete)?.let { facade ->
            return facade.deleteOnu(externalId)
        }
        val smartOltId = smartOltExternalIdFor(externalId)
        oltService.deleteOnu(smartOltId)
        return SmartOltActionResponseDto(status = true, unique_external_id = smartOltId)
    }

    fun reboot(externalId: String): SmartOltActionResponseDto {
        gatewayFor(providers.reboot)?.let { facade ->
            return facade.rebootOnu(externalId)
        }
        val smartOltId = smartOltExternalIdFor(externalId)
        oltService.rebootOnu(smartOltId)
        return SmartOltActionResponseDto(status = true, unique_external_id = smartOltId)
    }

    fun deleteBySn(sn: String): SmartOltActionResponseDto {
        gatewayFor(providers.delete)?.let { facade ->
            return facade.deleteOnu(gatewayExternalIdBySn(facade, sn))
        }
        val smartOltId = smartOltExternalIdBySn(sn)
        oltService.deleteOnu(smartOltId)
        return SmartOltActionResponseDto(status = true, unique_external_id = smartOltId)
    }

    fun rebootBySn(sn: String): SmartOltActionResponseDto {
        gatewayFor(providers.reboot)?.let { facade ->
            return facade.rebootOnu(gatewayExternalIdBySn(facade, sn))
        }
        val smartOltId = smartOltExternalIdBySn(sn)
        oltService.rebootOnu(smartOltId)
        return SmartOltActionResponseDto(status = true, unique_external_id = smartOltId)
    }

    fun move(request: MoveOnuRequest, onu: Onu, newNapBox: NapBox): SmartOltActionResponseDto {
        gatewayFor(providers.move)?.let { facade ->
            return facade.moveOnu(
                onu.sn,
                MoveOnuFormDto(
                    olt_id = newNapBox.oltId?.toString().orEmpty(),
                    board = newNapBox.oltBoard?.toString().orEmpty(),
                    port = newNapBox.oltPort?.toString().orEmpty()
                )
            )
        }
        oltService.moveOnu(request, onu, newNapBox)
        return SmartOltActionResponseDto(status = true)
    }

    private fun gatewayFor(provider: OnuWriteProvider): OltManagerFacade? {
        if (provider != OnuWriteProvider.GATEWAY) return null
        val facade = oltManagerFacade.getIfAvailable()
        if (facade == null) {
            logger.warn("Escritura enrutada al gateway pero olt.gateway.enabled=false; se aplica por SmartOLT")
        }
        return facade
    }

    private fun gatewayExternalIdBySn(facade: OltManagerFacade, sn: String): String {
        val onu = facade.getOnusDetailsBySn(sn).onus.firstOrNull()
            ?: throw IllegalArgumentException(ONU_NOT_FOUND)
        return onu.unique_external_id.ifBlank { throw IllegalStateException(MISSING_EXTERNAL_ID) }
    }

    private fun smartOltExternalIdBySn(sn: String): String {
        val onu = oltService.getOnuBySn(sn).onus.firstOrNull()
            ?: throw IllegalArgumentException(ONU_NOT_FOUND)
        return onu.unique_external_id.ifBlank { throw IllegalStateException(MISSING_EXTERNAL_ID) }
    }

    private fun smartOltExternalIdFor(externalId: String): String {
        val facade = oltManagerFacade.getIfAvailable() ?: return externalId
        val oltId = gatewayProperties.getIfAvailable()?.oltId?.takeIf { it.isNotBlank() } ?: return externalId
        if (!OnuExternalIdPolicy.isCanonical(externalId, oltId)) return externalId
        val sn = facade.findSnByExternalId(externalId) ?: return externalId
        return smartOltExternalIdBySn(sn)
    }

    private fun runShadow(request: AuthorizeOnuFormDto) {
        val facade = oltManagerFacade.getIfAvailable() ?: return
        try {
            facade.recordAuthorizeShadow(request, readAppliedAuthorization(request.sn))
        } catch (ex: Exception) {
            logger.warn("Modo sombra de authorize falló para SN={}: {}", request.sn, ex.message)
        }
    }

    private fun readAppliedAuthorization(sn: String): AppliedAuthorization? = try {
        oltService.getOnuBySn(sn).onus.firstOrNull()?.let { applied ->
            AppliedAuthorization(
                board = applied.board.toIntOrNull(),
                port = applied.port.toIntOrNull(),
                ontId = applied.onu.toIntOrNull(),
                externalId = applied.unique_external_id
            )
        }
    } catch (ex: Exception) {
        logger.warn("No se pudo leer en SmartOLT la ONU recién autorizada SN={}: {}", sn, ex.message)
        null
    }

    private fun AuthorizeOnuFormDto.toLegacyAuthorizationRequest() = OnuAuthorizationRequest(
        olt_id = olt_id,
        pon_type = pon_type,
        board = board,
        port = port,
        sn = sn,
        vlan = vlan,
        onu_type = onu_type,
        zone = zone,
        name = name,
        onu_mode = onu_mode,
        custom_profile = custom_profile
    )

    companion object {
        private const val ONU_NOT_FOUND = "No se encontró la ONU en SmartOLT para el serial indicado"
        private const val MISSING_EXTERNAL_ID = "La ONU no tiene identificador externo en SmartOLT"
        private val logger = LoggerFactory.getLogger(OnuWriteRouter::class.java)
    }
}
