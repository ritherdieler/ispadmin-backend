package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.oltgateway.api.AuthorizeOnuFormDto
import com.dscorp.wispadmin.oltgateway.api.SmartOltActionResponseDto
import com.dscorp.wispadmin.oltgateway.dto.BoardPortCatalogDto
import com.dscorp.wispadmin.oltgateway.dto.ConfiguredOnuDetailDto
import com.dscorp.wispadmin.oltgateway.dto.ConfiguredOnuFilter
import com.dscorp.wispadmin.oltgateway.dto.ConfiguredOnuHistoryDto
import com.dscorp.wispadmin.oltgateway.dto.ConfiguredOnuItemDto
import com.dscorp.wispadmin.oltgateway.dto.ConfiguredOnuLiveStatusDto
import com.dscorp.wispadmin.oltgateway.dto.ConfiguredOnuPageDto
import com.dscorp.wispadmin.oltgateway.dto.OnuCatalogsDto
import com.dscorp.wispadmin.oltgateway.dto.SignalPollResultDto
import com.dscorp.wispadmin.oltgateway.dto.SmartOltImportResultDto
import com.dscorp.wispadmin.oltgateway.dto.SyncResultDto
import com.dscorp.wispadmin.oltgateway.service.OltInventorySyncService
import com.dscorp.wispadmin.oltgateway.service.OltManagerFacade
import com.dscorp.wispadmin.oltgateway.service.OltSignalPollService
import com.dscorp.wispadmin.oltgateway.service.SmartOltImportService
import com.dscorp.wispadmin.wispadmin.data.model.NapBox
import com.dscorp.wispadmin.wispadmin.data.model.Onu
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.dscorp.wispadmin.wispadmin.requestbody.smartoltrequest.MoveOnuRequest
import com.dscorp.wispadmin.wispadmin.requestbody.smartoltrequest.OnuAuthorizationRequest
import com.dscorp.wispadmin.wispadmin.response.OnuBySnResponse
import com.dscorp.wispadmin.wispadmin.response.Response
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class OnuService @Autowired constructor(
    private val oltService: OltService,
    private val inventorySyncService: ObjectProvider<OltInventorySyncService>,
    private val oltManagerFacade: ObjectProvider<OltManagerFacade>,
    private val signalPollService: ObjectProvider<OltSignalPollService>,
    private val smartOltImportService: ObjectProvider<SmartOltImportService>,
    private val subscriptionRepository: SubscriptionRepository
) {

    fun listConfigured(
        page: Int,
        size: Int,
        filter: ConfiguredOnuFilter = ConfiguredOnuFilter()
    ): ConfiguredOnuPageDto {
        val sync = inventorySyncService.getIfAvailable()
            ?: throw ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "OLT gateway no está habilitado"
            )
        val configured = sync.listConfigured(page = page, size = size, filter = filter)
        return configured.copy(items = enrichWithSubscriptionIps(configured.items))
    }

    fun getConfiguredByExternalId(externalId: String): ConfiguredOnuDetailDto {
        val sync = inventorySyncService.getIfAvailable()
            ?: throw ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "OLT gateway no está habilitado"
            )
        val detail = sync.getConfiguredByExternalId(externalId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "ONU no encontrada")
        return enrichDetailWithSubscriptionIp(detail)
    }

    fun getConfiguredLiveStatus(externalId: String): ConfiguredOnuLiveStatusDto {
        val sync = inventorySyncService.getIfAvailable()
            ?: throw ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "OLT gateway no está habilitado"
            )
        return sync.getLiveStatusByExternalId(externalId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "ONU no encontrada")
    }

    fun getConfiguredHistory(externalId: String, limit: Int = 50): ConfiguredOnuHistoryDto {
        val sync = inventorySyncService.getIfAvailable()
            ?: throw ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "OLT gateway no está habilitado"
            )
        return sync.getHistoryByExternalId(externalId, limit)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "ONU no encontrada")
    }

    fun listCatalogs(): OnuCatalogsDto {
        val sync = inventorySyncService.getIfAvailable()
            ?: throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "OLT gateway no está habilitado")
        return sync.listCatalogs()
    }

    fun listBoardsPorts(oltId: Long? = null, board: Int? = null): BoardPortCatalogDto {
        val sync = inventorySyncService.getIfAvailable()
            ?: throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "OLT gateway no está habilitado")
        return sync.listBoardsPorts(oltId, board)
    }

    fun getUnConfiguredOnus(): List<Response> {
        val facade = oltManagerFacade.getIfAvailable()
        if (facade != null) {
            return facade.unconfiguredOnus().response.map { item ->
                Response(
                    board = item.board,
                    olt_id = item.olt_id,
                    onu = item.onu,
                    onu_type_id = item.onu_type_id,
                    onu_type_name = item.onu_type_name,
                    pon_type = item.pon_type,
                    port = item.port,
                    sn = item.sn
                )
            }
        }
        return oltService.getUnConfiguredOnus() ?: emptyList()
    }

    fun authorizeOnu(request: AuthorizeOnuFormDto): SmartOltActionResponseDto {
        val facade = oltManagerFacade.getIfAvailable()
        if (facade != null) {
            return facade.authorizeOnu(request)
        }
        authorizeOnuInSmartOltWidthPostMethod(
            OnuAuthorizationRequest(
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
        return SmartOltActionResponseDto(status = true, message = "authorized via SmartOLT")
    }

    fun deleteConfiguredOnu(externalId: String): SmartOltActionResponseDto {
        val facade = oltManagerFacade.getIfAvailable()
        if (facade != null) {
            return facade.deleteOnu(externalId)
        }
        oltService.deleteOnu(externalId)
        return SmartOltActionResponseDto(status = true, unique_external_id = externalId)
    }

    fun rebootConfiguredOnu(externalId: String): SmartOltActionResponseDto {
        val facade = oltManagerFacade.getIfAvailable()
        if (facade != null) {
            return facade.rebootOnu(externalId)
        }
        oltService.rebootOnu(externalId)
        return SmartOltActionResponseDto(status = true, unique_external_id = externalId)
    }

    fun syncInventory(): SyncResultDto {
        val sync = inventorySyncService.getIfAvailable()
            ?: throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "OLT gateway no está habilitado")
        val result = sync.syncInventoryFromSnmp()
        return SyncResultDto(
            inserted = result.inserted,
            updated = result.updated,
            softDeleted = result.softDeleted,
            unchanged = result.unchanged,
            durationMs = result.durationMs,
            skippedReason = result.skippedReason,
            error = result.error
        )
    }

    fun syncSignal(): SignalPollResultDto {
        val poll = signalPollService.getIfAvailable()
            ?: throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "OLT gateway no está habilitado")
        val result = poll.pollSignalsFromSnmp()
        return SignalPollResultDto(
            slotsPolled = result.slotsPolled,
            portsPolled = result.portsPolled,
            onusUpdated = result.onusUpdated,
            durationMs = result.durationMs,
            skippedReason = result.skippedReason,
            error = result.error
        )
    }

    fun getOnuBySn(onuSn: String): OnuBySnResponse {
        return oltService.getOnuBySn(onuSn)
    }

    fun moveOnu(request: MoveOnuRequest, onu: Onu, newNapBox: NapBox) {
        oltService.moveOnu(request, onu, newNapBox)
    }

    fun authorizeOnuInSmartOltWidthPostMethod(authorizationRequest: OnuAuthorizationRequest) {
        val facade = oltManagerFacade.getIfAvailable()
        if (facade != null) {
            facade.authorizeOnu(authorizationRequest.toAuthorizeForm())
            return
        }
        oltService.authorizeOnuInSmartOltWidthPostMethod(authorizationRequest)
    }

    fun importFromSmartOlt(pageSize: Int = 100, maxPages: Int? = null): SmartOltImportResultDto {
        val importService = smartOltImportService.getIfAvailable()
            ?: throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "OLT gateway no está habilitado")
        return importService.importFromSmartOlt(pageSize, maxPages)
    }

    private fun OnuAuthorizationRequest.toAuthorizeForm(): AuthorizeOnuFormDto =
        AuthorizeOnuFormDto(
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

    fun deleteOnu(onuExternalId: String) {
        deleteConfiguredOnu(onuExternalId)
    }

    fun deleteOnuBySn(onuSn: String) {
        val details = getOnuBySn(onuSn)
        if (details.onus.isEmpty()) {
            throw IllegalArgumentException("No se encontró la ONU en SmartOLT para el serial indicado")
        }
        val uniqueId = details.onus[0].unique_external_id
        if (uniqueId.isBlank()) {
            throw IllegalStateException("La ONU no tiene identificador externo en SmartOLT")
        }
        deleteConfiguredOnu(uniqueId)
    }

    fun rebootOnuBySn(onuSn: String) {
        val details = getOnuBySn(onuSn)
        if (details.onus.isEmpty()) {
            throw IllegalArgumentException("No se encontró la ONU en SmartOLT para el serial indicado")
        }
        val uniqueId = details.onus[0].unique_external_id
        if (uniqueId.isBlank()) {
            throw IllegalStateException("La ONU no tiene identificador externo en SmartOLT")
        }
        rebootConfiguredOnu(uniqueId)
    }

    private fun enrichWithSubscriptionIps(items: List<ConfiguredOnuItemDto>): List<ConfiguredOnuItemDto> {
        if (items.isEmpty()) return items
        val missing = items.filter { it.ipAddress.isNullOrBlank() }
        if (missing.isEmpty()) return items

        val lookupKeys = missing
            .flatMap { item ->
                val sn = item.sn.trim().uppercase()
                if (sn.length >= SUFFIX_LENGTH) listOf(sn, sn.takeLast(SUFFIX_LENGTH)) else listOf(sn)
            }
            .distinct()
        val ipBySn = linkedMapOf<String, String>()
        if (lookupKeys.isNotEmpty()) {
            subscriptionRepository.findActiveIpSnPairsByFiberOnuSnIn(lookupKeys).forEach { row ->
                val fiberSn = (row[0] as? String)?.trim().orEmpty()
                val ip = (row[1] as? String)?.trim().orEmpty()
                if (fiberSn.isEmpty() || ip.isEmpty()) return@forEach
                ipBySn.putIfAbsent(fiberSn, ip)
                if (fiberSn.length >= SUFFIX_LENGTH) {
                    ipBySn.putIfAbsent(fiberSn.takeLast(SUFFIX_LENGTH), ip)
                }
            }
        }

        val stillMissing = missing.filter { item ->
            val sn = item.sn.trim().uppercase()
            ipBySn[sn].isNullOrBlank() &&
                (sn.length < SUFFIX_LENGTH || ipBySn[sn.takeLast(SUFFIX_LENGTH)].isNullOrBlank())
        }
        val ipByName = linkedMapOf<String, String>()
        val nameKeys = stillMissing
            .mapNotNull { it.name?.trim()?.lowercase()?.replace(Regex("\\s+"), " ")?.takeIf { n -> n.isNotEmpty() } }
            .distinct()
        if (nameKeys.isNotEmpty()) {
            subscriptionRepository.findActiveIpNamePairsByFullNameIn(nameKeys).forEach { row ->
                val fullName = (row[0] as? String)?.trim()?.replace(Regex("\\s+"), " ").orEmpty()
                val ip = (row[1] as? String)?.trim().orEmpty()
                if (fullName.isNotEmpty() && ip.isNotEmpty()) {
                    ipByName.putIfAbsent(fullName, ip)
                }
            }
        }

        return items.map { item ->
            if (!item.ipAddress.isNullOrBlank()) return@map item
            val sn = item.sn.trim().uppercase()
            val bySn = ipBySn[sn] ?: sn.takeLast(SUFFIX_LENGTH).let { ipBySn[it] }
            if (!bySn.isNullOrBlank()) return@map item.copy(ipAddress = bySn)
            val byName = item.name?.trim()?.lowercase()?.replace(Regex("\\s+"), " ")?.let { ipByName[it] }
            if (byName.isNullOrBlank()) item else item.copy(ipAddress = byName)
        }
    }

    private fun enrichDetailWithSubscriptionIp(detail: ConfiguredOnuDetailDto): ConfiguredOnuDetailDto {
        if (!detail.ipAddress.isNullOrBlank()) return detail
        val enriched = enrichWithSubscriptionIps(
            listOf(
                ConfiguredOnuItemDto(
                    id = detail.id,
                    sn = detail.sn,
                    externalId = detail.externalId,
                    board = detail.board,
                    port = detail.port,
                    onuIndex = detail.onuIndex,
                    name = detail.name,
                    importedFromOlt = detail.importedFromOlt,
                    runState = detail.runState,
                    matchState = detail.matchState,
                    polledAt = detail.polledAt,
                    ipAddress = detail.ipAddress
                )
            )
        ).firstOrNull()?.ipAddress
        return if (enriched.isNullOrBlank()) detail else detail.copy(ipAddress = enriched)
    }

    companion object {
        private const val SUFFIX_LENGTH = 8
    }
}
