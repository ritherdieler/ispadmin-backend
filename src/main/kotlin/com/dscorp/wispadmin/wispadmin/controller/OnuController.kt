package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.oltgateway.api.AuthorizeOnuFormDto
import com.dscorp.wispadmin.oltgateway.api.SmartOltActionResponseDto
import com.dscorp.wispadmin.oltgateway.dto.BoardPortCatalogDto
import com.dscorp.wispadmin.oltgateway.dto.ConfiguredOnuDetailDto
import com.dscorp.wispadmin.oltgateway.dto.ConfiguredOnuFilter
import com.dscorp.wispadmin.oltgateway.dto.ConfiguredOnuHistoryDto
import com.dscorp.wispadmin.oltgateway.dto.ConfiguredOnuLiveStatusDto
import com.dscorp.wispadmin.oltgateway.dto.ConfiguredOnuPageDto
import com.dscorp.wispadmin.oltgateway.dto.OnuCatalogsDto
import com.dscorp.wispadmin.oltgateway.dto.SignalPollResultDto
import com.dscorp.wispadmin.oltgateway.dto.SmartOltImportResultDto
import com.dscorp.wispadmin.oltgateway.dto.SyncResultDto
import com.dscorp.wispadmin.wispadmin.data.model.util.BaseResponse
import com.dscorp.wispadmin.wispadmin.repository.MufaRepository
import com.dscorp.wispadmin.wispadmin.response.Response
import com.dscorp.wispadmin.wispadmin.service.OnuService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import javax.validation.constraints.Max
import javax.validation.constraints.Min

@RestController
@RequestMapping("/onu")
class OnuController(
    private val onuService: OnuService,
    private val mufaRepository: MufaRepository
) {

    @DeleteMapping
    fun deleteOnu(@RequestParam onuExternalId: String): BaseResponse {
        onuService.deleteOnu(onuExternalId)
        return BaseResponse(
            status = 200,
            message = "Onu eliminada correctamente",
            data = "",
            error = null
        )
    }

    @GetMapping("configured")
    fun listConfigured(
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @RequestParam(defaultValue = "50") @Min(1) @Max(200) size: Int,
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) board: Int?,
        @RequestParam(required = false) port: Int?,
        @RequestParam(required = false) oltId: Long?,
        @RequestParam(required = false) zoneId: Long?,
        @RequestParam(required = false) vlan: Int?,
        @RequestParam(required = false) onuTypeId: Long?,
        @RequestParam(required = false) onuTypeName: String?,
        @RequestParam(required = false) customProfile: String?,
        @RequestParam(required = false) ponType: String?,
        @RequestParam(required = false) mode: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) runState: String?,
        @RequestParam(required = false) signalCategory: String?,
        @RequestParam(required = false) splitterId: Long?,
        @RequestParam(required = false) configurationMethod: String?,
        @RequestParam(required = false) wanMode: String?,
        @RequestParam(required = false) mgmtIpMode: String?,
        @RequestParam(required = false) importedSynced: Boolean?,
        @RequestParam(required = false) lastResyncFailed: Boolean?,
        @RequestParam(required = false) lineProfileMaptype: String?
    ): ConfiguredOnuPageDto = onuService.listConfigured(
        page = page,
        size = size,
        filter = ConfiguredOnuFilter(
            q = q,
            board = board,
            port = port,
            oltId = oltId,
            zoneId = zoneId,
            vlan = vlan,
            onuTypeId = onuTypeId,
            onuTypeName = onuTypeName,
            customProfile = customProfile,
            ponType = ponType,
            mode = mode,
            status = status,
            runState = runState,
            signalCategory = signalCategory,
            splitterId = splitterId,
            configurationMethod = configurationMethod,
            wanMode = wanMode,
            mgmtIpMode = mgmtIpMode,
            importedSynced = importedSynced,
            lastResyncFailed = lastResyncFailed,
            lineProfileMaptype = lineProfileMaptype
        )
    )

    @GetMapping("configured/{externalId}")
    fun getConfigured(@PathVariable externalId: String): ConfiguredOnuDetailDto =
        onuService.getConfiguredByExternalId(externalId)

    @GetMapping("configured/{externalId}/status")
    fun getConfiguredStatus(@PathVariable externalId: String): ConfiguredOnuLiveStatusDto =
        onuService.getConfiguredLiveStatus(externalId)

    @GetMapping("configured/{externalId}/history")
    fun getConfiguredHistory(
        @PathVariable externalId: String,
        @RequestParam(defaultValue = "50") @Min(1) @Max(200) limit: Int
    ): ConfiguredOnuHistoryDto = onuService.getConfiguredHistory(externalId, limit)

    @GetMapping("catalog")
    fun listCatalogs(): OnuCatalogsDto = onuService.listCatalogs()

    @GetMapping("catalog/boards-ports")
    fun listBoardsPorts(
        @RequestParam(required = false) oltId: Long?,
        @RequestParam(required = false) board: Int?
    ): BoardPortCatalogDto = onuService.listBoardsPorts(oltId, board)

    @GetMapping("unconfigured_onus")
    fun getUnConfiguredOnus(): ResponseEntity<List<Response>> =
        ResponseEntity.ok(onuService.getUnConfiguredOnus())

    @PostMapping("authorize")
    fun authorizeOnu(@RequestBody request: AuthorizeOnuFormDto): SmartOltActionResponseDto =
        onuService.authorizeOnu(request)

    @PostMapping("configured/{externalId}/reboot")
    fun rebootConfigured(@PathVariable externalId: String): SmartOltActionResponseDto =
        onuService.rebootConfiguredOnu(externalId)

    @DeleteMapping("configured/{externalId}")
    fun deleteConfigured(@PathVariable externalId: String): SmartOltActionResponseDto =
        onuService.deleteConfiguredOnu(externalId)

    @PostMapping("sync/inventory")
    fun syncInventory(): SyncResultDto = onuService.syncInventory()

    @PostMapping("sync/signal")
    fun syncSignal(): SignalPollResultDto = onuService.syncSignal()

    @PostMapping("import/smartolt")
    fun importFromSmartOlt(
        @RequestParam(defaultValue = "100") @Min(1) @Max(500) pageSize: Int,
        @RequestParam(required = false) maxPages: Int?
    ): SmartOltImportResultDto = onuService.importFromSmartOlt(pageSize, maxPages)

    @GetMapping("getBySn")
    fun getOnuBySn(@RequestParam onuSn: String): BaseResponse {
        val onu = onuService.getOnuBySn(onuSn)
        return if (onu.onus.isNotEmpty())
            BaseResponse(
                status = 200,
                data = onu,
                message = "Success",
                error = null
            )
        else
            BaseResponse(
                status = 500,
                data = null,
                message = "Error",
                error = "No se encontró la ONU registrada en la OLT"
            )
    }
}
