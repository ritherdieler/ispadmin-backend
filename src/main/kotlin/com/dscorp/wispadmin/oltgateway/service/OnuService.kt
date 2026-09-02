package com.dscorp.wispadmin.oltgateway.service

import com.dscorp.wispadmin.oltgateway.api.AuthorizeOnuFormDto
import com.dscorp.wispadmin.oltgateway.api.SmartOltActionResponseDto
import com.dscorp.wispadmin.oltgateway.api.SmartOltOnuBySnResponseDto
import com.dscorp.wispadmin.oltgateway.config.OltGatewayProperties
import com.dscorp.wispadmin.oltgateway.exception.OnuNotFoundException
import com.dscorp.wispadmin.oltgateway.dto.BoardPortCatalogDto
import com.dscorp.wispadmin.oltgateway.dto.ConfiguredOnuDetailDto
import com.dscorp.wispadmin.oltgateway.dto.ConfiguredOnuFilter
import com.dscorp.wispadmin.oltgateway.dto.ConfiguredOnuHistoryDto
import com.dscorp.wispadmin.oltgateway.dto.ConfiguredOnuItemDto
import com.dscorp.wispadmin.oltgateway.dto.ConfiguredOnuLiveStatusDto
import com.dscorp.wispadmin.oltgateway.dto.ConfiguredOnuPageDto
import com.dscorp.wispadmin.oltgateway.dto.OnuCatalogsDto
import com.dscorp.wispadmin.oltgateway.dto.SmartOltImportResultDto
import com.dscorp.wispadmin.oltgateway.dto.SyncJobStatusDto
import com.dscorp.wispadmin.oltgateway.dto.SyncStatusDto
import com.dscorp.wispadmin.wispadmin.data.model.NapBox
import com.dscorp.wispadmin.wispadmin.data.model.Onu
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.dscorp.wispadmin.wispadmin.response.Onu as SmartOltOnuLegacy
import org.slf4j.LoggerFactory
import com.dscorp.wispadmin.wispadmin.requestbody.smartoltrequest.MoveOnuRequest
import com.dscorp.wispadmin.wispadmin.requestbody.smartoltrequest.OnuAuthorizationRequest
import com.dscorp.wispadmin.wispadmin.response.OnuBySnResponse
import com.dscorp.wispadmin.wispadmin.response.Response
import com.dscorp.wispadmin.wispadmin.service.OltService
import com.dscorp.wispadmin.wispadmin.service.onu.OnuOperationsPort
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Primary
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
@Primary
class OnuService @Autowired constructor(
    private val oltService: OltService,
    private val inventorySyncService: ObjectProvider<OltInventorySyncService>,
    private val signalPollService: ObjectProvider<OltSignalPollService>,
    private val smartOltImportService: ObjectProvider<SmartOltImportService>,
    private val subscriptionRepository: SubscriptionRepository,
    private val autofindCacheService: ObjectProvider<OltAutofindCacheService>,
    private val oltManagerFacade: ObjectProvider<OltManagerFacade>,
    private val syncJobRunner: ObjectProvider<OltGatewaySyncJobRunner>,
    private val properties: ObjectProvider<OltGatewayProperties>,
    private val writeRouter: OnuWriteRouter
) : OnuOperationsPort {

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

    fun getUnConfiguredOnus(forceRefresh: Boolean = false): List<Response> {
        val cache = autofindCacheService.getIfAvailable()
            ?.takeIf { properties.getIfAvailable()?.autofind?.enabled == true }
            ?: return oltService.getUnConfiguredOnus() ?: emptyList()

        if (forceRefresh) {
            cache.refreshLive()
        }
        return cache.listUnconfigured().map { item ->
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

    fun authorizeOnu(request: AuthorizeOnuFormDto): SmartOltActionResponseDto =
        writeRouter.authorize(request)

    fun deleteConfiguredOnu(externalId: String): SmartOltActionResponseDto =
        writeRouter.delete(externalId)

    fun rebootConfiguredOnu(externalId: String): SmartOltActionResponseDto =
        writeRouter.reboot(externalId)

    fun startInventorySync(): SyncJobStatusDto {
        val runner = syncJobRunner.getIfAvailable()
            ?: throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "OLT gateway no está habilitado")
        return runner.startSnmpInventory()
    }

    fun startSignalSync(): SyncJobStatusDto {
        val runner = syncJobRunner.getIfAvailable()
            ?: throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "OLT gateway no está habilitado")
        return runner.startSignal()
    }

    fun syncStatus(): SyncStatusDto {
        val sync = inventorySyncService.getIfAvailable()
            ?: throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "OLT gateway no está habilitado")
        val inventory = sync.status()
        val signal = signalPollService.getIfAvailable()?.status()
        return inventory.copy(
            signalRunning = signal?.running ?: false,
            signalLastStartedAt = signal?.lastStartedAt,
            signalLastResult = signal?.lastResult
        )
    }

    fun getOnuBySn(onuSn: String): OnuBySnResponse {
        val facade = oltManagerFacade.getIfAvailable() ?: return oltService.getOnuBySn(onuSn)
        return try {
            facade.getOnusDetailsBySn(onuSn).toLegacyOnuBySnResponse()
        } catch (ex: OnuNotFoundException) {
            OnuBySnResponse(onus = emptyList(), response_code = "404", status = false)
        } catch (ex: Exception) {
            logger.warn("getOnuBySn desde el gateway falló para SN={}, se cae a SmartOLT: {}", onuSn, ex.message)
            oltService.getOnuBySn(onuSn)
        }
    }

    override fun moveOnu(request: MoveOnuRequest, onu: Onu, newNapBox: NapBox) {
        writeRouter.move(request, onu, newNapBox)
    }

    override fun authorizeOnuInSmartOltWidthPostMethod(authorizationRequest: OnuAuthorizationRequest) {
        writeRouter.authorize(authorizationRequest)
    }

    fun importFromSmartOlt(pageSize: Int = 100, maxPages: Int? = null): SmartOltImportResultDto {
        val importService = smartOltImportService.getIfAvailable()
            ?: throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "OLT gateway no está habilitado")
        return importService.importFromSmartOlt(pageSize, maxPages)
    }

    override fun deleteOnu(onuExternalId: String) {
        writeRouter.delete(onuExternalId)
    }

    override fun deleteOnuBySn(onuSn: String) {
        writeRouter.deleteBySn(onuSn)
    }

    override fun rebootOnuBySn(onuSn: String) {
        writeRouter.rebootBySn(onuSn)
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

    private fun SmartOltOnuBySnResponseDto.toLegacyOnuBySnResponse(): OnuBySnResponse = OnuBySnResponse(
        onus = onus.map { dto ->
            SmartOltOnuLegacy(
                address = dto.address,
                administrative_status = dto.administrative_status,
                authorization_date = dto.authorization_date,
                board = dto.board,
                catv = dto.catv,
                custom_template_name = dto.custom_template_name,
                default_gateway = dto.default_gateway,
                dns1 = dto.dns1,
                dns2 = dto.dns2,
                ethernet_ports = emptyList(),
                ip_address = dto.ip_address,
                iptv = dto.iptv,
                iptv_allowed_macs = dto.iptv_allowed_macs,
                iptv_cvlan = dto.iptv_cvlan,
                iptv_download_speed = dto.iptv_download_speed,
                iptv_filtered_macs = dto.iptv_filtered_macs,
                iptv_service_port = dto.iptv_service_port,
                iptv_svlan = dto.iptv_svlan,
                iptv_tag_transform_mode = dto.iptv_tag_transform_mode,
                iptv_upload_speed = dto.iptv_upload_speed,
                iptv_vlan = dto.iptv_vlan,
                mgmt_ip_address = dto.mgmt_ip_address,
                mgmt_ip_cvlan = dto.mgmt_ip_cvlan,
                mgmt_ip_default_gateway = dto.mgmt_ip_default_gateway,
                mgmt_ip_dns1 = dto.mgmt_ip_dns1,
                mgmt_ip_dns2 = dto.mgmt_ip_dns2,
                mgmt_ip_mode = dto.mgmt_ip_mode,
                mgmt_ip_service_port = dto.mgmt_ip_service_port,
                mgmt_ip_subnet_mask = dto.mgmt_ip_subnet_mask,
                mgmt_ip_svlan = dto.mgmt_ip_svlan,
                mgmt_ip_tag_transform_mode = dto.mgmt_ip_tag_transform_mode,
                mgmt_ip_vlan = dto.mgmt_ip_vlan,
                mode = dto.mode,
                name = dto.name,
                odb_name = dto.odb_name,
                olt_id = dto.olt_id,
                olt_name = dto.olt_name,
                onu = dto.onu,
                onu_type_id = dto.onu_type_id,
                onu_type_name = dto.onu_type_name,
                password = dto.password,
                pon_type = dto.pon_type,
                port = dto.port,
                service_ports = emptyList(),
                sn = dto.sn,
                subnet_mask = dto.subnet_mask,
                tr069_profile = dto.tr069_profile,
                unique_external_id = dto.unique_external_id,
                username = dto.username,
                vlan = dto.vlan,
                voip_ip_address = dto.voip_ip_address,
                voip_ip_cvlan = dto.voip_ip_cvlan,
                voip_ip_default_gateway = dto.voip_ip_default_gateway,
                voip_ip_dns1 = dto.voip_ip_dns1,
                voip_ip_dns2 = dto.voip_ip_dns2,
                voip_ip_mode = dto.voip_ip_mode,
                voip_ip_service_port = dto.voip_ip_service_port,
                voip_ip_subnet_mask = dto.voip_ip_subnet_mask,
                voip_ip_svlan = dto.voip_ip_svlan,
                voip_ip_tag_transform_mode = dto.voip_ip_tag_transform_mode,
                voip_ip_vlan = dto.voip_ip_vlan,
                wan_mode = dto.wan_mode,
                wifi_ports = dto.wifi_ports,
                zone_id = dto.zone_id,
                zone_name = dto.zone_name
            )
        },
        response_code = response_code,
        status = status
    )

    companion object {
        private const val SUFFIX_LENGTH = 8
        private val logger = LoggerFactory.getLogger(OnuService::class.java)
    }
}
