package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.data.model.util.BaseResponse
import com.dscorp.wispadmin.wispadmin.requestbody.smartoltrequest.OnuAuthorizationRequest
import com.dscorp.wispadmin.wispadmin.response.OnuActionResponseDto
import com.dscorp.wispadmin.wispadmin.response.Response
import com.dscorp.wispadmin.wispadmin.security.PlatformAuthFilter
import com.dscorp.wispadmin.wispadmin.service.OltService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import javax.servlet.http.HttpServletRequest

@RestController
@RequestMapping("/onu")
class OnuSmartOltController(
    private val oltService: OltService,
) {
    @DeleteMapping
    fun deleteOnu(@RequestParam onuExternalId: String, request: HttpServletRequest): BaseResponse {
        requireAdmin(request)
        oltService.deleteOnu(onuExternalId)
        return BaseResponse(status = 200, message = "Onu eliminada correctamente", data = "", error = null)
    }

    @GetMapping("unconfigured_onus")
    fun getUnConfiguredOnus(): ResponseEntity<List<Response>> =
        ResponseEntity.ok(oltService.getUnConfiguredOnus() ?: emptyList())

    @PostMapping("authorize")
    fun authorizeOnu(@RequestBody request: OnuAuthorizationRequest): OnuActionResponseDto {
        oltService.authorizeOnuInSmartOltWidthPostMethod(request)
        return OnuActionResponseDto(status = true, message = "authorized via SmartOLT")
    }

    @PostMapping("configured/{externalId}/reboot")
    fun rebootConfigured(@PathVariable externalId: String): OnuActionResponseDto {
        oltService.rebootOnu(externalId)
        return OnuActionResponseDto(status = true, unique_external_id = externalId)
    }

    @DeleteMapping("configured/by-sn/{sn}")
    fun deleteConfiguredBySn(@PathVariable sn: String, request: HttpServletRequest): OnuActionResponseDto {
        requireAdmin(request)
        oltService.deleteOnuBySn(sn)
        return OnuActionResponseDto(status = true, message = "deleted", unique_external_id = sn.trim().uppercase())
    }

    @DeleteMapping("configured/{externalId}")
    fun deleteConfigured(@PathVariable externalId: String, request: HttpServletRequest): OnuActionResponseDto {
        requireAdmin(request)
        oltService.deleteOnu(externalId)
        return OnuActionResponseDto(status = true, unique_external_id = externalId)
    }

    @GetMapping("getBySn")
    fun getOnuBySn(@RequestParam onuSn: String): BaseResponse {
        val onu = oltService.getOnuBySn(onuSn)
        return if (onu.onus.isNotEmpty())
            BaseResponse(status = 200, data = onu, message = "Success", error = null)
        else
            BaseResponse(status = 500, data = null, message = "Error", error = "No se encontró la ONU registrada en la OLT")
    }

    private fun requireAdmin(request: HttpServletRequest) {
        val type = request.getAttribute(PlatformAuthFilter.AUTH_USER_TYPE_ATTRIBUTE)?.toString()
        if (type != "ADMIN") {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Solo ADMIN puede eliminar una ONU")
        }
    }
}
