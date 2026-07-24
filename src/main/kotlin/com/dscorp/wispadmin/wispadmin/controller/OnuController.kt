package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.data.model.util.BaseResponse
import com.dscorp.wispadmin.wispadmin.repository.MufaRepository
import com.dscorp.wispadmin.wispadmin.response.Response
import com.dscorp.wispadmin.wispadmin.service.OnuService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

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

    @GetMapping("unconfigured_onus")
    fun getUnConfiguredOnus(): ResponseEntity<List<Response>> =
        ResponseEntity.ok(onuService.getUnConfiguredOnus())

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
